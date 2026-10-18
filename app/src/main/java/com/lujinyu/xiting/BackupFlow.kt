package com.lujinyu.xiting

import android.app.AlertDialog
import android.content.Context
import org.json.JSONObject

/** 所有设备统一预览后确认；只有点击“恢复”才交给调用方写入。 */
internal object BackupFlow {
    fun confirmRestore(ctx: Context, obj: JSONObject, onConfirm: (Int) -> Unit): AlertDialog {
        val summary = summarizeBackup(obj)
        val deviceText = ctx.getString(if (BackupManager.isSameDevice(ctx, obj))
            R.string.ui_backup_same_device else R.string.ui_backup_other_device)
        val versionText = summary.version?.toString() ?: ctx.getString(R.string.ui_backup_unknown_version)
        val message = listOf(
            ctx.getString(R.string.ui_backup_version_fmt, versionText),
            ctx.getString(R.string.ui_backup_summary_fmt, summary.growth, summary.validSessionCount),
            summary.createdAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) }
                ?: ctx.getString(R.string.ui_unknown_date),
            deviceText,
            "",
            ctx.getString(R.string.ui_backup_restore_effect),
            ctx.getString(R.string.listen_backup_effect),
            ctx.getString(R.string.rel_snapshot_explain),
            obj.optJSONArray("pending_energy")?.let { raw ->
                val items=CompleteBackup.pending(raw,System.currentTimeMillis())
                ctx.getString(R.string.rel_pending_summary,(0 until items.length()).sumOf { items.getJSONObject(it).getInt("v") })
            } ?: ""
        ).joinToString("\n")
        return AlertDialog.Builder(ctx)
            .setTitle(R.string.restore_title)
            .setMessage(message)
            .setPositiveButton(R.string.restore_ok_btn) { _, _ -> onConfirm(summary.growth) }
            .setNegativeButton(R.string.dlg_cancel, null)
            .show()
    }
}
