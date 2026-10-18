package com.lujinyu.xiting

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ImageView

/** Embedded in the existing black window. No Activity switch, popup window or keyboard required. */
internal class CurtainTimerPanel(ctx:Context,private val onClose:()->Unit,private val onAccepted:()->Unit):LinearLayout(ctx) {
    private var target=ctx.queryNowPlaying()?.controller
    private var targetPackage=target?.packageName
    private var reviewed=target?.let { TimerRequests.signature(it) }
    private val remembered=targetPackage?.let { PlayerTimerDefaults.load(ctx,it) }
    private var minutes=remembered?.minutes ?: 30
    private var episode=false
    private var pending=false
    private var cancelRequest:(()->Unit)?=null
    private val player=label("")
    private val choices=LinearLayout(ctx).apply { orientation=VERTICAL;visibility=GONE }
    private val duration=label("")
    private val custom=LinearLayout(ctx).apply { orientation=VERTICAL;visibility=GONE }
    private val modes=RadioGroup(ctx)
    private val returnVideo=RadioButton(ctx).apply { id=View.generateViewId();text=ctx.getString(R.string.flow_timer_return);setTextColor(Color.WHITE);minHeight=dp(48);tag="flow_action_return" }
    private val pause=RadioButton(ctx).apply { id=View.generateViewId();text=ctx.getString(R.string.flow_timer_pause);setTextColor(Color.WHITE);minHeight=dp(48);tag="flow_action_pause" }
    private val feedback=label("")
    private val confirm=button(ctx.getString(R.string.dlg_ok),"flow_timer_confirm") { submit() }
    init {
        tag="flow_timer_editor";orientation=VERTICAL
        setPadding(dp(12),dp(8),dp(12),dp(8))
        addView(label(ctx.getString(R.string.flow_timer_setup)).apply { textSize=18f;setTypeface(typeface,Typeface.BOLD) })
        player.tag="flow_timer_player";player.minHeight=dp(48)
        player.isClickable=false
        val identity=LinearLayout(ctx).apply { gravity=Gravity.CENTER_VERTICAL;isBaselineAligned=false }
        identity.addView(ImageView(ctx).apply { target?.let { setImageDrawable(ctx.playerIcon(it.packageName)) };tag="flow_player_icon" },LayoutParams(dp(28),dp(28)).apply { marginEnd=dp(10) })
        identity.addView(player,LayoutParams(0,-2,1f))
        addView(identity);addView(choices)
        val row=LinearLayout(ctx).apply { isBaselineAligned=false;gravity=Gravity.CENTER_VERTICAL }
        for(value in listOf(15,30,60))row.addView(button(ctx.getString(R.string.flow_minutes,value),"flow_preset_$value") {
            if(!pending) { minutes=value;episode=false;custom.visibility=GONE;refresh() }
        },LayoutParams(0,-2,1f).apply { marginEnd=dp(4) })
        row.addView(button(ctx.getString(R.string.flow_custom),"flow_preset_custom") {
            if(!pending) { episode=false;custom.visibility=VISIBLE;refresh() }
        },LayoutParams(0,-2,1f))
        addView(row);duration.tag="flow_timer_duration";addView(duration)
        custom.addView(SeekBar(ctx).apply {
            max=599;progress=minutes-1;tag="flow_custom_minutes"
            progressTintList=ColorStateList.valueOf(0xff36b781.toInt());thumbTintList=progressTintList
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar:SeekBar) { }
                override fun onStopTrackingTouch(bar:SeekBar) { }
                override fun onProgressChanged(bar:SeekBar,value:Int,fromUser:Boolean) { if(fromUser && !pending) { minutes=value+1;refresh() } }
            })
        })
        val steps=LinearLayout(ctx).apply { isBaselineAligned=false }
        for(delta in listOf(-5,-1,1,5))steps.addView(button(if(delta>0)"+$delta" else delta.toString(),"flow_step_$delta") {
            if(!pending) { minutes=(minutes+delta).coerceIn(1,600);custom.findViewWithTag<SeekBar>("flow_custom_minutes").progress=minutes-1;refresh() }
        },LayoutParams(0,-2,1f).apply { marginEnd=dp(4) })
        custom.addView(steps);addView(custom)
        addView(button(ctx.getString(R.string.timer_item_episode),"flow_preset_episode") {
            if(!pending) { episode=true;custom.visibility=GONE;refresh() }
        })
        addView(label(ctx.getString(R.string.flow_end_action)))
        modes.addView(returnVideo);modes.addView(pause);addView(modes)
        returnVideo.buttonTintList=ColorStateList.valueOf(0xff36b781.toInt());pause.buttonTintList=returnVideo.buttonTintList
        modes.check(if(remembered?.pauseKeepBlack ?: ctx.prefs().getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false))pause.id else returnVideo.id)
        modes.setOnCheckedChangeListener { _,_ -> if(!pending)feedback.text="" }
        feedback.tag="flow_timer_feedback";addView(feedback)
        val buttons=LinearLayout(ctx).apply { isBaselineAligned=false }
        buttons.addView(button(ctx.getString(R.string.dlg_cancel),"flow_timer_close") { if(!pending)onClose() },LayoutParams(0,-2,1f).apply { marginEnd=dp(8) })
        confirm.background=background(0xff168859.toInt());buttons.addView(confirm,LayoutParams(0,-2,1f));addView(buttons)
        if(minutes !in listOf(15,30,60))custom.visibility=VISIBLE
        refresh()
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun label(value:String)=TextView(context).apply {
        text=value;textSize=14f;setTextColor(Color.LTGRAY);setPadding(0,dp(6),0,dp(6))
        layoutParams=LayoutParams(-1,-2)
    }
    private fun background(color:Int)=GradientDrawable().apply { setColor(color);cornerRadius=dp(10).toFloat() }
    private fun button(value:String,identity:String,action:()->Unit)=TextView(context).apply {
        text=value;tag=identity;textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
        minHeight=dp(48);setPadding(dp(8),dp(6),dp(8),dp(6));background=background(0xff262626.toInt())
        setOnClickListener { action() };layoutParams=LayoutParams(-1,-2).apply { topMargin=dp(4);bottomMargin=dp(4) }
    }
    private fun refresh() {
        val current=context.queryNowPlaying()
        player.text=if(current!=null && current.controller.sessionToken==target?.sessionToken && !current.controlsReliable)
            context.getString(R.string.playback_source_control_in_app,current.appLabel.orEmpty())
            else targetPackage?.let { context.playerLabel(it) } ?: context.getString(R.string.auto_playback_unavailable)
        findViewWithTag<ImageView>("flow_player_icon")?.setImageDrawable(targetPackage?.let { context.playerIcon(it) })
        duration.text=if(episode)context.getString(R.string.timer_item_episode) else context.getString(R.string.flow_minutes,minutes)
        for(value in listOf(15,30,60))findViewWithTag<View>("flow_preset_$value")?.background=background(
            if(!episode && custom.visibility==GONE && minutes==value)0xff11754d.toInt() else 0xff262626.toInt())
        findViewWithTag<View>("flow_preset_custom")?.background=background(if(!episode && custom.visibility==VISIBLE)0xff11754d.toInt() else 0xff262626.toInt())
        findViewWithTag<View>("flow_preset_episode")?.background=background(if(episode)0xff11754d.toInt() else 0xff262626.toInt())
        val readableTarget=if(context.hasMediaAccess())target else null
        val timeline=readableTarget?.let {
            val probe=EpisodeTimerTarget(it)
            try { probe.read() } finally { probe.close() }
        }
        findViewWithTag<TextView>("flow_preset_episode")?.apply {
            isEnabled=timeline?.valid==true && timeline.remainingMs>0
            text=context.getString(if(isEnabled)R.string.timer_item_episode else R.string.flow_episode_unavailable)
            alpha=if(isEnabled)1f else .5f
        }
        pause.isEnabled=try { readableTarget?.let { TimerPlaybackTarget(it).canArm() }==true } catch(_:RuntimeException) { false }
        if(modes.checkedRadioButtonId==pause.id && !pause.isEnabled)modes.check(returnVideo.id)
        if(targetPackage!=null && target==null)feedback.text=context.getString(R.string.player_selected_no_session)

    }
    private fun submit() {
        if(pending)return
        val needsPlayer=modes.checkedRadioButtonId==pause.id || episode
        val live=target?.sessionToken?.let { token -> context.playbackSources().firstOrNull { it.sessionToken==token } }
        if(needsPlayer && context.queryNowPlaying()?.controller?.sessionToken!=target?.sessionToken) {
            feedback.text=context.getString(R.string.auto_playback_changed);return
        }
        if(needsPlayer && live==null) { feedback.text=context.getString(R.string.auto_playback_unavailable);return }
        if(needsPlayer && live!=null && reviewed!=TimerRequests.signature(live)) {
            reviewed=TimerRequests.signature(live);target=live;refresh();feedback.text=context.getString(R.string.v50_target_changed);return
        }
        var endAt=0L
        if(episode) {
            val probe=EpisodeTimerTarget(live!!)
            val reading=try { probe.read() } finally { probe.close() }
            if(!reading.valid || reading.remainingMs<=0) { feedback.text=context.getString(R.string.flow_episode_unavailable);return }
            endAt=System.currentTimeMillis()+reading.remainingMs.coerceAtMost(SleepTimerPolicy.MAX_DURATION_MS-4_000)+4_000
        }
        pending=true;confirm.isEnabled=false;confirm.alpha=.5f;feedback.text=context.getString(R.string.flow_timer_pending)
        val currentMatches=live!=null && context.queryNowPlaying()?.controller?.sessionToken==live.sessionToken
        cancelRequest=TimerRequests.submit(context,TimerRequest(minutes.toLong(),endAt,live?.sessionToken?.takeIf { needsPlayer || currentMatches },
            episode,modes.checkedRadioButtonId==pause.id,if(needsPlayer)reviewed else null,targetPackage?.takeIf { currentMatches })) { result ->
            if(!isAttachedToWindow)return@submit
            pending=false;confirm.isEnabled=true;confirm.alpha=1f
            if(result.accepted)onAccepted() else feedback.text=context.getString(result.errorResource.takeIf { it!=0 } ?: R.string.flow_timer_failed)
        }
    }
    fun dispose() { cancelRequest?.invoke();cancelRequest=null }
    override fun onDetachedFromWindow() { dispose();super.onDetachedFromWindow() }
}

internal fun PlaybackAvailability.messageResource():Int=when(this) {
    PlaybackAvailability.NO_ACCESS -> R.string.flow_player_permission
    PlaybackAvailability.SELECTED_UNAVAILABLE -> R.string.flow_player_lost
    PlaybackAvailability.SELECTED_NO_SESSION -> R.string.player_selected_no_session
    PlaybackAvailability.ERROR -> R.string.flow_player_error
    else -> R.string.flow_player_empty
}
