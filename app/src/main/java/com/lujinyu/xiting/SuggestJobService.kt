// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.job.JobParameters
import android.app.job.JobService

/**
 * 息屏建议通知的兜底路径：助手没跑时，JobScheduler 周期唤醒（≥15 分钟），
 * 检测到有媒体在播就发建议。只能知道「有媒体在响」（isMusicActive 无需权限），
 * 不知道是哪个 App —— 精确路径在 OverlayService 的 tick 里（通知使用权已授权时）。
 * 开关（suggest_notify）关着时两条路径都不发。
 */
class SuggestJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                if (!prefs().getBoolean(Prefs.SUGGEST_NOTIFY, false)) return@Thread tryEnd(params)
                if (OverlayService.isRunning) return@Thread tryEnd(params) // 精确路径在服务里
                val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                if (am.isMusicActive) SuggestNotify.fire(this)
            } catch (_: Exception) {
            } finally {
                tryEnd(params)
            }
        }.start()
        return true // 异步处理
    }

    private fun tryEnd(params: JobParameters?) {
        params?.let { jobFinished(it, false) }
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
