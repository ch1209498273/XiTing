// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

/**
 * 海报版式：垂直栈的纯计算（无 Android 依赖，可 JVM 单测）。
 *
 * ## 为什么把版式抽成纯函数
 * 旧版 render() 里是 1080×1440 的手写绝对坐标。要加 1:1 比例就得再抄一份坐标，
 * 两份手写坐标必然漂移 —— 与「图鉴顶部大图不跟随选择」是同一类教训：
 * 多处共享的状态必须收拢到唯一来源。
 *
 * 现在版式是「固定链 + 弹性精灵」：每个比例只有一组旋钮（见 [KNOBS]），
 * 帧内所有 y 都从旋钮推导；纵向放不下时**先收缩精灵**（主角可以小一点，
 * 但页脚绝不能被挤出画布 —— 旧版 0.58 精灵就出过仓库地址被裁掉的事故）。
 *
 * ## 推演纪律（改任何旋钮前先读懂）
 * 自上而下的固定链：顶栏基线 → 精灵顶 →（弹性精灵）→ 主数字基线 →
 * 说明小字基线 → 卡片顶 → 卡片底 → 页脚两行基线。
 * 精灵边长 = min(比例上限, 纵向剩余空间)，且不得低于 [PET_MIN_SCALE]×W；
 * 剩余空间连下限都装不下时 [compute] 直接抛异常 —— 宁可渲染失败走纯文本退路，
 * 也不出一页脚被裁的坏图。
 *
 * 字号**不随比例缩放**：两种比例宽度同为 1080，缩字号会改变信息层级；
 * 只动纵向间距与精灵占比。
 */
object PosterLayout {

    /** 版式比例。3:4 = 朋友圈/微博（原有）；1:1 = 小红书（2026-10-11 新增）。 */
    enum class Ratio(val w: Int, val h: Int) {
        PORTRAIT_3_4(1080, 1440),
        SQUARE_1_1(1080, 1080)
    }

    /** 一帧版式。坐标均为像素绝对值（基线或顶边）；x 方向由 render 按居中/左右留白规则使用。 */
    class Frame(
        val headerY: Float,    // 顶栏（品牌名 + 形态名）基线
        val petTop: Float,     // 精灵顶边
        val petSize: Int,      // 精灵边长（弹性项，见类 KDoc）
        val headY: Float,      // 主数字基线
        val hintY: Float,      // 主数字下说明小字基线
        val cardTop: Float,    // 明细卡片顶边
        val cardH: Float,      // 明细卡片高
        val cardBottom: Float, // 卡片底边 = cardTop + cardH
        val cardTextTop: Float,// 卡片顶 → 第一行明细基线
        val footer1Y: Float,   // 脚注基线
        val footer2Y: Float    // 仓库地址基线
    )

    private class Knobs(
        val headerY: Float,
        val petTop: Float,
        val petScale: Float,   // 精灵边长上限 = W × petScale
        val headGap: Float,    // 精灵底 → 主数字基线
        val hintGap: Float,    // 主数字基线 → 说明小字基线
        val cardGap: Float,    // 说明小字基线 → 卡片顶
        val rowH: Float,       // 明细每行行高
        val cardPad: Float,    // 卡片总高在行高之外加的上下内边距
        val cardTextTop: Float,// 卡片顶 → 首行明细基线
        val footerGap1: Float, // 卡片底 → 脚注基线
        val footerGap2: Float  // 脚注基线 → 仓库地址基线
    )

    // ---- 旋钮表。数值互相约束，改任何一个都要重跑 PosterLayoutTest ----
    private val KNOBS = mapOf(
        Ratio.PORTRAIT_3_4 to Knobs(
            headerY = 116f, petTop = 196f, petScale = 0.52f,
            headGap = 172f, hintGap = 58f, cardGap = 42f,
            rowH = 76f, cardPad = 52f, cardTextTop = 68f,
            footerGap1 = 46f, footerGap2 = 52f
        ),
        Ratio.SQUARE_1_1 to Knobs(
            headerY = 96f, petTop = 132f, petScale = 0.40f,
            headGap = 110f, hintGap = 48f, cardGap = 40f,
            rowH = 64f, cardPad = 44f, cardTextTop = 60f,
            footerGap1 = 40f, footerGap2 = 50f
        )
    )

    /** 画布底边与页脚基线的最小余量（字形下伸 + 视觉收边） */
    private const val BOTTOM_RESERVE = 32f
    /** 精灵边长下限 = W × 此系数：再小主角就没了，宁可让整张海报失败 */
    private const val PET_MIN_SCALE = 0.28f
    /** 版式锚定宽度（与 Ratio.w 一致；字号体系按这个宽度定标，不随比例变） */
    private const val PET_W = 1080

    /**
     * 推出一帧版式。
     * @throws IllegalArgumentException 明细行数超界，或纵向连精灵下限都放不下
     */
    fun compute(ratio: Ratio, lineCount: Int): Frame {
        require(lineCount in 1..4) { "海报明细行数超出版式范围：$lineCount（按 1..4 行推演）" }
        val k = KNOBS.getValue(ratio)
        val cardH = k.rowH * lineCount + k.cardPad
        val fixed = k.headGap + k.hintGap + k.cardGap + cardH + k.footerGap1 + k.footerGap2
        val petBySpace = ratio.h - BOTTOM_RESERVE - k.petTop - fixed
        require(petBySpace >= PET_MIN_SCALE * PET_W) {
            "纵向放不下：$ratio × $lineCount 行，精灵只剩 ${petBySpace.toInt()}px（下限 ${(PET_MIN_SCALE * PET_W).toInt()}px）"
        }
        val petSize = minOf(k.petScale * PET_W, petBySpace).toInt()
        val headY = k.petTop + petSize + k.headGap
        val hintY = headY + k.hintGap
        val cardTop = hintY + k.cardGap
        val cardBottom = cardTop + cardH
        val footer1Y = cardBottom + k.footerGap1
        val footer2Y = footer1Y + k.footerGap2
        return Frame(
            headerY = k.headerY, petTop = k.petTop, petSize = petSize,
            headY = headY, hintY = hintY,
            cardTop = cardTop, cardH = cardH, cardBottom = cardBottom, cardTextTop = k.cardTextTop,
            footer1Y = footer1Y, footer2Y = footer2Y
        )
    }
}
