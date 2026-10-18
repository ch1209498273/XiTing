package com.lujinyu.xiting

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.view.View
import android.widget.Button

/** Checks app semantics and confirmation routing; this is not a real TalkBack session. */
internal object SupportDeviceChecks {
    fun run(instrumentation: Instrumentation, activity: Activity) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                AccessibilityUi.apply(activity.window.decorView)
                val nav = activity.findViewById<View>(R.id.nav_home)
                nav.performClick()
                val node = nav.createAccessibilityNodeInfo()
                check(node.className == Button::class.java.name && node.isSelected)
                check(!node.contentDescription.isNullOrBlank())
                check(nav.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt())
                val diagnostic = SupportDiagnostics.show(activity)
                check(diagnostic.getButton(AlertDialog.BUTTON_POSITIVE).text.isNotBlank())
                diagnostic.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
                // Refresh opens a new dialog, so dismiss that window via back after idle below.
            } catch (t: Throwable) { failure = t }
        }
        failure?.let { throw it }
        instrumentation.waitForIdleSync()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            try {
                val confirmation = FirstSuccessFlow.confirm(activity)
                check(confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).text.isNotBlank())
                confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            } catch (t: Throwable) { failure = t }
        }
        failure?.let { throw it }
        instrumentation.waitForIdleSync()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    }
}
