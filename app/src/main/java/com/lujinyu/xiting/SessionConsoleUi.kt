package com.lujinyu.xiting

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Every surface translates the same independent state dimensions. No inferred playback success. */
internal object SessionConsoleUi {
    private const val CONSOLE = "v50_console"
    private const val SUMMARY = "v50_summary"
    private const val RECAP = "v50_recap"
    fun summary(ctx:Context,state:SessionSnapshot):String {
        val timer=when {
            state.timer==TimerPhase.WAITING -> ctx.getString(R.string.timer_episode_waiting)
            state.remainingMs>0 -> ctx.getString(R.string.notif_timer_fmt,(state.remainingMs+59999)/60000)
            else -> ""
        }
        val main=if(state.assistant==AssistantPhase.RUNNING)ctx.getString(R.string.notif_text,timer)
            else ctx.getString(R.string.widget_idle)
        return listOf(main,pause(ctx,state.pause)).filter { it.isNotBlank() }.joinToString(" · ")
    }
    fun pause(ctx: Context, report: PauseReport): String = if(report == PauseReport.NONE) "" else ctx.getString(when(report) {
        PauseReport.REQUESTED -> R.string.v50_pause_requested
        PauseReport.CONFIRMED -> R.string.v50_pause_confirmed
        PauseReport.ALREADY_PAUSED -> R.string.v50_pause_already
        PauseReport.UNAVAILABLE -> R.string.v50_pause_unavailable
        PauseReport.FAILED -> R.string.v50_pause_failed
        PauseReport.UNCONFIRMED -> R.string.v50_pause_unconfirmed
        PauseReport.NONE -> R.string.v50_play_unknown
    })
    internal fun card(ctx: Context) = LinearLayout(ctx).apply {
        orientation=LinearLayout.VERTICAL
        val pad=(16*resources.displayMetrics.density).toInt();setPadding(pad,pad,pad,pad)
        setBackgroundResource(R.drawable.bg_card)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=pad/2;bottomMargin=pad/2 }
    }
    internal fun label(ctx: Context, value: String, title: Boolean=false) = TextView(ctx).apply {
        text=value;textSize=if(title)18f else 14f;setTextColor(ctx.getColor(R.color.text_primary))
        if(title)setTypeface(typeface,android.graphics.Typeface.BOLD)
        setPadding(0,4,0,8)
    }
    private fun outcomeText(ctx:Context,item:SessionOutcome):String {
        val date=java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.startedAt))
        val reason=ctx.getString(when(item.reason) {
            "user" -> R.string.v50_end_user
            "timer" -> R.string.v50_end_timer
            "interrupted" -> R.string.v50_interrupted
            else -> R.string.v50_end_unknown
        })
        val pause=try { pause(ctx,PauseReport.valueOf(item.pause.uppercase(java.util.Locale.ROOT))) } catch(_:IllegalArgumentException) { "" }
        return listOf(date,ctx.getString(ListeningScenes.label(item.scene)),reason,pause).filter { it.isNotBlank() }.joinToString("\n")
    }
    internal fun records(activity:MainActivity) = with(activity) {
        val items=SessionOutcomeStore.list(this)
        val builder=AlertDialog.Builder(this).setTitle(R.string.v50_records).setNegativeButton(R.string.dlg_cancel,null)
        if(items.isEmpty())builder.setMessage(R.string.v50_no_records)
        else builder.setItems(items.map { outcomeText(this,it) }.toTypedArray()) { _,index ->
            AlertDialog.Builder(this).setTitle(R.string.v50_session).setMessage(outcomeText(this,items[index]))
                .setPositiveButton(R.string.dlg_ok,null).show()
        }
        builder.show()
    }
    internal fun recapSwitch(activity:MainActivity) = Switch(activity).apply {
        text=activity.getString(R.string.v50_recap_enabled);minHeight=(48*resources.displayMetrics.density).toInt()
        isChecked=activity.prefs().getBoolean("session_recap_enabled",true)
        setOnCheckedChangeListener { _,enabled -> activity.prefs().edit().putBoolean("session_recap_enabled",enabled).apply() }
    }
}
internal fun MainActivity.bindSessionConsole() = Unit
internal fun MainActivity.refreshSessionConsole() = Unit
internal fun MainActivity.showSessionRecords() = SessionConsoleUi.records(this)
