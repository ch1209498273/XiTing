// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

/**
 * 省电实测：**被动测量**，用电池能量计数器的**差值**。
 *
 * ## 为什么重做
 * 旧实现要求用户「亮屏播 3 分钟 + 黑屏 3 分钟、不能碰手机、不能充电」——
 * 6 分钟的打扰式测试换取一个估算数字，而且用的是 `BATTERY_PROPERTY_CURRENT_NOW`
 * （瞬时电流）：噪声极大，需要采样 30 次求平均才能勉强压住，部分机型还直接返回 0。
 *
 * 真机探针（ProbeBattery，2026-10-07，OPPO PME110）确认：
 * - `CHARGE_COUNTER`（剩余能量 µWh）**可用** → 差值测量成立
 * - `CURRENT_AVERAGE` / `ENERGY_COUNTER` 返回 Long.MIN_VALUE，不可用
 * - `CURRENT_NOW` 在本机亮屏时读到 542µA，量级与「整机耗电」不符，语义存疑
 *
 * ## 为什么差值比瞬时电流准
 * 瞬时电流每分钟跳一次，屏幕内容、CPU 负载、信号强度都在影响它，采样 30 次求平均
 * 仍是个粗糙估计。能量计数器给的是**累计消耗的绝对值**：读一次「开始」再读一次
 * 「结束」，差值就是这段时间真实消耗的电能。**一次会话 = 一个精确样本**，
 * 不需要攒量，也就不需要用户配合。
 *
 * ## 样本怎么来（零打扰）
 * - **黑屏段**：每次息屏会话开始/结束时各读一次
 * - **亮屏段**：App 运行时在后台周期读，**不需要用户做任何事**
 *
 * ## 有效性过滤
 * - 充电中 → 丢弃（计数器会回升）
 * - 计数不变或增加 → 丢弃（读数不可信）
 * - 样本过短（<2 分钟）→ 丢弃（差值太小，被量化误差淹没）
 * - 样本过短或耗电率离谱 → 丢弃
 */
object PowerCalib {

    private const val TAG = "XiTing"

    // ---- 存储键（旧键保留只为读取，历史数据不丢）----
    private const val K_ON_UA = "calib_on_ua"
    private const val K_OFF_UA = "calib_off_ua"
    private const val K_TS = "calib_ts"

    /** 黑屏段累计耗电（µWh），用于取平均 */
    private const val K_OFF_UWH = "meas_off_uwh"
    private const val K_OFF_MS = "meas_off_ms"
    /** 亮屏段累计耗电（µWh）与时长 */
    private const val K_ON_UWH = "meas_on_uwh"
    private const val K_ON_MS = "meas_on_ms"
    /** 各自的样本数（用于置信度展示） */
    private const val K_OFF_N = "meas_off_n"
    private const val K_ON_N = "meas_on_n"
    /** 已被过滤掉的样本数（诊断用） */
    private const val K_DROPPED = "meas_dropped"

    /** 样本最短时长：太短的差值会被计数器量化误差淹没，得不偿失 */
    const val MIN_SAMPLE_MS = 120_000L          // 2 分钟
    /** 黑屏段至少要这么多样本才认为亮屏基准可用（亮屏基准更容易被噪声污染） */
    private const val MIN_OFF_SAMPLES = 3
    private const val MIN_ON_SAMPLES = 3

    /**
     * 单样本的功耗率上限（µWh/ms → 折算 mA）。
     * 超过 1500mA 的「整机功耗」不可能是听剧场景（那是充电或高负载），
     * 判为异常样本丢弃。
     */
    private const val MAX_RATE_MA = 1500.0

    // ────────────────────────── 采样 ──────────────────────────

    /** 读当前剩余能量（µWh）。不可用返回 null */
    fun energyCounter(ctx: Context): Long? {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
            val v = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (v <= 0L || v == Long.MIN_VALUE) null else v
        } catch (_: Exception) {
            null
        }
    }

    /** 是否在充电。**只要插着电就一律不测** —— 计数器会回升，差值无意义 */
    fun isCharging(ctx: Context): Boolean {
        return try {
            val st = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = st?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val status = st?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) {
            true // 读不到就当作在充电，宁可少测不可测错
        }
    }

    /**
     * 记录一个样本段。startUwh/endUwh 是同一时间轴上的两个能量读数。
     *
     * 返回是否采纳。
     */
    fun recordSegment(
        ctx: Context, startUwh: Long, endUwh: Long, durationMs: Long, black: Boolean
    ): Boolean {
        if (durationMs < MIN_SAMPLE_MS) return false
        // 算式统一在 MeasMath（纯函数，可单测）——不要在这里重写一遍，
        // 否则单测测的就不是线上跑的那套了
        val r = MeasMath.rate(startUwh, endUwh, durationMs, MAX_RATE_MA) ?: run {
            bumpDropped(ctx); return false
        }
        try {
            val p = ctx.prefs()
            val ed = p.edit()
            if (black) {
                ed.putLong(K_OFF_UWH, p.getLong(K_OFF_UWH, 0L) + (startUwh - endUwh))
                ed.putLong(K_OFF_MS, p.getLong(K_OFF_MS, 0L) + durationMs)
                ed.putInt(K_OFF_N, p.getInt(K_OFF_N, 0) + 1)
            } else {
                ed.putLong(K_ON_UWH, p.getLong(K_ON_UWH, 0L) + (startUwh - endUwh))
                ed.putLong(K_ON_MS, p.getLong(K_ON_MS, 0L) + durationMs)
                ed.putInt(K_ON_N, p.getInt(K_ON_N, 0) + 1)
            }
            ed.putLong(K_TS, System.currentTimeMillis())
            ed.apply()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "记录样本失败: $e")
            return false
        }
    }

    private fun bumpDropped(ctx: Context) {
        try {
            ctx.prefs().edit().putInt(K_DROPPED, ctx.prefs().getInt(K_DROPPED, 0) + 1).apply()
        } catch (_: Exception) {
        }
    }

    // ────────────────────────── 读取与折算 ──────────────────────────

    data class Confidence(val offN: Int, val onN: Int, val dropped: Int) {
        /** 样本越多越可信，UI 上照这个显示 */
        val level: Int
            get() = when {
                offN >= 30 && onN >= 30 -> 3
                offN >= 10 && onN >= 10 -> 2
                offN >= MIN_OFF_SAMPLES && onN >= MIN_ON_SAMPLES -> 1
                else -> 0
            }
    }

    fun confidence(ctx: Context): Confidence {
        val p = ctx.prefs()
        return Confidence(
            p.getInt(K_OFF_N, 0), p.getInt(K_ON_N, 0), p.getInt(K_DROPPED, 0)
        )
    }

    /**
     * 依据实测的亮屏/黑屏能耗差，折算「用本 App 息屏听剧」的累计省电（mAh）。
     *
     * 未达最小样本返回 null（UI 回落���通用模型估算）。
     *
     * 数学：saveRate = onRate - offRate（µWh/ms）
     *       savedUwh = saveRate × totalBlackMs
     *       savedMwh = savedUwh / 1000
     * 这里把 mWh 直接当 mA·h 报（1 mWh 在 3.7V 下约等于 1 mAh，量纲上是一致能量），
     * 与旧版「(onUa - offUa) / 1000」同口径，数字可对照。
     */
    fun savingMah(ctx: Context, totalBlackMs: Long): Int? {
        val p = ctx.prefs()
        return MeasMath.savingMah(
            onUwh = p.getLong(K_ON_UWH, 0L), onMs = p.getLong(K_ON_MS, 0L),
            onN = p.getInt(K_ON_N, 0),
            offUwh = p.getLong(K_OFF_UWH, 0L), offMs = p.getLong(K_OFF_MS, 0L),
            offN = p.getInt(K_OFF_N, 0),
            totalMs = totalBlackMs,
            minOn = MIN_ON_SAMPLES, minOff = MIN_OFF_SAMPLES
        )
    }

    /**
     * 黑屏段整机能耗速率（µWh/ms）与样本数，用于「实测功耗」展示。样本不足返回 null。
     *
     * 不换算成 mA：mA 需要「功率 ÷ 电压」，而电池电压随电量在 3.5~4.4V 浮动，
     * 换算反而引入误差。这里只保留能量速率，够用于「黑屏时每分钟消耗多少」这类展示。
     */
    fun blackScreenPower(ctx: Context): Pair<Double, Int>? {
        val p = ctx.prefs()
        val n = p.getInt(K_OFF_N, 0)
        val ms = p.getLong(K_OFF_MS, 0L)
        if (n < MIN_OFF_SAMPLES || ms <= 0L) return null
        val rateUwhPerMs = p.getLong(K_OFF_UWH, 0L).toDouble() / ms
        return rateUwhPerMs to n
    }

    /** 清空实测数据（重新开始） */
    fun reset(ctx: Context) {
        ctx.prefs().edit()
            .remove(K_OFF_UWH).remove(K_OFF_MS).remove(K_ON_UWH).remove(K_ON_MS)
            .remove(K_OFF_N).remove(K_ON_N).remove(K_DROPPED)
            .apply()
    }

    // ────────────────────────── 旧接口（保留兼容，不再写入）──────────────────────────

    /** 旧：瞬时电流均值。已不再用于折算，保留仅供旧 UI 读取 */
    fun calibrated(ctx: Context): Triple<Long, Long, Long>? {
        val p = ctx.prefs()
        val on = p.getLong(K_ON_UA, 0L)
        val off = p.getLong(K_OFF_UA, 0L)
        val ts = p.getLong(K_TS, 0L)
        return if (ts == 0L || on <= off) null else Triple(off, on, ts)
    }

    /** 旧：按旧校准折算。已让位给 [savingMah] */
    fun calibratedSavingMah(ctx: Context, totalMs: Long): Int? =
        savingMah(ctx, totalMs)
}
