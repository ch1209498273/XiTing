package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 持久化键名与文件名守卫。
 *
 * 这些字符串一旦发布就不能改：改了等于全体用户丢失该项设置
 * （读不到旧键即静默回落到默认值）。编译器管不住字符串字面量，
 * 只有把「键名 ↔ 常量」钉在测试里才有人会注意到改动。
 */
class PrefsKeysTest {

    @Test
    fun `文件名与历史一致`() {
        assertEquals("xiiting_prefs", Prefs.FILE)
    }

    @Test
    fun `设置类键名不变`() {
        assertEquals("assistant_wanted", Prefs.ASSISTANT_WANTED)
        assertEquals("bubble_perm_error", Prefs.BUBBLE_PERM_ERROR)
        assertEquals("bubble_hidden", Prefs.BUBBLE_HIDDEN)
        assertEquals("bubble_style", Prefs.BUBBLE_STYLE)
        assertEquals("bubble_x", Prefs.BUBBLE_X)
        assertEquals("bubble_y", Prefs.BUBBLE_Y)
    }

    @Test
    fun `黑幕行为键名不变`() {
        assertEquals("direct_unlock", Prefs.DIRECT_UNLOCK)
        assertEquals("black_info_show", Prefs.BLACK_INFO_SHOW)
        assertEquals("black_media_controls", Prefs.BLACK_MEDIA_CONTROLS)
        assertEquals("switch_headset", Prefs.SWITCH_HEADSET)
    }

    @Test
    fun `养成类键名不变`() {
        assertEquals("energy_pending", Prefs.ENERGY_PENDING)
        assertEquals("energy_collected_total", Prefs.ENERGY_COLLECTED)
        assertEquals("energy_migrated_v1", Prefs.ENERGY_MIGRATED)
        assertEquals("energy_carry_ms", Prefs.ENERGY_CARRY_MS)
        assertEquals("last_rare_date", Prefs.LAST_RARE_DATE)
    }

    @Test
    fun `只读遗留键不能被改名`() {
        // 早期版本写入的分享奖励成长值，老用户 prefs 里仍有值。
        // 改名 = 这部分用户的迁移加成静默归零。
        assertEquals("share_bonus_gp", Prefs.SHARE_BONUS_GP_LEGACY)
    }

    @Test
    fun `成就与皮肤键名不变`() {
        assertEquals("ach_unlocked", Prefs.ACH_UNLOCKED)
        assertEquals("skins_unlocked", Prefs.SKINS_UNLOCKED)
        assertEquals("pet_skin", Prefs.PET_SKIN)
        assertEquals("last_seen_stage", Prefs.LAST_SEEN_STAGE)
    }

    @Test
    fun `分享与校准键名不变`() {
        assertEquals("last_share_date", Prefs.LAST_SHARE_DATE)
        assertEquals("pwr_samp_ua_sum", Prefs.PWR_SAMP_UA_SUM)
        assertEquals("pwr_samp_count", Prefs.PWR_SAMP_COUNT)
        assertEquals("calib_on_ua", Prefs.CALIB_ON_UA)
        assertEquals("calib_off_ua", Prefs.CALIB_OFF_UA)
        assertEquals("calib_ts", Prefs.CALIB_TS)
    }

    @Test
    fun `键名不得重复`() {
        // 重复键名会让两个功能共用一个存储位，互不相关的设置互相覆盖
        val values = listOf(
            Prefs.ASSISTANT_WANTED, Prefs.BUBBLE_PERM_ERROR, Prefs.BUBBLE_HIDDEN,
            Prefs.BUBBLE_STYLE, Prefs.BUBBLE_X, Prefs.BUBBLE_Y,
            Prefs.DIRECT_UNLOCK, Prefs.BLACK_INFO_SHOW, Prefs.BLACK_MEDIA_CONTROLS,
            Prefs.SWITCH_HEADSET, Prefs.ENERGY_PENDING, Prefs.ENERGY_COLLECTED,
            Prefs.ENERGY_MIGRATED, Prefs.ENERGY_CARRY_MS, Prefs.LAST_RARE_DATE,
            Prefs.SHARE_BONUS_GP_LEGACY, Prefs.ACH_UNLOCKED, Prefs.SKINS_UNLOCKED,
            Prefs.PET_SKIN, Prefs.LAST_SEEN_STAGE, Prefs.LAST_SHARE_DATE,
            Prefs.PWR_SAMP_UA_SUM, Prefs.PWR_SAMP_COUNT, Prefs.CALIB_ON_UA,
            Prefs.CALIB_OFF_UA, Prefs.CALIB_TS
        )
        val dup = values.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("键名重复：$dup", dup.isEmpty())
    }
}