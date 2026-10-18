package com.lujinyu.xiting

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

internal object BackupManagement {
    data class Entry(val uri:Uri,val name:String)
    fun show(activity:Activity,onChanged:()->Unit):AlertDialog {
        val box=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;setPadding(24,16,24,16) }
        box.addView(Switch(activity).apply {
            text=activity.getString(R.string.rel_auto_backup);minHeight=(48*resources.displayMetrics.density).toInt()
            isChecked=activity.prefs().getBoolean("auto_backup_enabled",true)
            setOnCheckedChangeListener { _,checked ->activity.prefs().edit().putBoolean("auto_backup_enabled",checked).apply() }
        })
        box.addView(TextView(activity).apply {
            text=activity.getString(if(activity.prefs().getBoolean("last_backup_failed",false))R.string.rel_backup_failed
                else if(BackupManager.lastSuccessfulBackupAt(activity)!=null)R.string.rel_backup_ok else R.string.rel_backup_none)
            setPadding(0,12,0,12)
        })
        box.addView(TextView(activity).apply {text=activity.getString(R.string.listen_backup_effect);setPadding(0,12,0,12)})
        box.addView(action(activity,R.string.rel_backup_history) { history(activity,onChanged) })
        box.addView(action(activity,R.string.rel_undo_restore) {
            if(OverlayService.isRunning) { toast(activity,R.string.rel_stop_first);return@action }
            if(!RestoreSnapshot.available(activity)) { toast(activity,R.string.rel_no_snapshot);return@action }
            AlertDialog.Builder(activity).setTitle(R.string.rel_undo_restore).setMessage(R.string.rel_snapshot_explain)
                .setPositiveButton(R.string.restore_ok_btn) { _,_ ->
                    toast(activity,if(RestoreSnapshot.undo(activity))R.string.rel_restore_undo_ok else R.string.toast_restore_fail);onChanged()
                }.setNegativeButton(R.string.dlg_cancel,null).show()
        })
        return AlertDialog.Builder(activity).setTitle(R.string.rel_backup_manage).setView(android.widget.ScrollView(activity).apply { addView(box) })
            .setPositiveButton(R.string.dlg_cancel,null).show()
    }
    internal fun action(activity:Activity,label:Int,onClick:()->Unit)=TextView(activity).apply {
        text=activity.getString(label);textSize=15f;minHeight=(52*resources.displayMetrics.density).toInt()
        gravity=android.view.Gravity.CENTER_VERTICAL;isFocusable=true;setOnClickListener { onClick() }
    }
    private fun toast(ctx:Context,text:Int)=Toast.makeText(ctx,text,Toast.LENGTH_LONG).show()
    fun entries(ctx:Context):List<Entry> {
        if(Build.VERSION.SDK_INT<29)return emptyList()
        val out=ArrayList<Entry>()
        ctx.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID,MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf("XiTing-backup%.json","Download%/XiTing%"),"${MediaStore.Downloads.DATE_ADDED} DESC, ${MediaStore.Downloads._ID} DESC")?.use { c ->
                while(c.moveToNext() && out.size<100)out.add(Entry(Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,c.getLong(0).toString()),c.getString(1)))
            }
        return out
    }
    fun history(activity:Activity,onChanged:()->Unit) {
        val weak=java.lang.ref.WeakReference(activity)
        Thread({
            val rows=try { entries(activity.applicationContext) } catch(_:Exception) { emptyList() }
            weak.get()?.runOnUiThread {
                val host=weak.get() ?: return@runOnUiThread
                if(host.isFinishing || host.isDestroyed)return@runOnUiThread
                if(rows.isEmpty()) { toast(host,R.string.rel_backup_none);return@runOnUiThread }
                AlertDialog.Builder(host).setTitle(R.string.rel_saved_backups)
                    .setItems(rows.map { it.name }.toTypedArray()) { _,index -> select(host,rows[index],onChanged) }
                    .setNegativeButton(R.string.dlg_cancel,null).show()
            }
        },"XiTing-backup-list").start()
    }
    private fun select(activity:Activity,row:Entry,onChanged:()->Unit) {
        AlertDialog.Builder(activity).setTitle(row.name)
            .setItems(arrayOf(activity.getString(R.string.restore_ok_btn),activity.getString(R.string.rel_delete_backup))) { _,which ->
                if(which==0) {
                    if(OverlayService.isRunning) { toast(activity,R.string.rel_stop_first);return@setItems }
                    val obj=BackupManager.readFromUri(activity,row.uri)
                    if(obj==null) { toast(activity,R.string.toast_invalid_backup);return@setItems }
                    BackupFlow.confirmRestore(activity,obj) {
                        toast(activity,if(BackupManager.restore(activity,obj))R.string.rel_backup_ok else R.string.toast_restore_fail);onChanged()
                    }
                } else AlertDialog.Builder(activity).setTitle(R.string.rel_delete_backup).setMessage(R.string.rel_delete_confirm)
                    .setPositiveButton(R.string.rel_delete_backup) { _,_ ->
                        try {
                            check(activity.contentResolver.delete(row.uri,null,null)>0);onChanged()
                        } catch(_:Exception) { toast(activity,R.string.rel_backup_failed) }
                    }.setNegativeButton(R.string.dlg_cancel,null).show()
            }.setNegativeButton(R.string.dlg_cancel,null).show()
    }
}
