// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一枚待收集的能量球（id=生成时刻，唯一） */
data class PendingEnergy(val id: Long, val value: Int, val expireAt: Long)

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

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences("xiiting_prefs", Context.MODE_PRIVATE)

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
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                PendingEnergy(o.getLong("id"), o.getInt("v"), o.getLong("e"))
            }
        } catch (_: Exception) {
            emptyList()
        }
        val valid = list.filter { it.expireAt > now }.sortedBy { it.id }
        if (valid.size != list.size) writePending(ctx, valid)
        return valid
    }

    /** 新增能量球（听剧结算 / 每日分享） */
    fun add(ctx: Context, value: Int) {
        if (value <= 0) return
        val now = System.currentTimeMillis()
        // id 唯一性：取当前时间与现有最大 id+1 的较大者（防同毫秒并发）
        val id = maxOf(now, (pending(ctx).maxOfOrNull { it.id } ?: 0L) + 1)
        val list = pending(ctx) + PendingEnergy(id, value, now + EXPIRE_MS)
        writePending(ctx, list)
    }

    /** 收集一枚能量球，返回获得值（0=不存在/已过期） */
    fun collect(ctx: Context, id: Long): Int {
        val list = pending(ctx).toMutableList()
        val item = list.find { it.id == id } ?: return 0
        list.remove(item)
        writePending(ctx, list)
        val p = sp(ctx)
        p.edit().putInt(KEY_COLLECTED, p.getInt(KEY_COLLECTED, 0) + item.value).apply()
        return item.value
    }

    private fun writePending(ctx: Context, list: List<PendingEnergy>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("v", it.value).put("e", it.expireAt))
        }
        sp(ctx).edit().putString(KEY_PENDING, arr.toString()).apply()
    }
}
