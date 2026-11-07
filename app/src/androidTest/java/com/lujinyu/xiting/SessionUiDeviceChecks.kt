package com.lujinyu.xiting

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.Spinner
import java.util.Locale

/**
 * 通知栏正文与桌面小组件共用的状态文案，以及定时弹窗取消后的残留检查。
 *
 * 本类原先没有任何调用点，等于从没跑过；且断言对着 v5.0.1 之前的实现：
 * 控制台卡片（当时的 `bindSessionConsole` 已退化成 `= Unit` 空壳，2026-11-06 已删）、
 * 场景标签 `v50_scene_*`（已撤掉的堆叠）、
 * 以及「summary 含 v50_play_unknown」——而 `pause()` 把 `NONE` 提前判成空串，
 * 当前实现不可能输出那句措辞。
 *
 * 处理方式：摘掉死界面的断言，保留并补强仍在线上跑的部分——
 * summary 的定时三种形态、pause 的六个取值（逐语言核对，防止某个语言包把通知文案改坏或改空），
 * 以及「取消定时不留下偏好写入」「听完这集预设选中第 5 项」这两条对现行实现仍然成立的检查。
 */
internal class SessionUiDeviceChecks(private val instrumentation:Instrumentation) {
    private val ctx get()=instrumentation.targetContext
    private val languages=listOf("zh","en","de","es","fr","ja","ko","ru")
    private fun main(block:()->Unit) {
        var error:Throwable?=null
        instrumentation.runOnMainSync { try { block() } catch(t:Throwable) { error=t } }
        error?.let { throw it }
    }

    fun run() { summaryAcrossLocales();pauseReports();timerDialogLeavesNothingBehind() }

    private fun localized(language:String)=ctx.createConfigurationContext(
        Configuration(ctx.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })

    private fun summaryAcrossLocales() {
        for (language in languages) {
            val l=localized(language)
            val idle=SessionConsoleUi.summary(l,SessionSnapshot())
            check(idle.contains(l.getString(R.string.widget_idle))) {"$language: 未运行时的通知正文丢了「未运行」措辞"}

            val running=SessionConsoleUi.summary(l,SessionSnapshot(assistant=AssistantPhase.RUNNING))
            check(running==l.getString(R.string.notif_text,"")) {
                "$language: 无定时的正文应恰好等于 notif_text，实际「$running」"}

            val two=SessionConsoleUi.summary(l,SessionSnapshot(assistant=AssistantPhase.RUNNING,
                timer=TimerPhase.FIXED,remainingMs=90_000))
            check(two.contains(l.getString(R.string.notif_timer_fmt,2))) {
                "$language: 90 秒应向上折算成 2 分钟并出现在正文，实际「$two」"}
            check(!two.contains(l.getString(R.string.notif_timer_fmt,1))) {"$language: 分钟折算位数写错"}

            val waiting=SessionConsoleUi.summary(l,SessionSnapshot(assistant=AssistantPhase.RUNNING,
                timer=TimerPhase.WAITING,remainingMs=0))
            check(waiting.contains(l.getString(R.string.timer_episode_waiting))) {
                "$language: 等待播放继续的定时没有对应措辞"}

            // 定时还在但助手没起：不该播报剩余时间，也不该把空闲文案挤成空
            val idleWithTimer=SessionConsoleUi.summary(l,SessionSnapshot(timer=TimerPhase.FIXED,remainingMs=600_000))
            check(!idleWithTimer.contains(l.getString(R.string.notif_timer_fmt,10))) {
                "$language: 助手未运行时不应播报定时剩余"}
            check(idleWithTimer.contains(l.getString(R.string.widget_idle))) {"$language: 空闲态文案被定时挤掉了"}
        }
    }

    private fun pauseReports() {
        for (report in PauseReport.values()) {
            val text=SessionConsoleUi.pause(ctx,report)
            check((report==PauseReport.NONE)==text.isEmpty()) {"$report 的措辞与空串对应错了：「$text」"}
            if(report!=PauseReport.NONE) {
                val expected=ctx.getString(when(report) {
                    PauseReport.REQUESTED -> R.string.v50_pause_requested
                    PauseReport.CONFIRMED -> R.string.v50_pause_confirmed
                    PauseReport.ALREADY_PAUSED -> R.string.v50_pause_already
                    PauseReport.UNAVAILABLE -> R.string.v50_pause_unavailable
                    PauseReport.FAILED -> R.string.v50_pause_failed
                    PauseReport.UNCONFIRMED -> R.string.v50_pause_unconfirmed
                    PauseReport.NONE -> R.string.v50_play_unknown
                })
                check(text==expected) {"$report 应显示「$expected」，实际「$text」"}
            }
        }
        // 结果没回来时只能说不确定，不能替播放器宣布已暂停
        for (language in languages) {
            val l=localized(language)
            val unconfirmed=SessionConsoleUi.summary(l,SessionSnapshot(assistant=AssistantPhase.RUNNING,
                curtain=CurtainPhase.LOCKED,timer=TimerPhase.FIXED,remainingMs=90_000,
                pauseKeepBlack=true,pause=PauseReport.UNCONFIRMED))
            check(unconfirmed.contains(l.getString(R.string.v50_pause_unconfirmed))) {"$language: 缺少「未确认」措辞"}
            check(!unconfirmed.contains(l.getString(R.string.v50_pause_confirmed))) {"$language: 把未确认写成了已暂停"}
        }
    }

    private fun timerDialogLeavesNothingBehind() {
        val oldScene=ctx.prefs().getString("listening_scene_v1",null)
        val oldPause=ctx.prefs().getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false)
        val activity=instrumentation.startActivitySync(Intent(ctx,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            var dialog:AlertDialog?=null
            main {
                dialog=activity.showTimerOptions(ListeningPreset(30,true),confirmedScene="sleep")
                dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            main {
                check(dialog?.isShowing==false) {"取消按钮没有关闭弹窗"}
                check(ctx.prefs().getString("listening_scene_v1",null)==oldScene) {"取消却写了场景"}
                check(ctx.prefs().getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false)==oldPause) {"取消却改了结束动作"}
                val episode=activity.showTimerOptions(initialEpisode=true,confirmedScene="episode")
                fun firstSpinner(view:View):Spinner? {
                    if(view is Spinner)return view
                    if(view is android.view.ViewGroup)for(n in 0 until view.childCount) {
                        firstSpinner(view.getChildAt(n))?.let { return it }
                    }
                    return null
                }
                check(firstSpinner(episode.window!!.decorView)?.selectedItemPosition==4) {"「听完这集」没有落到预设第 5 项"}
                episode.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                check(ctx.prefs().getString("listening_scene_v1",null)==oldScene) {"取消集末定时却写了场景"}
            }
        } finally { main { activity.finish() } }
    }
}
