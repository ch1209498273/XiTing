package com.lujinyu.xiting

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import java.util.Locale

/** Allowlist projection: never serialize a SessionSnapshot or a player/controller object directly. */
internal fun diagnosticText(version: String, api: Int, model: String, permissions: SupportSnapshot,
    session: SessionSnapshot, wakeLock: WakeLockSnapshot): String {
    fun safe(value: String) = value.filterNot { it.isISOControl() }.take(160)
    fun Enum<*>.value() = name.lowercase(Locale.ROOT)
    return linkedMapOf(
        "app.version" to safe(version),
        "android.api" to api,
        "device.model" to safe(model),
        "permission.overlay" to permissions.overlay,
        "permission.media_access" to permissions.mediaAccess,
        "permission.notifications" to permissions.notifications,
        "permission.battery_exempt" to permissions.batteryExempt,
        "session.assistant" to session.assistant.value(),
        "session.curtain" to session.curtain.value(),
        "session.playback" to session.playback.value(),
        "session.timer" to session.timer.value(),
        "session.pause" to session.pause.value(),
        "session.remaining_ms" to session.remainingMs.coerceAtLeast(0),
        "wakelock.held" to wakeLock.held,
        "wakelock.held_ms" to wakeLock.heldMs.coerceAtLeast(0),
        "wakelock.total_ms" to wakeLock.totalMs.coerceAtLeast(0)
    ).entries.joinToString("\n") { "${it.key}=${it.value}" }
}

internal object SessionDiagnostics {
    /** Capture once: the user reviews the exact text later passed to the Android share sheet. */
    fun preview(activity: Activity): AlertDialog {
        val text = diagnosticText(BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, Build.MODEL,
            SupportDiagnostics.read(activity), SessionRuntime.snapshot(), WakeLockMetrics.snapshot(SystemClock.elapsedRealtime()))
        return AlertDialog.Builder(activity)
            .setTitle(R.string.diag50_title)
            .setMessage(activity.getString(R.string.diag50_scope) + "\n\n" + text)
            .setPositiveButton(R.string.diag50_share) { _, _ ->
                try {
                    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), activity.getString(R.string.diag50_title)))
                } catch (_: RuntimeException) {
                    Toast.makeText(activity, R.string.support_open_failed, Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton(R.string.support_refresh) { _, _ -> preview(activity) }
            .setNegativeButton(R.string.dlg_cancel, null).show().also { AccessibilityUi.apply(it.window!!.decorView) }
    }
}
