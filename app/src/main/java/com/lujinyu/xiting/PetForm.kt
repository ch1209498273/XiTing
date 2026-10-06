// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

/**
 * 精灵形态的「解锁」与「展示」分离。
 *
 * 原先形态由成长值直接算出（stageOf），主页精灵和悬浮球显示的都是同一个形态，
 * 于是「养到第几级」被硬绑成「长什么样」。设置页那个「悬浮球样式」里虽然也能选
 * pet_0..pet_4，但它只影响悬浮球，主页精灵照旧按成长值渲染 —— 两套状态各说各话，
 * 选了一个地方另一个地方不变。
 *
 * 现在：
 *   · 解锁到哪一形态 = 成长值决定（PetView.stageOf），只增不减
 *   · 当前展示哪一形态 = 用户在图鉴里选，存在 pet_form
 * 三处消费（主页精灵 / 悬浮球 / 小组件）统一读这里。
 */
object PetForm {

    /** 已解锁的最高形态（由成长值算出） */
    fun unlockedStage(ctx: android.content.Context): Int =
        PetView.stageOf(EnergyStore.collectedTotal(ctx).toLong())

    fun isUnlocked(ctx: android.content.Context, stage: Int): Boolean =
        stage <= unlockedStage(ctx)

    /**
     * 当前展示的形态。未设置过则沿用「悬浮球样式」里原本选的形态，都没有就按成长值算，
     * 尽量不改变用户已有的观感。
     */
    fun selected(ctx: android.content.Context): Int {
        val prefs = ctx.prefs()
        val stored = if (prefs.contains(Prefs.PET_FORM)) {
            prefs.getInt(Prefs.PET_FORM, -1)
        } else {
            // 迁移：老版本把形态编码在 bubble_style="pet_N" 里
            prefs.getString(Prefs.BUBBLE_STYLE, "text")
                ?.takeIf { it.startsWith("pet_") }
                ?.removePrefix("pet_")
                ?.toIntOrNull()
                ?: -1
        }
        val max = unlockedStage(ctx)
        val chosen = stored.coerceIn(PetView.STAGE_SPARK, max)
        if (stored != chosen) prefs.edit().putInt(Prefs.PET_FORM, chosen).apply()
        return chosen
    }

    /** 选择展示形态；超过解锁上限的会被拒绝（返回是否成功） */
    fun select(ctx: android.content.Context, stage: Int): Boolean {
        if (stage > unlockedStage(ctx)) return false
        ctx.prefs().edit().putInt(Prefs.PET_FORM, stage.coerceIn(PetView.STAGE_SPARK, PetView.STAGE_KING)).apply()
        return true
    }

    /** 悬浮球是否用精灵形象（false = 「息屏」文字） */
    fun bubbleUsesPet(ctx: android.content.Context): Boolean {
        val prefs = ctx.prefs()
        val stored = prefs.getString(Prefs.BUBBLE_STYLE, "text") ?: "text"
        return if (stored.startsWith("pet_")) {
            // 迁移：老值 pet_N 视为「用精灵」，并把形态并入 pet_form
            prefs.edit()
                .putString(Prefs.BUBBLE_STYLE, "pet")
                .apply()
            true
        } else {
            stored == "pet"
        }
    }

    fun setBubbleUsesPet(ctx: android.content.Context, on: Boolean) {
        ctx.prefs().edit()
            .putString(Prefs.BUBBLE_STYLE, if (on) "pet" else "text")
            .apply()
    }

    /** 形态达到该阶段所需的成长值（图鉴/设置里提示用） */
    fun requiredFor(stage: Int): Long = PetView.THRESHOLDS[stage.coerceIn(0, PetView.STAGE_KING)]
}