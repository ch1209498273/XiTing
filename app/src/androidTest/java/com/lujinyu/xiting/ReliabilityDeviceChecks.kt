package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.Intent
import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

internal class ReliabilityDeviceChecks(private val i:Instrumentation) {
    fun run() {
        val wanted=i.targetContext.prefs().getBoolean(Prefs.ASSISTANT_WANTED,false)
        i.runOnMainSync {
            i.targetContext.prefs().edit().putBoolean(Prefs.ASSISTANT_WANTED,false).commit()
            // API35 can deliver a pending boot broadcast when instrumentation unstops
            // the package. Queue an orderly exit after onCreate/startForeground rather
            // than stopping a service whose foreground start is still pending.
            i.targetContext.startService(Intent(i.targetContext,OverlayService::class.java)
                .setAction(OverlayService.ACTION_EXIT))
        }
        i.waitForIdleSync()
        try {
        check(!OverlayService.isRunning) { "Stop test service before isolated restore tests" }
        backupRoundTrip()
        val history=HistoryDeviceChecks.run(i.targetContext)
        File(i.targetContext.cacheDir,"increment-history-performance.json").writeText(history.toString(2))
        PersonalizationDeviceChecks(i).run()
        val activity=i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try { i.waitForIdleSync();SupportDeviceChecks.run(i,activity) }
        finally { i.runOnMainSync { activity.finish() };i.waitForIdleSync() }
        } finally { i.targetContext.prefs().edit().putBoolean(Prefs.ASSISTANT_WANTED,wanted).commit() }
    }
    private fun backupRoundTrip() {
        val base=i.targetContext;val id="backup-probe-${System.nanoTime()}"
        val dir=File(base.cacheDir,id).apply { check(mkdirs()) }
        val ctx=object:ContextWrapper(base) {
            override fun getFilesDir():File=dir
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=base.getSharedPreferences(id,mode)
        }
        try {
            val now=System.currentTimeMillis()
            val old=ListenSession(now-120L*86400000,now-120L*86400000+1800000,1800000,0)
            val recent=ListenSession(now-120000,now,120000,0)
            SessionLog.add(ctx,old);SessionLog.add(ctx,recent)
            val expiry=now+3600000
            ctx.prefs().edit().putInt(Prefs.ENERGY_COLLECTED,55).putLong(Prefs.ENERGY_CARRY_MS,12345)
                .putString(Prefs.ENERGY_PENDING,"""[{"id":1,"v":45,"e":$expiry}]""")
                .putString("appearance_mode_v1","dark").putString("listening_presets_v1","[]").commit()
            val full=BackupManager.buildBackupJson(ctx)
            check(full.getInt("v")==BackupManager.CURRENT_VERSION)
            check(full.getJSONArray("pending_energy").getJSONObject(0).getLong("e")==expiry)
            ctx.prefs().edit().putInt(Prefs.ENERGY_COLLECTED,99).putString(Prefs.ENERGY_PENDING,"[]")
                .putLong(Prefs.ENERGY_CARRY_MS,3).putLong(Prefs.STATS_TOTAL_MS,8).putString("appearance_mode_v1","light").commit()
            val before=ctx.prefs().all.toMap();val beforeSessions=File(dir,"sessions.json").readText()
            check(BackupManager.restore(ctx,full))
            check(EnergyStore.collectedTotal(ctx)==55)
            check(EnergyStore.pending(ctx).single().expireAt==expiry)
            check(ctx.prefs().getLong(Prefs.ENERGY_CARRY_MS,0)==12345L)
            check(HistoryStore.totalMs(ctx)==1920000L && HistoryStore.totalCount(ctx)==2L)
            check(ctx.prefs().getString("appearance_mode_v1",null)=="dark")
            check(RestoreSnapshot.available(ctx));check(RestoreSnapshot.undo(ctx))
            check(ctx.prefs().all==before);check(File(dir,"sessions.json").readText()==beforeSessions)
            check(!RestoreSnapshot.available(ctx));check(!RestoreSnapshot.undo(ctx))
            // Crash between a restore file write and preferences commit: replay original snapshot.
            RestoreSnapshot.capture(ctx)
            ctx.prefs().edit().putInt(Prefs.ENERGY_COLLECTED,4321).commit()
            File(dir,"sessions.json").writeText("[]")
            check(RestoreSnapshot.recoverPending(ctx))
            check(ctx.prefs().all==before && File(dir,"sessions.json").readText()==beforeSessions)
            // Inject a failed preferences commit after sessions have been replaced.
            var rejectNextCommit=true
            val backing=ctx.prefs()
            fun failingEditor():SharedPreferences.Editor {
                val real=backing.edit();lateinit var proxy:SharedPreferences.Editor
                proxy=java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)) { _,method,args ->
                    if(method.name=="commit" && rejectNextCommit) { rejectNextCommit=false;false }
                    else {
                        val result=method.invoke(real,*(args ?: emptyArray()))
                        if(method.returnType==SharedPreferences.Editor::class.java)proxy else result
                    }
                } as SharedPreferences.Editor
                return proxy
            }
            val faulty=object:ContextWrapper(ctx) {
                override fun getFilesDir():File=dir
                override fun getSharedPreferences(name:String,mode:Int):SharedPreferences =
                    java.lang.reflect.Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
                        arrayOf(SharedPreferences::class.java)) { _,method,args ->
                        if(method.name=="edit")failingEditor() else method.invoke(backing,*(args ?: emptyArray()))
                    } as SharedPreferences
            }
            check(!BackupManager.restore(faulty,full))
            check(ctx.prefs().all==before && File(dir,"sessions.json").readText()==beforeSessions)
            check(!RestoreSnapshot.isPending(ctx)) { "Successful failure rollback must settle marker" }
            // v1 lacks pending/carry/theme: these fields must keep local values.
            ctx.prefs().edit().putString(Prefs.ENERGY_PENDING,"""[{"id":2,"v":7,"e":$expiry}]""").commit()
            val v1=JSONObject().put("v",1).put("gp",7).put("sessions",JSONArray())
            check(BackupManager.restore(ctx,v1));check(EnergyStore.pending(ctx).single().value==7)
            check(ctx.prefs().getLong(Prefs.ENERGY_CARRY_MS,0)==3L)
            check(ctx.prefs().getString("appearance_mode_v1",null)=="light")
            val state=ctx.prefs().all.toMap()
            check(!BackupManager.restore(ctx,JSONObject("{}")));check(ctx.prefs().all==state)
            // Explicit empty v3 sessions replaces details instead of silently retaining old history.
            val empty=JSONObject().put("v",3).put("gp",0).put("sessions",JSONArray()).put("pending_energy",JSONArray())
            check(BackupManager.restore(ctx,empty));check(SessionLog.sessions(ctx).isEmpty())
            check(EnergyStore.pending(ctx).isEmpty())
            // Snapshot reads and restores share a transaction lock: no crossed growth/totals.
            val choices=listOf(11 to 60000L,22 to 120000L).map { (gp,duration)->
                JSONObject().put("v",3).put("gp",gp).put("sessions",JSONArray().put(JSONObject()
                    .put("s",now-duration).put("e",now).put("d",duration).put("m",0)))
            }
            check(BackupManager.restore(ctx,choices[0]))
            val failures=java.util.Collections.synchronizedList(mutableListOf<Throwable>())
            val writer=Thread { try { repeat(12) { check(BackupManager.restore(ctx,choices[it%2])) } } catch(t:Throwable) { failures.add(t) } }
            val reader=Thread { try { repeat(12) {
                val snapshot=BackupManager.buildBackupJson(ctx)
                val expected=if(snapshot.getInt("gp")==11)60000L else 120000L
                check(snapshot.getJSONArray("sessions").getJSONObject(0).getLong("d")==expected)
                check(snapshot.getJSONObject("extras").getLong(Prefs.STATS_TOTAL_MS)==expected)
            } } catch(t:Throwable) { failures.add(t) } }
            writer.start();reader.start();writer.join(10000);reader.join(10000)
            check(!writer.isAlive && !reader.isAlive) { "Backup transaction deadlocked" }
            failures.firstOrNull()?.let { throw it }
            ctx.prefs().edit().putBoolean("auto_backup_enabled",false).commit()
            BackupManager.save(ctx);check(BackupManager.lastSuccessfulBackupAt(ctx)==null)
            // Interrupted checkpoints survive a new service; clean exit suppresses the warning.
            ServiceRecovery.started(ctx);ServiceRecovery.started(ctx)
            check(ctx.prefs().getBoolean("recovery_notice",false))
            ServiceRecovery.stopped(ctx,false)
            check(!ctx.prefs().getBoolean("recovery_notice",true))
            ServiceRecovery.started(ctx);ServiceRecovery.stopped(ctx,true)
            check(ctx.prefs().getBoolean("recovery_notice",false))
        } finally {
            base.deleteSharedPreferences(id);dir.listFiles()?.forEach { it.delete() };dir.delete()
        }
    }
}
