package com.lujinyu.xiting

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.widget.EditText
import android.widget.RadioGroup
import android.view.View
import android.view.ViewGroup
import java.util.Locale

/** Framework checks with preferences restored in finally; uses no external player. */
internal class PersonalizationDeviceChecks(private val i: Instrumentation) {
    private val ctx get() = i.targetContext
    private fun main(block: () -> Unit) {
        var error: Throwable? = null
        i.runOnMainSync { try { block() } catch (t: Throwable) { error = t } }
        error?.let { throw it }
    }
    private fun modeGroup(view: View): RadioGroup? {
        if (view is RadioGroup) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            modeGroup(view.getChildAt(index))?.let { return it }
        }
        return null
    }
    fun run() {
        val p = ctx.prefs()
        val keys = listOf("appearance_mode_v1", "home_entry_order_v1", "listening_presets_v1")
        val before = keys.associateWith { p.getString(it, null) }
        val timerMode = p.getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK, false)
        val lastTab = p.getInt(Prefs.LAST_TAB, 0)
        val token = PlaybackSelection.token
        var a: MainActivity? = null
        try {
            check(!ListeningPresets.save(ctx, ListeningPreset(0, true)))
            check(!ListeningPresets.save(ctx, ListeningPreset(601, true)))
            p.edit().putString("listening_presets_v1", "").commit()
            check(ListeningPresets.save(ctx, ListeningPreset(37, true)))
            check(!ListeningPresets.save(ctx, ListeningPreset(37, true)))
            check(ListeningPresets.load(ctx) == listOf(ListeningPreset(37, true)))
            ListeningPresets.remove(ctx, ListeningPreset(37, true))
            check(ListeningPresets.load(ctx).isEmpty())
            HomeEntryOrder.promote(ctx, "demo")
            check(HomeEntryOrder.keys(ctx).first() == "demo")
            check(HomeEntryOrder.orderedIds(ctx).distinct().size == 4)
            val localizedConfig = Configuration(ctx.resources.configuration).apply {
                setLocale(Locale.JAPANESE)
                uiMode = Configuration.UI_MODE_TYPE_CAR or Configuration.UI_MODE_NIGHT_NO
            }
            val localized = ctx.createConfigurationContext(localizedConfig)
            AppearanceSettings.set(ctx, AppearanceSettings.DARK)
            val wrapped = AppearanceSettings.wrap(localized)
            check(wrapped.resources.configuration.locales[0].language == "ja")
            check(wrapped.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_CAR)
            check(wrapped.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            AppearanceSettings.set(ctx, AppearanceSettings.SYSTEM)
            check(AppearanceSettings.wrap(localized) === localized)
            for ((mode, night) in listOf(AppearanceSettings.LIGHT to Configuration.UI_MODE_NIGHT_NO,
                AppearanceSettings.DARK to Configuration.UI_MODE_NIGHT_YES)) {
                AppearanceSettings.set(ctx, mode)
                a = i.startActivitySync(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                val current = a
                var opened:AlertDialog?=null
                i.waitForIdleSync()
                main {
                    check(current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == night)
                    val dialog = current.showTimerOptions(ListeningPreset(60, true))
                    opened=dialog
                    check(dialog.findViewById<EditText>(R.id.timer_minutes_input).text.toString() == "60")
                    val modes = modeGroup(dialog.window!!.decorView)!!
                    check(modes.checkedRadioButtonId == modes.getChildAt(1).id)
                    check(p.getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK, false) == timerMode)
                    check(PlaybackSelection.token == token)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                }
                i.waitForIdleSync()
                main { check(opened?.isShowing==false);current.finish() }
                i.waitForIdleSync()
                a = null
            }
        } finally {
            main { a?.finish() }
            val editor = p.edit()
            for ((key, value) in before) if (value == null) editor.remove(key) else editor.putString(key, value)
            editor.putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK, timerMode).putInt(Prefs.LAST_TAB, lastTab).commit()
            PlaybackSelection.token = token
        }
    }
}
