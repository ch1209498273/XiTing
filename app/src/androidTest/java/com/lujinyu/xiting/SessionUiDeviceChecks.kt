package com.lujinyu.xiting

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Spinner
import android.widget.TextView
import java.util.Locale

/** Synthetic emulator checks for the added v5 surfaces; does not arm timers or write outcomes. */
internal class SessionUiDeviceChecks(private val instrumentation:Instrumentation) {
    private val ctx get()=instrumentation.targetContext
    private fun main(block:()->Unit) {
        var error:Throwable?=null
        instrumentation.runOnMainSync { try { block() } catch(t:Throwable) { error=t } }
        error?.let { throw it }
    }
    fun run() { projectionAndLargeType();sceneCancellation() }
    private fun projectionAndLargeType()=main {
        for(language in listOf("zh","en","de","es","fr","ja","ko","ru")) {
            for(dark in listOf(false,true)) {
                val config=Configuration(ctx.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language));fontScale=1.5f;screenWidthDp=320
                    uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if(dark)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val localized=ctx.createConfigurationContext(config)
                val unknown=SessionConsoleUi.summary(localized,SessionSnapshot(assistant=AssistantPhase.RUNNING))
                check(unknown.contains(localized.getString(R.string.v50_play_unknown)))
                check(!unknown.contains(localized.getString(R.string.v50_play_playing)))
                val requested=SessionConsoleUi.summary(localized,SessionSnapshot(assistant=AssistantPhase.RUNNING,
                    curtain=CurtainPhase.LOCKED,timer=TimerPhase.FIXED,remainingMs=90_000,pauseKeepBlack=true,pause=PauseReport.UNCONFIRMED))
                check(requested.contains(localized.getString(R.string.v50_pause_unconfirmed)))
                check(!requested.contains(localized.getString(R.string.v50_pause_confirmed)))
                val card=SessionConsoleUi.card(localized)
                card.addView(SessionConsoleUi.label(localized,localized.getString(R.string.v50_session),true))
                val body=SessionConsoleUi.label(localized,requested);card.addView(body)
                val width=(280*localized.resources.displayMetrics.density).toInt()
                card.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                card.layout(0,0,width,card.measuredHeight)
                check(card.measuredHeight>0 && body.lineCount>1)
                for(line in 0 until body.lineCount)check(body.layout.getEllipsisCount(line)==0)
                val bitmap=Bitmap.createBitmap(width,card.measuredHeight,Bitmap.Config.ARGB_8888)
                card.draw(Canvas(bitmap));bitmap.recycle()
            }
        }
        for(report in PauseReport.values()) {
            val text=SessionConsoleUi.pause(ctx,report)
            check((report==PauseReport.NONE)==text.isEmpty())
        }
    }
    private fun sceneCancellation() {
        val oldScene=ctx.prefs().getString("listening_scene_v1",null)
        val oldPause=ctx.prefs().getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false)
        val activity=instrumentation.startActivitySync(Intent(ctx,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            var dialog:AlertDialog?=null
            main {
                for(scene in ListeningScenes.ids)check(activity.pageHome.findViewWithTag<View>("v50_scene_$scene")!=null)
                val before=activity.pageHome.findViewWithTag<View>("v50_console")
                activity.bindSessionConsole()
                check(before===activity.pageHome.findViewWithTag<View>("v50_console"))
                dialog=activity.showTimerOptions(ListeningPreset(30,true),confirmedScene="sleep")
                dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            main {
                check(dialog?.isShowing==false)
                check(ctx.prefs().getString("listening_scene_v1",null)==oldScene)
                check(ctx.prefs().getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false)==oldPause)
                val episode=activity.showTimerOptions(initialEpisode=true,confirmedScene="episode")
                fun firstSpinner(view:View):Spinner? {
                    if(view is Spinner)return view
                    if(view is android.view.ViewGroup)for(n in 0 until view.childCount) {
                        firstSpinner(view.getChildAt(n))?.let { return it }
                    }
                    return null
                }
                check(firstSpinner(episode.window!!.decorView)?.selectedItemPosition==4)
                episode.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                check(ctx.prefs().getString("listening_scene_v1",null)==oldScene)
            }
        } finally { main { activity.finish() } }
    }
}
