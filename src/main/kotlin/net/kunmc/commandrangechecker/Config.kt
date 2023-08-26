package net.kunmc.commandrangechecker

/**
 * プラグインの設定を管理するクラス
 */
object Config {
    val maxDistance = CommandRangeChecker.instance.config.getDouble("max_distance")
    val maxLength = CommandRangeChecker.instance.config.getDouble("max_length")
    val forceRangeLimit = CommandRangeChecker.instance.config.getBoolean("force_range_limit")

    val bypass: MutableList<String> = CommandRangeChecker.instance.config.getStringList("bypass")

    val prefix: String = getString("lang.prefix")
    val blockExecute: String = getString("lang.block_execute")
    val blockSetCommand: String = getString("lang.block_set_command")
    val blockNothing: String = getString("lang.block_nothing")
    val noRange: String = getString("lang.no_range")
    val largeDistance: String = getString("lang.large_distance")
    val largeLength: String = getString("lang.large_length")

    private fun getString(key: String): String {
        return CommandRangeChecker.instance.config.getString(key) ?: key
    }
}