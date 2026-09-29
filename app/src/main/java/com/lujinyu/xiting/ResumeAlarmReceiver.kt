// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import android.view.KeyEvent

/**
 * 真息屏续播闹钟：ColorOS(hans)会在锁屏后冻结本进程、拦截广播并强制释放唤醒锁，
 * 进程内的postDelayed注入永远无法执行。改用setAlarmClock精确闹钟——
 * 系统最高优先级、冻结豁免，能在锁屏前预布、锁屏后按时唤醒本接收器注入播放键。
 *
 * 时间线：悬浮球锁屏瞬间预布 +1s/+3s/+6s/+10s 四发（首次恢复），
 * 每发自链+8s续约，共约3分钟——覆盖片尾被掐断后的自动续播窗口。
 */
class ResumeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) return // 用户已亮屏：终止保活窗口

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val tick = intent.getIntExtra(EXTRA_TICK, 0)
        if (!am.isMusicActive) {
            // 静默中：注入播放键（锁屏后的首次恢复 / 片尾被掐断后的续播）
            val code = if (tick >= 3) KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
            try {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            } catch (_: Exception) {
            }
        }

        if (tick < MAX_TICK) {
            schedule(context, tick + 1, System.currentTimeMillis() + 8000)
        }
    }

    companion object {
        const val EXTRA_TICK = "tick"
        const val MAX_TICK = 20 // 1+3+6+10s首布 + 8s×17 ≈ 3分钟保活窗口

        fun schedule(context: Context, tick: Int, at: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, tick,
                Intent(context, ResumeAlarmReceiver::class.java).putExtra(EXTRA_TICK, tick),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val show = PendingIntent.getActivity(
                context, 0,
                Intent(context, com.lujinyu.xiting.MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        }
    }
}
