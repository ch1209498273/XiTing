// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

/**
 * 省电实测：黑屏会话期间周期性采样电池瞬时电流（BATTERY_PROPERTY_CURRENT_NOW），
 * 积累"息屏听剧整机功耗"的本机真实均值；并提供 5 分钟校准向导所需的两段式采样
 * （亮屏看视频 vs 黑屏听剧）差值 → 本机真实省电速率（mAh/小时）。
 *
 * 采样有效性规则：充电中 / 电量 <15% 或 >95% / 读数为 0 时丢弃（电流读数失真）。
 */
object PowerCalib {

    private const val TAG = "XiTing"
    private const val K_SUM = "pwr_samp_ua_sum"   // 有效采样累计（µA）
    private const val K_CNT = "pwr_samp_count"    // 有效采样次数
    private const val K_ON = "calib_on_ua"        // 校准：亮屏均值（µA）
    private const val K_OFF = "calib_off_ua"      // 校准：黑屏均值（µA）
    private const val K_TS = "calib_ts"           // 校准完成时间
    private const val MIN_SAMPLES = 30            // 会话采样达到该次数才展示实测均值

    /** 读当前电池电流（µA，取绝对值）；无效场景返回 null */
    fun sampleNow(ctx: Context): Long? {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
            val now = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (now == 0L || now == Long.MIN_VALUE) return null
            val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            if (plugged != 0) return null // 充电中读数失真
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (scale > 0) level * 100 / scale else 50
            if (pct < 15 || pct > 95) return null
            Math.abs(now)
        } catch (_: Exception) {
            null
        }
    }

    /** 会话期间采样入库 */
    fun recordSample(ctx: Context, ua: Long) {
        try {
            val p = ctx.prefs()
            p.edit()
                .putLong(K_SUM, p.getLong(K_SUM, 0L) + ua)
                .putInt(K_CNT, p.getInt(K_CNT, 0) + 1)
                .apply()
        } catch (_: Exception) {
        }
    }

    /** 实测黑屏听剧整机均值（mA）与采样次数；样本不足返回 null */
    fun ambientStats(ctx: Context): Pair<Double, Int>? {
        val p = ctx.prefs()
        val n = p.getInt(K_CNT, 0)
        if (n < MIN_SAMPLES) return null
        val avgUa = p.getLong(K_SUM, 0L).toDouble() / n
        return (avgUa / 1000.0) to n
    }

    /** 校准结果（亮屏µA, 黑屏µA, 完成时间）或 null */
    fun calibrated(ctx: Context): Triple<Long, Long, Long>? {
        val p = ctx.prefs()
        val on = p.getLong(K_ON, 0L)
        val off = p.getLong(K_OFF, 0L)
        val ts = p.getLong(K_TS, 0L)
        if (ts == 0L || on <= off) return null
        return Triple(on, off, ts)
    }

    /** 保存校准结果 */
    fun storeCalibration(ctx: Context, onUa: Long, offUa: Long) {
        ctx.prefs().edit()
            .putLong(K_ON, onUa).putLong(K_OFF, offUa)
            .putLong(K_TS, System.currentTimeMillis())
            .apply()
        Log.i(TAG, "省电校准完成: on=$onUa off=$offUa")
    }

    /** 按本机实测速率折算累计省电（mAh）；未校准返回 null */
    fun calibratedSavingMah(ctx: Context, totalMs: Long): Int? {
        val c = calibrated(ctx) ?: return null
        val mahPerHour = (c.first - c.second) / 1000.0
        return (mahPerHour * totalMs / 3_600_000.0).toInt()
    }
}
