package com.lujinyu.xiting

import android.app.AlertDialog
import android.widget.TextView

internal fun MainActivity.showTimerOptions() {
    val items=arrayOf(getString(R.string.timer_item_episode),getString(R.string.timer_15),
        getString(R.string.timer_30),getString(R.string.timer_60),getString(R.string.timer_custom),
        getString(R.string.timer_cancel))
    val mode=if(prefs().getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false))R.string.timer_pause_keep_black
        else R.string.timer_return_video
    AlertDialog.Builder(this).setTitle(getString(R.string.timer_title)+" · "+getString(mode))
        .setItems(items) { _,which ->
            when(which) {
                0 -> finishThisEpisode()
                4 -> showCustomTimerDialog()
                5 -> { startService(setTimerIntent(0));postRefresh() }
                else -> { startService(setTimerIntent(longArrayOf(0,15,30,60)[which]));postRefresh() }
            }
        }
        .setNeutralButton(R.string.timer_end_action) { _,_ -> showTimerEndAction() }
        .setNegativeButton(R.string.dlg_cancel,null).show()
}

private fun MainActivity.showTimerEndAction() {
    var selected=if(prefs().getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false))1 else 0
    val pad=(20*resources.displayMetrics.density).toInt()
    AlertDialog.Builder(this).setTitle(R.string.timer_end_action)
        .setSingleChoiceItems(arrayOf(getString(R.string.timer_return_video),getString(R.string.timer_pause_keep_black)),selected) { _,which -> selected=which }
        .setView(TextView(this).apply {
            text=getString(R.string.timer_pause_explain);textSize=13f
            setTextColor(getColor(R.color.text_secondary));setPadding(pad,pad/2,pad,pad/2)
        })
        .setPositiveButton(R.string.dlg_ok) { _,_ ->
            prefs().edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,selected==1).apply()
            showTimerOptions()
        }.setNegativeButton(R.string.dlg_cancel,null).show()
}
