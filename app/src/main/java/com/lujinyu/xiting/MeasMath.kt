// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

/**
 * 省电实测的纯计算。
 *
 * 为什么要单独拆出来：`PowerCalib` 里的读写都要 Context（prefs / BatteryManager），
 * JVM 单测跑不了。把「差值 → 速率 → 折算」这段算术放这里，
 * 就能用 MeasMathTest 把公式钉死，而 [PowerCalib] 仍然调用它 ——
 * 单测测的就是线上跑的那套，不是复制一遍的公式。
 */
object MeasMath {

    /** 样本最短时长：短于 2 分钟，差值会被计数器的量化误差淹没 */
    const val MIN_SAMPLE_MS = 2 * 60_000L

    /**
     * 单样本的能耗速率（µWh/ms）；无效段返回 null。
     *
     * @param maxRate 速率上限，超过判为脏数据（正常手机 100~800mW，
     *   换算成 µWh/ms 上千就是充电或高负载，不是听剧场景）
     */
    fun rate(startUwh: Long, endUwh: Long, durMs: Long, maxRate: Double): Double? {
        if (durMs < MIN_SAMPLE_MS) return null
        val delta = startUwh - endUwh      // 计数器递减，差值为正才是耗电
        if (delta <= 0L) return null       // 回升 = 充电中；不变 = 读数不可信
        val r = delta.toDouble() / durMs
        return if (r > maxRate) null else r
    }

    /**
     * 由两段速率折算累计省电（mAh）。
     *
     * saveRate = onRate - offRate；savedUwh = saveRate × totalMs；savedMWh = /1000。
     *
     * ⚠ 黑屏段比亮屏段更耗电时返回 **null 而不是负数或 0** ——
     * 「省了 -300 mAh」是误导，宁可不给结论。
     */
    fun savingMah(
        onUwh: Long, onMs: Long, onN: Int,
        offUwh: Long, offMs: Long, offN: Int,
        totalMs: Long, minOn: Int, minOff: Int
    ): Int? {
        if (onN < minOn || offN < minOff) return null
        if (onMs <= 0L || offMs <= 0L) return null
        val offRate = offUwh.toDouble() / offMs
        val onRate = onUwh.toDouble() / onMs
        val saveRate = onRate - offRate
        if (saveRate <= 0.0) return null
        val mah = (saveRate * totalMs / 1000.0).toLong()
        return when {
            mah > Int.MAX_VALUE -> Int.MAX_VALUE
            mah < 0L -> 0
            else -> mah.toInt()
        }
    }
}
