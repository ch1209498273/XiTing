// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences 的文件名与键名唯一来源。
 *
 * 为什么要有这一层：此前 26 个键散落在 8 个文件里、全部是裸字符串字面量，
 * 且 `getSharedPreferences("xiiting_prefs", MODE_PRIVATE)` 在 MainActivity 内部
 * 就重复了 11 次。裸字符串的代价是编译器管不着——把 `bubble_hidden` 拼成
 * `bubble_hide`，编译照过，但用户的设置从此读写到另一个键上，界面表现是
 * 「开关拨了没反应」。集中成常量后，改名变成一次全局可见的重构。
 *
 * ⚠ 键名一旦发布就不能改：改了等于全体用户丢失该设置（读不到旧键即回落默认值）。
 * 确实要重命名时，必须同时写迁移代码读旧键。
 */
object Prefs {

    /** 主设置文件（几乎所有设置与养成数据） */
    const val FILE = "xiiting_prefs"

    /** 累计听剧时长统计文件，与 FILE 分开存放 */
    const val FILE_STATS = "xiiting_stats"

    // ---- 助手生命周期 ----
    /** 用户是否希望助手运行：区分「系统杀了」与「用户主动退出」 */
    const val ASSISTANT_WANTED = "assistant_wanted"

    // ---- 悬浮球 ----
    /** 悬浮窗权限表征与内核态不一致（重装后 ColorOS 常见），用于首页引导修复 */
    const val BUBBLE_PERM_ERROR = "bubble_perm_error"
    const val BUBBLE_HIDDEN = "bubble_hidden"
    /** "text" 或 "pet_<stage>" */
    const val BUBBLE_STYLE = "bubble_style"
    const val BUBBLE_X = "bubble_x"
    const val BUBBLE_Y = "bubble_y"

    // ---- 黑幕行为 ----
    /** 轻点直接解锁，跳过两段式确认 */
    const val DIRECT_UNLOCK = "direct_unlock"
    /** 黑幕上显示暗色夜钟与电量 */
    const val BLACK_INFO_SHOW = "black_info_show"
    /** 黑幕内媒体控制行（上一集/播放暂停/下一集） */
    const val BLACK_MEDIA_CONTROLS = "black_media_controls"
    /** 耳机拔出/蓝牙断连自动撤幕 */
    const val SWITCH_HEADSET = "switch_headset"

    // ---- 能量与成长值 ----
    const val ENERGY_PENDING = "energy_pending"
    const val ENERGY_COLLECTED = "energy_collected_total"
    const val ENERGY_MIGRATED = "energy_migrated_v1"
    /** 每满 1 分钟 1 点能量的跨会话余数累积（毫秒），不浪费零头 */
    const val ENERGY_CARRY_MS = "energy_carry_ms"
    const val LAST_RARE_DATE = "last_rare_date"

    /**
     * 只读遗留键：早期版本写入的「分享奖励成长值」，当前版本不再写，
     * 但老用户 prefs 里仍有值。[EnergyStore.migrateIfNeeded] 首次运行时用它。
     * 删除读取会让这部分老用户的迁移加成静默归零，因此必须保留。
     */
    const val SHARE_BONUS_GP_LEGACY = "share_bonus_gp"

    // ---- 成就与皮肤 ----
    const val ACH_UNLOCKED = "ach_unlocked"
    const val SKINS_UNLOCKED = "skins_unlocked"
    const val PET_SKIN = "pet_skin"
    /** 上次展示过的精灵形态，用于「新形态进化」提示去重 */
    const val LAST_SEEN_STAGE = "last_seen_stage"

    // ---- 分享 ----
    const val LAST_SHARE_DATE = "last_share_date"

    // ---- 省电校准（PowerCalib 使用）----
    const val PWR_SAMP_UA_SUM = "pwr_samp_ua_sum"
    const val PWR_SAMP_COUNT = "pwr_samp_count"
    const val CALIB_ON_UA = "calib_on_ua"
    const val CALIB_OFF_UA = "calib_off_ua"
    const val CALIB_TS = "calib_ts"

    // ---- FILE_STATS ----
    const val STATS_TOTAL_MS = "total_ms"
    const val STATS_COUNT = "count"
}

/** 主 prefs。此前 `getSharedPreferences("xiiting_prefs", MODE_PRIVATE)` 在 MainActivity 内就重复 11 次。 */
fun Context.prefs(): SharedPreferences =
    getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

/** 累计时长统计 prefs，与主设置分开存放 */
fun Context.prefsStats(): SharedPreferences =
    getSharedPreferences(Prefs.FILE_STATS, Context.MODE_PRIVATE)