// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 启动图标换肤（成就奖励，v4.0 新增）。
 *
 * 机制：`activity-alias` + PackageManager.setComponentEnabledSetting —— 每个图标
 * 是一个指向 MainActivity 的 alias，同一时刻**恰好启用一个**：全部停用 = 桌面上
 * 图标消失、无法启动，这个不变量由 [apply] 的顺序保证（先启新、再停旧，最坏是
 * 短暂双图标，永不出现零图标）。
 *
 * PackageManager 是唯一事实源（组件启用状态由系统持久化），**不镜像到 prefs**：
 * 重装/清数据后回落 manifest 默认（LauncherDefault enabled = 经典款），
 * 成就解锁态本来就在 ach_unlocked 里，重新应用一次即可。
 *
 * 已知风险（原型验证项，2026-10-11 ColorOS 实测）：桌面图标刷新可能有 1-2 秒延迟；
 * 换图标瞬间部分桌面会闪一下。不杀进程（DONT_KILL_APP），黑幕/服务不中断。
 */
object IconStyle {

    data class Option(
        val id: String,
        val component: ComponentName,
        val title: (Context) -> String,
        /** 解锁所需的成就 id；null = 恒可用 */
        val unlockAchievement: String?
    )

    private const val PKG = "com.lujinyu.xiting"

    /**
     * 图标选项。default 恒可用；其余按 id 对应的成就解锁。
     * 新增图标 = manifest 加 alias + 这里加一行。
     */
    val OPTIONS = listOf(
        Option("default", ComponentName(PKG, "$PKG.LauncherDefault"),
            { c -> c.getString(R.string.app_name) }, null),
        Option("king", ComponentName(PKG, "$PKG.LauncherCrown"),
            { c -> Achievements.title(c, Achievements.ALL.first { it.id == "king" }) }, "king")
    )

    private fun option(id: String): Option = OPTIONS.first { it.id == id }

    /** 当前生效的图标 id。全为 DEFAULT 状态（从未切换过）时返回 "default"。 */
    fun currentId(ctx: Context): String {
        for (o in OPTIONS) {
            val st = ctx.packageManager.getComponentEnabledSetting(o.component)
            if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return o.id
        }
        return "default"
    }

    /** 该图标是否已解锁（default 恒可用；其余按成就） */
    fun isUnlocked(ctx: Context, id: String): Boolean {
        val unlock = option(id).unlockAchievement ?: return true
        return unlock in Achievements.unlockedIds(ctx)
    }

    /** 换图标：先启新、再停旧；DONT_KILL_APP 保活进程（黑幕/服务不中断） */
    fun apply(ctx: Context, id: String) {
        require(OPTIONS.any { it.id == id }) { "未知图标 id：$id" }
        val pm = ctx.packageManager
        pm.setComponentEnabledSetting(
            option(id).component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        for (o in OPTIONS) {
            if (o.id != id) {
                pm.setComponentEnabledSetting(
                    o.component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
