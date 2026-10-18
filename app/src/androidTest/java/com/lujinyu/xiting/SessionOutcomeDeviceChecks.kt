package com.lujinyu.xiting

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Isolated files and preferences; invoke after stopping the assistant. */
internal object SessionOutcomeDeviceChecks {
    fun run(base:Context) {
        check(!OverlayService.isRunning)
        val id="outcome-probe-${System.nanoTime()}"
        val dir=File(base.cacheDir,id).apply { check(mkdirs()) }
        val ctx=object:ContextWrapper(base) {
            override fun getFilesDir():File=dir
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=base.getSharedPreferences(id,mode)
        }
        try {
            val now=System.currentTimeMillis()
            val first=SessionOutcome("probe",now-1000,now,"sleep","timer","requested")
            SessionOutcomeStore.record(ctx,first)
            SessionOutcomeStore.record(ctx,first)
            SessionOutcomeStore.record(ctx,first.copy(pause="confirmed"))
            check(SessionOutcomeStore.list(ctx)==listOf(first.copy(pause="confirmed")))
            val full=BackupManager.buildBackupJson(ctx)
            check(full.getInt("v")==4 && full.getJSONArray("session_outcomes").length()==1)
            SessionOutcomeStore.clear(ctx)
            check(BackupManager.restore(ctx,full));check(SessionOutcomeStore.list(ctx).size==1)
            check(RestoreSnapshot.undo(ctx));check(SessionOutcomeStore.list(ctx).isEmpty())
            SessionOutcomeStore.record(ctx,first)
            for(version in 1..3) {
                check(BackupManager.restore(ctx,JSONObject().put("v",version).put("gp",0).put("sessions",JSONArray())))
                check(SessionOutcomeStore.list(ctx)==listOf(first))
            }
            RestoreSnapshot.capture(ctx)
            SessionOutcomeStore.clear(ctx)
            check(RestoreSnapshot.recoverPending(ctx));check(SessionOutcomeStore.list(ctx)==listOf(first))
            // AtomicFile's surviving .bak is part of the rollback snapshot.
            val file=File(dir,"session-outcomes.json")
            val bytes=AtomicFile(file).readFully()
            check(file.renameTo(File(file.path+".bak")))
            RestoreSnapshot.capture(ctx)
            SessionOutcomeStore.clear(ctx)
            check(RestoreSnapshot.recoverPending(ctx));check(AtomicFile(file).readFully().contentEquals(bytes))
            check(SessionOutcomeStore.list(ctx)==listOf(first))
            SessionOutcomeStore.restore(ctx,JSONArray());check(SessionOutcomeStore.list(ctx).isEmpty())
            for(raw in listOf("broken", "{\"v\":99,\"rows\":[]}")) {
                AtomicFile(file).delete();file.writeText(raw)
                check(SessionOutcomeStore.list(ctx).isEmpty())
                check(runCatching { SessionOutcomeStore.record(ctx,first) }.isFailure)
                check(file.readText()==raw) { "Unreadable history must not be overwritten" }
            }
            AtomicFile(file).delete();SessionOutcomeStore.record(ctx,first)
            val beforeOutcome=AtomicFile(file).readFully()
            val beforePrefs=ctx.prefs().all.toMap()
            val beforeSessions=File(dir,"sessions.json").takeIf { it.exists() }?.readBytes()
            var rejectNextCommit=true
            val backing=ctx.prefs()
            fun failingEditor():SharedPreferences.Editor {
                val real=backing.edit();lateinit var proxy:SharedPreferences.Editor
                proxy=java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)) { _,method,args ->
                    if(method.name=="commit" && rejectNextCommit) { rejectNextCommit=false;false }
                    else { val result=method.invoke(real,*(args ?: emptyArray()))
                        if(method.returnType==SharedPreferences.Editor::class.java)proxy else result }
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
            val replacement=JSONObject(full.toString()).put("session_outcomes",JSONArray())
            check(!BackupManager.restore(faulty,replacement))
            check(AtomicFile(file).readFully().contentEquals(beforeOutcome))
            check(ctx.prefs().all==beforePrefs)
            check(beforeSessions?.contentEquals(File(dir,"sessions.json").readBytes()) ?: !File(dir,"sessions.json").exists())
            check(!RestoreSnapshot.isPending(ctx))
        } finally {
            base.getSharedPreferences(id,Context.MODE_PRIVATE).edit().clear().commit()
            dir.deleteRecursively()
        }
    }
}


