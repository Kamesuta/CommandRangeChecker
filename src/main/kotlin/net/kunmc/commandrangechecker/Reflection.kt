package net.kunmc.commandrangechecker

import net.minecraft.advancements.critereon.CriterionConditionValue
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.world.phys.AxisAlignedBB
import java.lang.reflect.Field

/**
 * 地図操作リフレクションクラス
 */
object Reflection {
    /**
     * NMSにアクセスするためのクラス
     * NMSクラスが見つからなかったりした際、クラスの関数がそもそも呼べなくなるのを防ぐ
     */
    private object Accessor {
        // NMS関数/フィールド
        val entitySelectorRange: Field = EntitySelector::class.java.getDeclaredField("h").apply { isAccessible = true }
        val entitySelectorAABB: Field = EntitySelector::class.java.getDeclaredField("j").apply { isAccessible = true }
        val entitySelectorUsesSelector: Field =
            EntitySelector::class.java.getDeclaredField("p").apply { isAccessible = true }
    }

    /**
     * NMSクラスが存在するかチェックします
     * 存在しない場合は例外を投げます
     */
    @Throws(ReflectiveOperationException::class)
    fun checkReflection() {
        try {
            // NMSクラスが見つからなかったらエラー
            Accessor.javaClass
        } catch (e: ExceptionInInitializerError) {
            // 中身を返す
            throw e.cause ?: e
        }
    }

    /**
     * EntitySelectorのRangeを取得します
     * @receiver EntitySelector
     * @return Range
     */
    val EntitySelector.range: CriterionConditionValue.DoubleRange?
        get() {
            return runCatching {
                Accessor.entitySelectorRange[this] as CriterionConditionValue.DoubleRange?
            }.onFailure {
                CommandRangeChecker.instance.logger.warning("EntitySelector.rangeの取得に失敗")
            }.getOrNull()
        }

    /**
     * EntitySelectorのAABBを取得します
     * @receiver EntitySelector
     * @return AABB
     */
    val EntitySelector.aabb: AxisAlignedBB?
        get() {
            return runCatching {
                Accessor.entitySelectorAABB[this] as AxisAlignedBB?
            }.onFailure {
                CommandRangeChecker.instance.logger.warning("EntitySelector.aabbの取得に失敗")
            }.getOrNull()
        }

    /**
     * EntitySelectorのusesSelectorを取得します
     * @receiver EntitySelector
     * @return usesSelector
     */
    val EntitySelector.usesSelector: Boolean?
        get() {
            return runCatching {
                Accessor.entitySelectorUsesSelector[this] as Boolean?
            }.onFailure {
                CommandRangeChecker.instance.logger.warning("EntitySelector.usesSelectorの取得に失敗")
            }.getOrNull()
        }
}
