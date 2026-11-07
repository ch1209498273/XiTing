package com.lujinyu.xiting

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings

/** Success is reported only from two explicit user answers; no timer infers it. */
internal object FirstSuccessFlow {
    fun show(activity: Activity): AlertDialog = AlertDialog.Builder(activity)
        .setTitle(R.string.support_first_title).setMessage(R.string.support_first_intro)
        .setPositiveButton(R.string.guide_start) { _, _ ->
            if (!Settings.canDrawOverlays(activity)) SupportDiagnostics.show(activity)
            else try {
                activity.startForegroundService(Intent(activity,OverlayService::class.java).setAction(OverlayService.ACTION_START_DEMO))
                if (activity is MainActivity) {
                    activity.window.decorView.removeCallbacks(activity.demoFeedbackCheck)
                    activity.window.decorView.postDelayed(activity.demoFeedbackCheck,10_500)
                }
            } catch (_: RuntimeException) { SupportDiagnostics.show(activity) }
        }
        .setNeutralButton(R.string.support_check_result) { _, _ -> confirm(activity) }
        .setNegativeButton(R.string.dlg_cancel,null).show().also { AccessibilityUi.apply(it.window!!.decorView) }

    fun confirm(activity: Activity): AlertDialog = AlertDialog.Builder(activity)
        .setTitle(R.string.support_first_title).setMessage(R.string.support_sound_question)
        .setPositiveButton(R.string.support_yes) { _, _ -> confirmReturn(activity) }
        .setNegativeButton(R.string.support_no) { _, _ -> SupportDiagnostics.show(activity) }
        .setNeutralButton(R.string.dlg_cancel,null).show().also { AccessibilityUi.apply(it.window!!.decorView) }

    private fun confirmReturn(activity: Activity) {
        AlertDialog.Builder(activity).setTitle(R.string.support_first_title).setMessage(R.string.support_return_question)
            .setPositiveButton(R.string.support_yes) { _, _ ->
                if (firstSuccessConfirmed(true,true)) AlertDialog.Builder(activity)
                    .setTitle(R.string.support_first_title).setMessage(R.string.support_first_success)
                    .setPositiveButton(R.string.dlg_got_it,null).show()
            }
            .setNegativeButton(R.string.support_no) { _, _ -> SupportDiagnostics.show(activity) }
            .setNeutralButton(R.string.dlg_cancel,null).show().also { AccessibilityUi.apply(it.window!!.decorView) }
    }
}
