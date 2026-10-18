package com.lujinyu.xiting

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/** On-demand local snapshot. Session declarations are capabilities, not proof of response. */
internal object SupportDiagnostics {
    fun read(context: Context): SupportSnapshot {
        val media = try { context.queryNowPlaying() } catch (_: RuntimeException) { null }
        val service = OverlayService.instance
        val actions = try { media?.controller?.playbackState?.actions ?: 0L } catch (_: RuntimeException) { 0L }
        return SupportSnapshot(Settings.canDrawOverlays(context), context.hasMediaAccess(),
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.areNotificationsEnabled() == true,
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(context.packageName) == true,
            media != null, media?.playing == true, actions and PlaybackState.ACTION_PAUSE != 0L,
            media?.canSeek == true, service != null, service?.isAnyBlackShowing() == true)
    }

    fun show(activity: Activity): AlertDialog {
        val snapshot = read(activity)
        fun state(value: Boolean) = activity.getString(if(value) R.string.support_yes else R.string.support_no)
        val facts = listOf(R.string.support_overlay to snapshot.overlay, R.string.support_media_access to snapshot.mediaAccess,
            R.string.support_notifications to snapshot.notifications, R.string.support_battery to snapshot.batteryExempt,
            R.string.support_session to snapshot.session, R.string.support_playing to snapshot.playing,
            R.string.support_pause to snapshot.pause, R.string.support_seek to snapshot.seek,
            R.string.support_service to snapshot.assistant, R.string.support_black to snapshot.black)
            .joinToString("\n") { (label, value) -> "${activity.getString(label)}: ${state(value)}" }
        val repair = snapshot.suggestedRepair()
        val labels = mapOf(SupportRepair.OVERLAY to R.string.support_fix_overlay,
            SupportRepair.MEDIA_ACCESS to R.string.support_fix_media, SupportRepair.NOTIFICATIONS to R.string.support_fix_notifications,
            SupportRepair.PLAYER to R.string.support_fix_player, SupportRepair.START_ASSISTANT to R.string.support_fix_start,
            SupportRepair.BATTERY to R.string.support_fix_battery, SupportRepair.APP_DETAILS to R.string.support_fix_app)
        return AlertDialog.Builder(activity).setTitle(R.string.support_title)
            .setMessage("$facts\n\n${activity.getString(R.string.support_scope)}")
            .setPositiveButton(labels.getValue(repair)) { _, _ -> repair(activity, repair) }
            .setNeutralButton(R.string.diag50_export) { _, _ -> SessionDiagnostics.preview(activity) }
            .setNegativeButton(R.string.dlg_cancel, null).show().also { AccessibilityUi.apply(it.window!!.decorView) }
    }

    private fun repair(activity: Activity, action: SupportRepair) {
        val pkg = Uri.parse("package:${activity.packageName}")
        try {
            when (action) {
                SupportRepair.OVERLAY -> activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,pkg))
                SupportRepair.MEDIA_ACCESS -> activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                SupportRepair.NOTIFICATIONS -> activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName))
                SupportRepair.BATTERY -> activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                SupportRepair.APP_DETAILS -> activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,pkg))
                SupportRepair.START_ASSISTANT -> activity.startForegroundService(Intent(activity,OverlayService::class.java))
                SupportRepair.PLAYER -> AlertDialog.Builder(activity).setTitle(R.string.support_fix_player)
                    .setMessage(R.string.support_player_help).setPositiveButton(R.string.dlg_got_it,null).show()
            }
        } catch (_: RuntimeException) {
            Toast.makeText(activity,R.string.support_open_failed,Toast.LENGTH_LONG).show()
        }
    }
}

