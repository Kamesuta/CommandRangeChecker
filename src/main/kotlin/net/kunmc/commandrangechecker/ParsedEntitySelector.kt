package net.kunmc.commandrangechecker

import net.kunmc.commandrangechecker.Reflection.aabb
import net.kunmc.commandrangechecker.Reflection.range
import net.kunmc.commandrangechecker.Reflection.usesSelector
import net.minecraft.server.v1_16_R3.AxisAlignedBB
import net.minecraft.server.v1_16_R3.CriterionConditionValue
import net.minecraft.server.v1_16_R3.EntitySelector
import kotlin.math.max

/**
 * EntitySelectorから距離など必要な情報を取得するクラス
 */
class ParsedEntitySelector(val selector: EntitySelector, val selectorCommand: String) {
    /** エンティティのみか */
    val includeEntities = selector.b()

    /** ワールドが指定されているか。(通常、範囲が指定されている場合trueになる) */
    val worldLimited = selector.d()

    /** 距離に応じた範囲制限 */
    val range: CriterionConditionValue.FloatRange? = selector.range

    /** セレクターを使用しているか */
    val usesSelector = selector.usesSelector

    /** `@s`かどうか */
    val currentEntity = selector.c()

    /** AABBによる範囲制限 */
    val aabb: AxisAlignedBB? = selector.aabb

    /** 最大距離 */
    val maxDistance = range?.b()

    /** AABBの最大辺 */
    val maxLength = aabb?.let {
        val xLength = it.maxX - it.minX
        val yLength = it.maxY - it.minY
        val zLength = it.maxZ - it.minZ
        max(max(xLength, yLength), zLength).toFloat()
    }
}