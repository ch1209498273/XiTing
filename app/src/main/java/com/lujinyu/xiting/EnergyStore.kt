// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一枚待收集的能量球（id=生成时刻，唯一） */
data class PendingEnergy(val id: Long, val value: Int, val expireAt: Long)

/**
 * 解析待收能量 JSON（纯函数，可单测）。
 *
 * 逐条 try/catch 跳过损坏记录。这里曾用 map()，一条脏记录就让整份待收能量
 * 变成 emptyList()——所有能量球静默消失。
 * 文本整体语法非法时抛给调用方。
 */
internal fun parsePending(raw: String): ParseResult<PendingEnergy> {
    val arr = JSONArray(raw)
    var skipped = 0
    val items = (0 until arr.length()).mapNotNull { i ->
        try {
            val o = arr.getJSONObject(i)
            PendingEnergy(o.getLong("id"), o.getInt("v"), o.getLong("e"))
        } catch (e: Exception) {
            skipped++
            null
        }
    }
    return ParseResult(items, skipped)
}

/**
 * 还能再塞多少能量（纯函数，可单测）。
 * 用 Long 累加：existing 来自持久化 prefs，单条 value 若是脏数据里的 Int.MAX，
 * Int 累加会溢出成负数，让 room 反而变成正数而突破上限。
 */
internal fun roomFor(existing: List<PendingEnergy>, max: Int): Int =
    (max - existing.sumOf { it.value.toLong() })
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/** 成长值累加，饱和不溢出（纯函数，可单测） */
internal fun saturatingAdd(current: Int, delta: Long): Int =
    (current.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/**
 * 能量收集机制（蚂蚁森林式）：
 * 听剧/每日分享产生能量球 → 用户在统计页主动点击收集 → 汇入成长值。
 * 能量球 3 天内未收集会过期消失（损失厌恶促回访）。
 * 旧版"成长值=听剧分钟+分享奖励"在首次运行时一次性迁入已收集总量。
 */
object EnergyStore {

    private const val KEY_PENDING = "energy_pending"
    private const val KEY_COLLECTED = "energy_collected_total"
    private const val KEY_MIGRATED = "energy_migrated_v1"

    const val EXPIRE_MS = 3L * 24 * 3600 * 1000   // 3 天过期
    const val NEAR_EXPIRE_MS = 12L * 3600 * 1000  // 12 小时内临期（变色提醒）
    const val MAX_PENDING = 200                   // 待收能量上限（满格后不再累积）

    private fun sp(ctx: Context) =
        ctx.prefs()

    /** 旧版成长值一次性迁入（听剧分钟 + 分享奖励），避免老用户进度清零 */
    fun migrateIfNeeded(ctx: Context, listenMinutes: Long, legacyBonus: Int) {
        val p = sp(ctx)
        if (p.getBoolean(KEY_MIGRATED, false)) return
        val total = (listenMinutes + legacyBonus).coerceAtLeast(0)
        p.edit()
            .putInt(KEY_COLLECTED, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .putBoolean(KEY_MIGRATED, true)
            .apply()
    }

    /** 已收集总量（=成长值） */
    fun collectedTotal(ctx: Context): Int = sp(ctx).getInt(KEY_COLLECTED, 0)

    /** 待收集能量（顺带清理过期项并回写） */
    fun pending(ctx: Context): List<PendingEnergy> {
        val p = sp(ctx)
        val now = System.currentTimeMillis()
        val raw = p.getString(KEY_PENDING, "[]") ?: "[]"
        val list = try {
            parsePending(raw).items
        } catch (_: Exception) {
            emptyList()
        }
        val valid = list.filter { it.expireAt > now }.sortedBy { it.id }
        if (valid.size != list.size) writePending(ctx, valid)
        return valid
    }

    /** 新增能量（听剧结算 / 每日分享）；满格（200）后不再累积，先收再产 */
    fun add(ctx: Context, value: Int) {
        if (value <= 0) return
        val exist = pending(ctx)
        val room = roomFor(exist, MAX_PENDING)
        if (room <= 0) return
        val v = value.coerceAtMost(room)
        val now = System.currentTimeMillis()
        // id 唯一性：取当前时间与现有最大 id+1 的较大者（防同毫秒并发）
        val id = maxOf(now, (exist.maxOfOrNull { it.id } ?: 0L) + 1)
        writePending(ctx, exist + PendingEnergy(id, v, now + EXPIRE_MS))
    }

    /** 收集一枚能量球，返回获得值（0=不存在/已过期） */
    fun collect(ctx: Context, id: Long): Int {
        val list = pending(ctx).toMutableList()
        val item = list.find { it.id == id } ?: return 0
        list.remove(item)
        writePending(ctx, list)
        credit(ctx, item.value.toLong())
        return item.value
    }

    /**
     * 收集全部待收能量，返回总量。
     *
     * 原实现是 sum += collect(id) 逐枚调用，而每枚 collect() 都会
     * 重新 pending()（读 prefs + 解析 JSON + 过期过滤）再 writePending()（全量回写），
     * 于是 n 枚球产生 O(n²) 次序列化。n 上限 200 时，一次点击能量条
     * 最多触发 400 次全量 SharedPreferences 写——低端机上肉眼可见的卡顿。
     * 这里改成「读一次、算总和、清一次」，O(1) 次写。
     */
    fun collectAll(ctx: Context): Int {
        val list = pending(ctx)
        if (list.isEmpty()) return 0
        val sum = list.sumOf { it.value }
        writePending(ctx, emptyList())
        credit(ctx, sum.toLong())
        return sum
    }

    /** 成长值累加（饱和，不允许溢出成负数） */
    private fun credit(ctx: Context, delta: Long) {
        val p = sp(ctx)
        p.edit()
            .putInt(KEY_COLLECTED, saturatingAdd(p.getInt(KEY_COLLECTED, 0), delta))
            .apply()
    }

    private fun writePending(ctx: Context, list: List<PendingEnergy>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("v", it.value).put("e", it.expireAt))
        }
        sp(ctx).edit().putString(KEY_PENDING, arr.toString()).apply()
    }
}
