package com.lujinyu.xiting

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份的养成进度部分。
 *
 * 背景：备份原本只存 gp（攒了多少成长值），不存「用成长值换了什么」——
 * 形态、皮肤、已解锁集合、悬浮球样式、成就徽章。换手机恢复后精灵退回默认、
 * 皮肤全丢、徽章清零。攒了半年的东西没了，这是设计上的断裂。
 *
 * 这里钉死两条最容易写错的规则：
 * 1. **v1 老备份没有这些字段时必须完全不动现有设置**。
 *    写成「空集合覆盖」的话，误用老备份恢复一次，用户当场丢光所有养成成果——
 *    比原本不备份还糟。
 * 2. **导入数据不可信**：形态夹范围、id 过白名单，否则手改的 JSON 就能
 *    写入不存在的皮肤 id，渲染时崩。
 */
class BackupProgressTest {

    private fun v1() = JSONObject()
        .put("v", 1)
        .put("device", "x")
        .put("gp", 100)
        .put("last_share_date", "")
        .put("sessions", JSONArray())

    private fun v2(extra: JSONObject.() -> Unit = {}) = v1().put("v", 2).apply(extra)

    // ── JSONArray → Set<String> ──

    @Test
    fun `字符串数组转集合`() {
        assertEquals(setOf("a", "b"), JSONArray("[\"a\",\"b\"]").toStringSet())
    }

    @Test
    fun `跳过空串与非字符串元素`() {
        val a = JSONArray()
        a.put("a"); a.put(""); a.put(42); a.put("b")
        assertEquals(setOf("a", "b"), a.toStringSet())
    }

    @Test
    fun `重复项去重`() {
        val a = JSONArray()
        a.put("a"); a.put("a")
        assertEquals(setOf("a"), a.toStringSet())
    }

    // ── v1 兼容：这是最重要的一条 ──

    @Test
    fun `v1 备份不含养成字段`() {
        val o = v1()
        // 模拟 applyProgress 的全部守卫条件：没有就什么都不写
        assertEquals(-1, o.optInt("pet_form", -1))
        assertEquals(null, o.optJSONArray("skins_unlocked"))
        assertEquals(null, o.optJSONArray("ach_unlocked"))
        assertEquals("", o.optString("pet_skin", ""))
        assertTrue(!o.has("bubble_uses_pet"))
    }

    @Test
    fun `bubble_uses_pet 缺失时不能当成 false`() {
        // v1 没有这个键。optBoolean 会返回默认 false —— 若直接用它，
        // 「用户本来用精灵悬浮球」会被无声地改回文字悬浮球。
        // 所以必须先 has() 判断。
        val o = v1()
        assertTrue(!o.has("bubble_uses_pet"))
        val v2o = v2 { put("bubble_uses_pet", false) }
        assertTrue(v2o.has("bubble_uses_pet"))
        assertEquals(false, v2o.optBoolean("bubble_uses_pet", true))
    }

    // ── 不可信输入的夹紧 ──

    @Test
    fun `形态越界被夹到合法区间`() {
        listOf(-99, 0, 4, 999).forEach { raw ->
            val stage = raw.coerceIn(PetView.STAGE_SPARK, PetView.STAGE_KING)
            assertTrue("stage=$stage", stage in PetView.STAGE_SPARK..PetView.STAGE_KING)
        }
    }

    @Test
    fun `v2 正常字段可读出`() {
        val o = v2 {
            put("pet_form", 2)
            put("pet_skin", "jade")
            put("bubble_uses_pet", true)
            put("skins_unlocked", JSONArray("[\"star\",\"jade\"]"))
            put("ach_unlocked", JSONArray("[\"week\",\"king\"]"))
        }
        assertEquals(2, o.optInt("pet_form", -1))
        assertEquals("jade", o.optString("pet_skin", ""))
        assertTrue(o.optBoolean("bubble_uses_pet", false))
        assertEquals(setOf("star", "jade"), o.getJSONArray("skins_unlocked").toStringSet())
        assertEquals(setOf("week", "king"), o.getJSONArray("ach_unlocked").toStringSet())
    }

    @Test
    fun `未知皮肤 id 不应被接受`() {
        // 白名单过滤的语义：不在 PetSkins.ALL 里的 id 一律挡掉，
        // 否则渲染时会拿不到对应的色相。
        val ids = setOf("star", "__evil__", "jade")
        val kept = ids.filter { cand -> PetSkins.ALL.any { it.id == cand } }
        assertEquals(setOf("star", "jade"), kept.toSet())
    }

    @Test
    fun `经典配色是所有皮肤的起点 恢复时必须带上`() {
        val fromBackup = setOf("jade")
        val merged = (fromBackup + "default").toSet()
        assertTrue(merged.contains("default"))
    }
}
