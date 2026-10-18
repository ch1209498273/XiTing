package com.lujinyu.xiting

import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Scenarios prepare existing timer controls; starting the assistant never covers this page. */
internal object ListeningScenes {
    val ids = listOf("sleep", "episode", "continuous")
    fun label(scene: String) = when(scene) {
        "sleep" -> R.string.v50_sleep
        "episode" -> R.string.v50_episode
        else -> R.string.v50_continuous
    }

    fun show(activity: MainActivity, scene: String) = with(activity) {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle(label(scene)).setMessage(R.string.v50_overlay_needed)
                .setPositiveButton(R.string.dlg_ok) { _, _ -> pageHome.findViewById<android.view.View>(R.id.row_overlay).performClick() }
                .setNegativeButton(R.string.dlg_cancel, null).show()
            return@with
        }
        if (scene != "continuous") {
            if (!hasMediaAccess()) {
                val builder=AlertDialog.Builder(this).setTitle(label(scene)).setMessage(R.string.ui_media_permission)
                    .setPositiveButton(R.string.dlg_ok) { _, _ ->
                        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: RuntimeException) {}
                    }.setNegativeButton(R.string.dlg_cancel, null)
                if(scene=="sleep")builder.setNeutralButton(R.string.timer_return_video) { _, _ ->
                    showTimerOptions(ListeningPreset(30,false),confirmedScene="sleep")
                }
                builder.show()
                return@with
            }
            // The existing review panel rechecks the original selected token at confirmation.
            // Missing PAUSE or progress is explained there; a return-video action is an explicit choice.
            showTimerOptions(if(scene == "sleep") ListeningPreset(30, true) else null,
                initialEpisode = scene == "episode", confirmedScene = scene)
            return@with
        }
        val hadTimer = OverlayService.instance?.timerRemainingMs()?.let { it > 0 } == true ||
            SessionRuntime.snapshot().timer in listOf(TimerPhase.FIXED, TimerPhase.EPISODE, TimerPhase.WAITING)
        AlertDialog.Builder(this).setTitle(R.string.v50_continuous)
            .setMessage(if(hadTimer) R.string.v50_continuous_cancel else R.string.v50_continuous_review)
            .setPositiveButton(R.string.dlg_ok) { _, _ ->
                // Permission can change while a dialog is open; do not start on stale capability.
                if(!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, R.string.v50_overlay_needed, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                OverlayService.instance?.cancelTimer()
                SessionRuntime.selectScene(this, "continuous")
                startForegroundService(Intent(this, OverlayService::class.java))
                prefs().edit().putBoolean(Prefs.ASSISTANT_WANTED, true).apply()
                Toast.makeText(this, R.string.v50_ready, Toast.LENGTH_LONG).show()
                postRefresh()
            }.setNegativeButton(R.string.dlg_cancel, null).show()
    }
}
