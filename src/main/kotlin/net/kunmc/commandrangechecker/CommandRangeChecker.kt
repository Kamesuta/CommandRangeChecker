package net.kunmc.commandrangechecker

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class CommandRangeChecker : JavaPlugin() {
    /** ProtocolManagerインスタンス */
    lateinit var protocolManager: ProtocolManager

    override fun onEnable() {
        // プラグインインスタンスをstaticフィールドに保存
        instance = this
        // ProtocolLibを初期化
        protocolManager = ProtocolLibrary.getProtocolManager()

        // コンフィグ生成
        saveDefaultConfig()

        // イベントリスナーの作成
        val listener = CommandListener()

        // イベント登録
        server.pluginManager.registerEvents(listener, this)

        // リフレクションクラスのチェック
        runCatching {
            // アイテムフレーム系クラスのチェック
            Reflection.checkReflection()
        }.onFailure { e ->
            logger.log(
                Level.SEVERE,
                "Failed to find classes, methods or fields in NMS. Disabling plugin.",
                e
            )
            // 読み込みに失敗したためプラグインを無効にする
            pluginLoader.disablePlugin(instance)
            return
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }


    companion object {
        /** プラグインインスタンス */
        lateinit var instance: CommandRangeChecker
            private set
    }
}