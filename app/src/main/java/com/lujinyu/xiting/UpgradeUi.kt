package com.lujinyu.xiting

import android.view.View
import android.widget.ImageView
import android.widget.TextView

/** V4 navigation and primary actions; media details are a small optional row. */
internal fun MainActivity.bindUpgradeUi() {
    pageHome.findViewById<View>(R.id.playback_source_card).setOnClickListener(null)
    bindReliabilityUi()
}
internal fun MainActivity.refreshUpgradeUi() {
    OverlayService.instance?.refreshSessionSnapshot()
    pageSettings.findViewById<TextView>(R.id.backup_last_success)?.text=
        BackupManager.lastSuccessfulBackupAt(this)?.let {
            getString(R.string.ui_last_backup_fmt,java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))
        } ?: getString(R.string.ui_no_backup)
    val np=try { queryNowPlaying() } catch(_:RuntimeException) { null }
    val status=playbackLookup().availability
    val row=pageHome.findViewById<View>(R.id.playback_source_card)
    row.visibility=View.GONE
    val selectedPackage=PlaybackSelection.packageName
    pageHome.findViewById<TextView>(R.id.playback_source_title).text=selectedPackage?.let { playerLabel(it) } ?: np?.appLabel ?: getString(R.string.player_choice_none)
    pageHome.findViewById<TextView>(R.id.playback_capabilities).apply {
        text=if(np!=null)PlaybackApp(np.pkg,np.appLabel.orEmpty(),np.controller).description(this@refreshUpgradeUi)
            else if(PlaybackSelection.hasSelection)getString(status.messageResource()) else getString(R.string.player_choice_intro)
        visibility=View.VISIBLE
    }
    row.findViewWithTag<ImageView>("player_source_icon")?.setImageDrawable((selectedPackage ?: np?.pkg)?.let { playerIcon(it) })
    AccessibilityUi.apply(window.decorView)
}
internal fun MainActivity.scrollToGrowth() {
    val panel=pageStats.findViewById<View>(R.id.growth_panel)
    (pageStats as? android.widget.ScrollView)?.smoothScrollTo(0,panel.top)
}
