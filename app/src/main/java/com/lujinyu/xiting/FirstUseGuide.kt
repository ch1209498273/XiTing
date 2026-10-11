package com.lujinyu.xiting

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

internal fun MainActivity.showFirstUseGuide() {
    AlertDialog.Builder(this).setTitle(R.string.guide_title).setMessage(R.string.guide_intro)
        .setPositiveButton(R.string.guide_start) { _,_ ->
            if(!Settings.canDrawOverlays(this)) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
                catch(_:RuntimeException) { Toast.makeText(this,getString(R.string.guide_permission),Toast.LENGTH_LONG).show() }
            } else {
                startForegroundService(Intent(this,OverlayService::class.java).setAction(OverlayService.ACTION_START_DEMO))
                window.decorView.removeCallbacks(demoFeedbackCheck)
                window.decorView.postDelayed(demoFeedbackCheck,10_500)
            }
        }.setNegativeButton(R.string.dlg_cancel,null).show()
}

internal fun MainActivity.showDemoFeedback() {
    if(isFinishing || isDestroyed || !hasWindowFocus() || OverlayService.instance?.isAnyBlackShowing()==true ||
        !prefs().getBoolean(Prefs.DEMO_FEEDBACK_PENDING,false)) return
    prefs().edit().putBoolean(Prefs.DEMO_FEEDBACK_PENDING,false).apply()
    AlertDialog.Builder(this).setTitle(R.string.guide_title).setMessage(R.string.guide_feedback)
        .setPositiveButton(R.string.guide_yes) { _,_ ->
            Toast.makeText(this,getString(R.string.guide_success),Toast.LENGTH_LONG).show()
        }.setNegativeButton(R.string.guide_no) { _,_ ->
            AlertDialog.Builder(this).setTitle(R.string.guide_title).setMessage(R.string.guide_retry)
                .setPositiveButton(R.string.dlg_ok,null).show()
        }.setNeutralButton(R.string.dlg_cancel,null).show()
}
