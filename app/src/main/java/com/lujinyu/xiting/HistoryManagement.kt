package com.lujinyu.xiting

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Date selection and export are limited to retained local details; archived totals are separate. */
class HistoryManagement(private val activity: Activity, private val onChanged: () -> Unit) {
    companion object { const val EXPORT_REQUEST = 7241 }
    private var pendingExport: String? = null
    private var selectedDay: Long? = null
    private var dialog: AlertDialog? = null
    private var rows: LinearLayout? = null
    private var dateButton: Button? = null
    private var exportButton: Button? = null
    private var visible: List<ListenSession> = emptyList()

    fun show():AlertDialog {
        val ctx = activity
        val density = ctx.resources.displayMetrics.density
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20*density).toInt(), (8*density).toInt(), (20*density).toInt(), 0)
        }
        content.addView(TextView(ctx).apply { text = ctx.getString(R.string.history_scope) })
        dateButton = Button(ctx).apply { setOnClickListener { pickDate() } }
        content.addView(dateButton)
        content.addView(Button(ctx).apply {
            setText(R.string.history_all)
            setOnClickListener { selectedDay = null; refresh() }
        })
        exportButton = Button(ctx).apply { setText(R.string.history_export); setOnClickListener { export() } }
        content.addView(exportButton)
        rows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rows)
        dialog = AlertDialog.Builder(ctx).setTitle(R.string.history_title).setView(ScrollView(ctx).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel, null).create().apply {
                setOnDismissListener { rows = null; dialog = null }
                show()
            }
        refresh()
        return dialog!!
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(activity, { _, y, m, d ->
            c.clear(); c.set(y,m,d,12,0,0)
            selectedDay = Streaks.epochDayOf(c.timeInMillis)
            refresh()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun refresh() {
        val list = SessionLog.sessions(activity)
        visible = list.filter { it.start >= HistoryStore.detailCutoff(activity) &&
            (selectedDay == null || Streaks.epochDayOf(it.start) == selectedDay) }
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        dateButton?.text = if (selectedDay == null) activity.getString(R.string.history_date) else {
            // Day number is a local calendar day, so display using UTC to avoid applying the offset twice.
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(Date(selectedDay!! * 86_400_000L))
        }
        exportButton?.isEnabled = visible.isNotEmpty()
        rows?.removeAllViews()
        if (visible.isEmpty()) rows?.addView(TextView(activity).apply { setText(R.string.history_empty) })
        for (session in visible) {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(activity).apply {
                text = activity.getString(R.string.history_row, format.format(Date(session.start)),
                    session.durationMs / 60_000,
                    activity.getString(if(session.mode == SessionLog.MODE_BLACK) R.string.history_black else R.string.history_screen_off))
            }, LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Button(activity).apply {
                setText(R.string.history_delete)
                contentDescription = activity.getString(R.string.history_delete) + " " + format.format(Date(session.start))
                setOnClickListener { confirmDelete(session) }
            })
            rows?.addView(row)
        }
    }

    private fun confirmDelete(session: ListenSession) {
        if (!SessionLog.canDelete(activity, session)) {
            AlertDialog.Builder(activity).setMessage(R.string.history_delete_unavailable)
                .setPositiveButton(android.R.string.ok,null).show()
            return
        }
        AlertDialog.Builder(activity).setTitle(R.string.history_delete)
            .setMessage(activity.getString(R.string.history_delete_impact, session.durationMs / 60_000))
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.history_delete) { _, _ ->
                val deleted = SessionLog.delete(activity, session)
                Toast.makeText(activity, if(deleted) R.string.history_deleted else R.string.history_delete_failed, Toast.LENGTH_LONG).show()
                if(deleted) onChanged()
                refresh()
            }.show()
    }

    private fun export() {
        pendingExport = HistoryExport.csv(visible)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE,"XiTing-history.csv")
        }
        try { activity.startActivityForResult(intent, EXPORT_REQUEST) }
        catch (_: Exception) { pendingExport = null; Toast.makeText(activity,R.string.history_export_failed,Toast.LENGTH_LONG).show() }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if(requestCode != EXPORT_REQUEST) return false
        val csv = pendingExport.also { pendingExport = null } ?: return true
        if(resultCode != Activity.RESULT_OK || data?.data == null) return true
        val uri = data.data!!
        // The user selected the destination. Stream I/O stays off the UI thread.
        Thread {
            val success = runCatching {
                val stream = activity.contentResolver.openOutputStream(uri,"wt") ?: error("No stream")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
            }.isSuccess
            activity.runOnUiThread {
                if(!activity.isDestroyed) Toast.makeText(activity, if(success) R.string.history_exported else R.string.history_export_failed,Toast.LENGTH_LONG).show()
            }
        }.start()
        return true
    }
}

object HistoryExport {
    /** Stable machine-readable fields avoid translated headers and spreadsheet formula injection. */
    fun csv(sessions: List<ListenSession>): String = buildString {
        append("start_epoch_ms,end_epoch_ms,duration_ms,mode\r\n")
        sessions.forEach { append("${it.start},${it.end},${it.durationMs},${it.mode}\r\n") }
    }
}
