package com.lujinyu.xiting

import android.app.AlertDialog
import android.media.session.PlaybackState
import android.view.View
import android.widget.*

/** One panel to review duration, action and original target together. */
@android.annotation.SuppressLint("SetTextI18n") // ASCII digits are required by the numeric input parser.
internal fun MainActivity.showTimerOptions(initialPreset:ListeningPreset?=null, initialEpisode:Boolean=false, confirmedScene:String?=null):AlertDialog {
    val remembered=initialPreset ?: (PlaybackSelection.packageName ?: queryNowPlaying()?.pkg)?.let { PlayerTimerDefaults.load(this,it) }
    val pad=(20*resources.displayMetrics.density).toInt()
    val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(pad,pad/2,pad,pad/2) }
    fun label(value:String)=TextView(this).apply {
        text=value;textSize=14f;setTextColor(getColor(R.color.text_secondary));setPadding(0,pad/2,0,pad/4)
    }.also { body.addView(it) }
    val presets=Spinner(this).apply {
        adapter=ArrayAdapter(this@showTimerOptions,android.R.layout.simple_spinner_dropdown_item,
            arrayOf(getString(R.string.timer_15),getString(R.string.timer_30),getString(R.string.timer_60),
                getString(R.string.timer_custom),getString(R.string.timer_item_episode)))
        val preferred=remembered?.minutes?.let { listOf(15,30,60).indexOf(it).takeIf { index->index>=0 } ?: 3 } ?: 1
        setSelection(if(initialEpisode)4 else preferred)
    }
    body.addView(presets)
    val input=EditText(this).apply {
        id=R.id.timer_minutes_input;inputType=android.text.InputType.TYPE_CLASS_NUMBER
        hint=getString(R.string.timer_custom_hint);setText((remembered?.minutes ?: 30).toString());contentDescription=getString(R.string.ui_timer_minutes)
    }
    body.addView(input)
    var refreshSummary:()->Unit={}
    presets.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent:AdapterView<*>?) {}
        override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
            input.isEnabled=position!=4
            if(position<3)input.setText(intArrayOf(15,30,60)[position].toString())
            refreshSummary()
        }
    }
    label(getString(R.string.timer_end_action))
    val modes=RadioGroup(this)
    val returnVideo=RadioButton(this).apply { id=View.generateViewId();text=getString(R.string.timer_return_video);minHeight=(48*resources.displayMetrics.density).toInt() }
    val keepBlack=RadioButton(this).apply { id=View.generateViewId();text=getString(R.string.timer_pause_keep_black);minHeight=(48*resources.displayMetrics.density).toInt() }
    modes.addView(returnVideo);modes.addView(keepBlack)
    modes.check(if(remembered?.pauseKeepBlack ?: prefs().getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false))keepBlack.id else returnVideo.id)
    body.addView(modes)
    val playerHeading=label(getString(R.string.auto_playback_source))
    val nowPlaying=queryNowPlaying()
    val sources=nowPlaying?.let { listOf(PlaybackApp(it.pkg,it.appLabel.orEmpty(),it.controller)) }.orEmpty()
    fun signature(source:android.media.session.MediaController):String {
        val metadata=source.metadata
        return listOf(metadata?.getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID),
            metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
            metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)).joinToString("\n")
    }
    val reviewed=sources.mapNotNull { it.controller }.associate { it.sessionToken to signature(it) }.toMutableMap()
    val targets=Spinner(this).apply {
        adapter=if(sources.isEmpty())ArrayAdapter(this@showTimerOptions,android.R.layout.simple_spinner_dropdown_item,listOf(getString(R.string.ui_no_session)))
            else PlayerRows.appsAdapter(this@showTimerOptions,sources)
        val selected=sources.indexOfFirst { it.packageName==PlaybackSelection.packageName || (it.controller!=null && it.controller.sessionToken==PlaybackSelection.token) }
        if(selected>=0)setSelection(selected)
    }
    targets.visibility=View.GONE
    label(if(nowPlaying!=null && !nowPlaying.controlsReliable)getString(R.string.playback_source_control_in_app,nowPlaying.appLabel.orEmpty())
        else sources.firstOrNull()?.label ?: getString(R.string.auto_playback_unknown))
    val capability=label("")
    fun updateCapability() {
        val source=sources.getOrNull(targets.selectedItemPosition)
        val supported=hasMediaAccess() && source?.canPause==true
        keepBlack.isEnabled=supported
        if(!supported && modes.checkedRadioButtonId==keepBlack.id)modes.check(returnVideo.id)
        capability.text=source?.description(this) ?: getString(R.string.auto_playback_unavailable)
    }
    updateCapability()
    if(initialEpisode)label(getString(R.string.timer_episode_changed))
    val summary=label("")
    fun updateSummary() {
        val custom=presets.selectedItemPosition==3
        input.visibility=if(custom)View.VISIBLE else View.GONE
        playerHeading.visibility=View.VISIBLE
        targets.visibility=View.GONE
        capability.visibility=View.VISIBLE
        body.findViewWithTag<View>("timer_saved_pick")?.visibility=if(custom)View.VISIBLE else View.GONE
        body.findViewWithTag<View>("timer_saved_save")?.visibility=if(custom)View.VISIBLE else View.GONE
        val duration=if(presets.selectedItemPosition==4)getString(R.string.timer_item_episode)
            else "${input.text} · ${getString(R.string.ui_timer_minutes)}"
        val mode=if(modes.checkedRadioButtonId==keepBlack.id)keepBlack.text else returnVideo.text
        summary.text=getString(R.string.ui_timer_summary_fmt,duration,mode,targets.selectedItem ?: getString(R.string.ui_no_session))
    }
    refreshSummary=::updateSummary
    input.addTextChangedListener(object:android.text.TextWatcher {
        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=updateSummary()
        override fun afterTextChanged(s:android.text.Editable?) {}
    })
    modes.setOnCheckedChangeListener { _,_ -> updateSummary() }
    targets.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent:AdapterView<*>?) {}
        override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { updateCapability();updateSummary() }
    }
    updateSummary()
    summary.visibility=View.GONE
    val feedback=label("")
    body.addView(Button(this).apply {
        tag="timer_saved_pick";visibility=View.GONE
        text=getString(R.string.extras_use_preset)
        setOnClickListener {
            val saved=ListeningPresets.load(this@showTimerOptions)
            val picker=AlertDialog.Builder(this@showTimerOptions).setTitle(R.string.extras_presets)
                .setNegativeButton(R.string.dlg_cancel,null)
                .setNeutralButton(R.string.extras_manage_presets) { _,_ -> showManageListeningPresets() }
            if(saved.isEmpty())picker.setMessage(R.string.extras_no_presets)
            else picker.setItems(saved.map { ListeningPresets.label(this@showTimerOptions,it) }.toTypedArray()) { _, index ->
                val selected=saved[index]
                presets.setSelection(3);input.isEnabled=true;input.setText(selected.minutes.toString())
                modes.check(if(selected.pauseKeepBlack)keepBlack.id else returnVideo.id)
                feedback.text=getString(R.string.extras_preset_review);updateSummary()
            }
            picker.show()
        }
    })
    body.addView(Button(this).apply {
        tag="timer_saved_save";visibility=View.GONE
        text=getString(R.string.extras_save_preset)
        setOnClickListener {
            val minutes=input.text.toString().trim().toIntOrNull()
            if(presets.selectedItemPosition==4 || minutes==null || minutes !in 1..600) {
                feedback.text=getString(R.string.timer_custom_hint);return@setOnClickListener
            }
            val saved=ListeningPresets.save(this@showTimerOptions,ListeningPreset(minutes,modes.checkedRadioButtonId==keepBlack.id))
            feedback.text=getString(if(saved)R.string.extras_preset_saved else R.string.extras_preset_duplicate)
        }
    })
    val dialog=AlertDialog.Builder(this).setTitle(R.string.timer_title)
        .setView(ScrollView(this).apply { addView(body) })
        .setPositiveButton(R.string.dlg_ok,null).setNegativeButton(R.string.dlg_cancel,null)
        .setNeutralButton(R.string.timer_cancel) { _,_ -> OverlayService.instance?.cancelTimer();postRefresh() }.create()
    var cancelPending:(()->Unit)?=null
    dialog.setOnDismissListener { cancelPending?.invoke();cancelPending=null }
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!android.provider.Settings.canDrawOverlays(this)) {
                feedback.text=getString(R.string.v50_overlay_needed);return@setOnClickListener
            }
            val episode=presets.selectedItemPosition==4
            val minutes=input.text.toString().trim().toLongOrNull()
            if(!episode && (minutes==null || minutes !in 1L..600L)) {
                input.error=getString(R.string.timer_custom_hint);return@setOnClickListener
            }
            val pause=modes.checkedRadioButtonId==keepBlack.id
            // A preset never restores a session. Re-query the selected live session at confirmation.
            val app=sources.getOrNull(targets.selectedItemPosition)
            val selectedToken=app?.controller?.sessionToken
            val source=playbackSources().firstOrNull { it.sessionToken==selectedToken }
            if((pause || episode) && queryNowPlaying()?.controller?.sessionToken!=selectedToken) {
                feedback.text=getString(R.string.auto_playback_changed);return@setOnClickListener
            }
            if((pause || episode) && (source==null ||
                (pause && ((source.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PAUSE)==0L))) {
                feedback.text=getString(R.string.timer_no_target)
                feedback.setTextColor(getColor(R.color.warn_red));return@setOnClickListener
            }
            if((pause || episode) && source!=null && reviewed[source.sessionToken]!=signature(source)) {
                reviewed[source.sessionToken]=signature(source)
                // Keep entered values and show the new target before accepting another confirmation.
                (targets.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
                updateSummary();feedback.text=getString(R.string.v50_target_changed)
                return@setOnClickListener
            }
            var episodeEndAt=0L
            if(episode) {
                val episodeTarget=EpisodeTimerTarget(source!!)
                val reading=try { episodeTarget.read() } finally { episodeTarget.close() }
                if(!reading.valid || reading.remainingMs<=0) {
                    feedback.text=getString(R.string.toast_no_progress);return@setOnClickListener
                }
                episodeEndAt=System.currentTimeMillis()+reading.remainingMs+4_000
            }
            fun pending(value:Boolean) {
                dialog.setCancelable(!value)
                for(button in listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL))dialog.getButton(button).isEnabled=!value
                presets.isEnabled=!value;targets.isEnabled=!value;input.isEnabled=!value && !episode
                returnVideo.isEnabled=!value
                if(value)keepBlack.isEnabled=false else updateCapability()
            }
            feedback.text=getString(R.string.flow_timer_pending);feedback.setTextColor(getColor(R.color.text_secondary));pending(true)
            val currentMatches=source!=null && queryNowPlaying()?.controller?.sessionToken==source.sessionToken
            val requestToken=source?.sessionToken?.takeIf { pause || episode || currentMatches }
            cancelPending=TimerRequests.submit(this,TimerRequest(minutes ?: 30,episodeEndAt,requestToken,
                episode,pause,source?.takeIf { pause || episode }?.let { reviewed[it.sessionToken] },app?.packageName?.takeIf { currentMatches })) { result ->
                if(!dialog.isShowing || isFinishing || isDestroyed)return@submit
                pending(false)
                if(result.accepted) {
                    confirmedScene?.let { SessionRuntime.selectScene(this,if(episode)"episode" else "sleep") }
                    postRefresh();dialog.dismiss()
                } else {
                    feedback.text=getString(result.errorResource.takeIf { it!=0 } ?: R.string.flow_timer_failed)
                    feedback.setTextColor(getColor(R.color.warn_red))
                }
            }
        }
    }
    dialog.show()
    return dialog
}
