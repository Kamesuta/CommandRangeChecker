package net.kunmc.commandrangechecker

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContextBuilder
import io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent
import net.minecraft.commands.CommandListenerWrapper
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.server.MinecraftServer
import org.bukkit.block.Block
import org.bukkit.command.CommandSender
import org.bukkit.craftbukkit.v1_20_R1.command.CraftBlockCommandSender
import org.bukkit.craftbukkit.v1_20_R1.command.VanillaCommandWrapper
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.server.ServerCommandEvent

/**
 * コマンド実行をフックして、範囲なし@eを検知するリスナー
 */
class CommandListener : Listener {
    /** バニラのコマンドパーサーを取得 */
    private val dispatcher: CommandDispatcher<CommandListenerWrapper> =
        MinecraftServer.getServer().vanillaCommandDispatcher.a()

    init {
        // ブロック変更時
        CommandRangeChecker.instance.protocolManager.addPacketListener(object : PacketAdapter(
            CommandRangeChecker.instance,
            ListenerPriority.NORMAL,
            PacketType.Play.Client.SET_COMMAND_BLOCK,
        ) {
            /** 受信 (クライアント→サーバー) */
            override fun onPacketReceiving(event: PacketEvent) {
                val packet = event.packet
                // 入力したコマンドを取得
                val command = packet.strings.read(0)
                // チェック
                if (onCommand(event.player, command, isCommandBlockSet = true)) {
                    // コマンドをサニタイズする
                    packet.strings.write(0, "!$command")
                    //event.isCancelled = true
                }
            }
        })
    }

    /** コマンドブロックの座標とコマンドを保持 */
    private val commandBlockCache = mutableMapOf<Block, String>()

    /**
     * コマンド実行時のチェック
     * @param sender コマンド実行者
     * @param command コマンド
     * @param isCommandBlockSet trueの場合はコマンドブロックの設定時
     * @return trueの場合コマンドをキャンセルする
     */
    private fun onCommand(sender: CommandSender, command: String, isCommandBlockSet: Boolean = false): Boolean {
        // 除外リスト
        if (sender is CraftPlayer && Config.bypass.contains(sender.name)) {
            return false
        }

        // コマンドブロックの場合、以前に実行されたコマンドでないかチェックする → 現在のコマンドを記憶する
        val commandBlockFirstExecute = !isCommandBlockSet
                && sender is CraftBlockCommandSender
                && commandBlockCache.put(sender.block, command) != command

        // 先頭の/を削除
        val commandBody = command.removePrefix("/")

        // コマンドラッパーを取得
        val commandWrapper = VanillaCommandWrapper.getListener(sender)
        // コマンドをパース
        val parse = dispatcher.parse(commandBody, commandWrapper)

        // すべてのコマンドノードを取得
        val selectors = sequence<CommandContextBuilder<*>> {
            var current = parse.context
            while (current != null) {
                yield(current)
                current = current.child
            }
        }.flatMap { it.arguments.values }.mapNotNull {
            // エンティティセレクターを取り出す
            val result = it.result as? EntitySelector ?: return@mapNotNull null
            // エンティティセレクターをパース
            ParsedEntitySelector(result, it.range.get(commandBody))
        }.filter {
            // セレクタ(@eや@r,@aなど)を使用している
            it.usesSelector ?: false
        }.filter {
            // エンティティを含む
            it.includeEntities
        }.toList()

        // ログの可変部分
        val actionNameMessage = if (isCommandBlockSet) Config.blockSetCommand else Config.blockExecute
        val actionNameLog = if (isCommandBlockSet) "コマンドブロックに設定しようと" else "使用"

        // 範囲指定がないセレクターを検出
        val noRangeSelector =
            selectors.find { !it.currentEntity && (!it.worldLimited || (it.maxDistance == null && it.maxLength == null)) }
        if (noRangeSelector != null) {
            // 範囲指定がない場合
            sender.sendMessage(Config.prefix + String.format(Config.noRange, actionNameMessage))
            if (commandBlockFirstExecute) CommandRangeChecker.instance.logger.warning("${sender.name} が範囲指定なしの@eを${actionNameLog}しました: ${noRangeSelector.selectorCommand} (場所: ${commandWrapper.d()}, コマンド拒否: true)")
            return true
        }

        // 距離範囲指定が大きすぎるセレクターを検出
        val largeDistanceSelector =
            selectors.find { selector -> selector.maxDistance?.let { it > Config.maxDistance } ?: false }
        if (largeDistanceSelector != null) {
            sender.sendMessage(
                String.format(
                    Config.prefix + Config.largeDistance,
                    if (Config.forceRangeLimit) actionNameMessage else Config.blockNothing,
                    Config.maxDistance
                )
            )
            if (commandBlockFirstExecute) CommandRangeChecker.instance.logger.warning("${sender.name} が距離範囲指定が${Config.maxDistance}ブロックを超える@eを${actionNameLog}しました: ${largeDistanceSelector.selectorCommand} (場所: ${commandWrapper.d()}, コマンド拒否: ${Config.forceRangeLimit})")
            return Config.forceRangeLimit
        }

        // 矩形範囲指定が大きすぎるセレクターを検出
        val largeLengthSelector =
            selectors.find { selector -> selector.maxLength?.let { it > Config.maxLength } ?: false }
        if (largeLengthSelector != null) {
            sender.sendMessage(
                String.format(
                    Config.prefix + Config.largeLength,
                    if (Config.forceRangeLimit) actionNameMessage else Config.blockNothing,
                    Config.maxLength
                )
            )
            if (commandBlockFirstExecute) CommandRangeChecker.instance.logger.warning("${sender.name} が矩形範囲指定が${Config.maxLength}ブロックを超える@eを${actionNameLog}しました: ${largeLengthSelector.selectorCommand} (場所: ${commandWrapper.d()}, コマンド拒否: ${Config.forceRangeLimit})")
            return Config.forceRangeLimit
        }

        return false
    }

    @EventHandler
    fun onCommand(event: ServerCommandEvent) {
        // チェック
        if (onCommand(event.sender, event.command)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        // チェック
        if (onCommand(event.player, event.message)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerSignCommand(event: PlayerSignCommandPreprocessEvent) {
        // チェック
        if (onCommand(event.player, event.message)) {
            event.isCancelled = true
        }
    }
}