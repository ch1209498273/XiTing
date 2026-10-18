package com.lujinyu.xiting

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File
import org.json.JSONObject

/** Isolated storage fixture: never opens the user's real sessions or preferences. */
object HistoryDeviceChecks {
    fun run(base: Context): JSONObject {
        val id="history-probe-${System.nanoTime()}"
        val dir=File(base.cacheDir,id).apply { check(mkdirs()) }
        val ctx=object:ContextWrapper(base) {
            override fun getFilesDir(): File=dir
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences =
                base.getSharedPreferences(id,mode)
        }
        try {
            val now=System.currentTimeMillis()
            val archived=ListenSession(now-120L*86_400_000,now-120L*86_400_000+1_800_000,1_800_000,0)
            val a=ListenSession(now-180_000,now-60_000,120_000,0)
            val b=ListenSession(now-60_000,now,60_000,1)
            SessionLog.add(ctx,archived);SessionLog.add(ctx,a);SessionLog.add(ctx,b)
            ctx.prefs().edit().putLong(Prefs.ENERGY_COLLECTED,1234)
                .putStringSet(Prefs.ACH_UNLOCKED,setOf("marathon")).commit()
            check(SessionLog.sessions(ctx).size==2)
            check(SessionLog.canDelete(ctx,a));check(SessionLog.delete(ctx,a))
            check(HistoryStore.totalMs(ctx)==1_860_000L)
            check(HistoryStore.totalCount(ctx)==2L)
            check(HistoryStore.maxSingleMs(ctx)==1_800_000L)
            check(HistoryStore.months(ctx)[HistoryStore.monthKey(archived.start)]?.first==1_800_000L)
            check(ctx.prefs().getLong(Prefs.ENERGY_COLLECTED,0)==1234L)
            check(ctx.prefs().getStringSet(Prefs.ACH_UNLOCKED,emptySet())==setOf("marathon"))
            check(!SessionLog.delete(ctx,a))
            // Seed a fully-written transaction to simulate process death before either state write.
            val remaining=SessionLog.sessions(ctx)
            val after=deleteFromHistory(HistorySnapshot.read(ctx),remaining,b)!!
            File(dir,"history-delete.json").writeText(JSONObject().put("sessions",org.json.JSONArray())
                .put("history",after.json()).toString())
            check(SessionLog.sessions(ctx).isEmpty())
            check(HistoryStore.totalCount(ctx)==1L)
            check(HistoryStore.totalMs(ctx)==1_800_000L)
            check(!File(dir,"history-delete.json").exists())
            return JSONObject().put("isolated_storage",true).put("archived_month_preserved",true)
                .put("growth_unlocks_preserved",true).put("duplicate_delete_rejected",true)
                .put("journal_recovery",true).put("performance",HistoryPerformanceProbe.run())
        } finally {
            base.deleteSharedPreferences(id)
            dir.listFiles()?.forEach { it.delete() };dir.delete()
        }
    }
}
