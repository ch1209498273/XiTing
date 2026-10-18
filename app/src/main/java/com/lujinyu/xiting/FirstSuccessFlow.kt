package com.lujinyu.xiting

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings

/** Success is reported only from two explicit user answers; no timer infers it. */
internal object FirstSuccessFlow {
    const val COMPLETE="first_success_confirmed_v1"
    fun show(activity:Activity):AlertDialog=GuideExperience.show(activity)

    fun confirm(activity: Activity): AlertDialog = AlertDialog.Builder(activity)
        .setTitle(R.string.support_first_title).setMessage(R.string.support_sound_question)
        .setPositiveButton(R.string.support_yes) { _, _ -> confirmReturn(activity) }
        .setNegativeButton(R.string.support_no) { _, _ -> GuideExperience.soundHelp(activity) }
        .setNeutralButton(R.string.dlg_cancel,null).show().also { AccessibilityUi.apply(it.window!!.decorView) }

    private fun confirmReturn(activity: Activity) {
        AlertDialog.Builder(activity).setTitle(R.string.support_first_title).setMessage(R.string.support_return_question)
            .setPositiveButton(R.string.support_yes) { _, _ ->
                if (firstSuccessConfirmed(true,true)) {
                    activity.prefs().edit().putBoolean(COMPLETE,true).apply()
                    if(activity is MainActivity)activity.postRefresh()
                    AlertDialog.Builder(activity)
                    .setTitle(R.string.support_first_title).setMessage(R.string.support_first_success)
                    .setPositiveButton(R.string.dlg_got_it,null).show()
                }
            }
            .setNegativeButton(R.string.support_no) { _, _ -> GuideExperience.returnHelp(activity) }
            .setNeutralButton(R.string.dlg_cancel,null).show().also { AccessibilityUi.apply(it.window!!.decorView) }
    }
}
