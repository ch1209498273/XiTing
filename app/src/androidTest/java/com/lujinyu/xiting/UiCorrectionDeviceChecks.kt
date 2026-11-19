package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView

internal class UiCorrectionDeviceChecks(private val i:Instrumentation) {
    fun run() {
        val a=i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            i.waitForIdleSync();var error:Throwable?=null
            i.runOnMainSync { try {
                val home=(a.pageHome as ScrollView).getChildAt(0) as ViewGroup
                check(home.getChildAt(0).id==R.id.card_black) { "Primary start button must be the first home item" }
                check(a.pageHome.findViewWithTag<View>("v50_scenes")==null) { "Scenario stack must not displace the main action" }
                check(a.pageHome.findViewWithTag<View>("v50_console")==null) { "Technical console must not appear on home" }
                val stats=(a.pageStats as ScrollView).getChildAt(0) as ViewGroup
                check((stats.getChildAt(0) as ViewGroup).findViewById<View>(R.id.pet_view)!=null) { "Restore the v4 growth-first stats layout" }
                check(a.pageStats.findViewWithTag<View>("v50_recap")==null)
                check(a.pageSettings.findViewWithTag<View>("setting_theme")!=null) { "Theme must have a direct setting" }
                check(a.pageSettings.findViewWithTag<View>("setting_backups")!=null) { "Backup management belongs in backup settings" }
            } catch(t:Throwable){error=t} }
            error?.let { throw it }
        } finally { i.runOnMainSync { a.finish() } }
        checkHomeEntryOrder()
    }

    private fun checkHomeEntryOrder() {
        val ctx = i.targetContext
        val prefs = ctx.prefs()
        val complete = FirstSuccessFlow.COMPLETE
        val previous = prefs.getBoolean(complete, false).takeIf { prefs.contains(complete) }
        val previousTab = prefs.getInt(Prefs.LAST_TAB, 0).takeIf { prefs.contains(Prefs.LAST_TAB) }
        try {
            for (guideCompleted in listOf(true, false)) {
                prefs.edit().putBoolean(complete, guideCompleted).commit()
                val activity = i.startActivitySync(Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                var error: Throwable? = null
                i.runOnMainSync {
                    try {
                        activity.findViewById<View>(R.id.nav_settings).performClick()
                        val dialog = activity.showHomeEntryOrder()
                        try {
                            val adapter = dialog.listView.adapter
                            val labels = (0 until adapter.count).map { adapter.getItem(it).toString() }
                            val demo = activity.getString(R.string.extras_entry_demo)
                            check(activity.getString(R.string.extras_entry_timer) in labels)
                            check(activity.getString(R.string.extras_entry_permissions) in labels)
                            check((demo in labels) == !guideCompleted) {
                                "Entry order lists the demo only before the first-use guide is completed"
                            }
                        } finally {
                            dialog.dismiss()
                        }
                    } catch (failure: Throwable) {
                        error = failure
                    } finally {
                        activity.finish()
                    }
                }
                error?.let { throw it }
                i.waitForIdleSync()
            }
        } finally {
            val editor = prefs.edit()
            if (previous == null) editor.remove(complete) else editor.putBoolean(complete, previous)
            if (previousTab == null) editor.remove(Prefs.LAST_TAB) else editor.putInt(Prefs.LAST_TAB, previousTab)
            editor.commit()
        }
    }
}
