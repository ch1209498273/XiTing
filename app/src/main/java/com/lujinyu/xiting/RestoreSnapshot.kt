package com.lujinyu.xiting

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject

/** App-private durable snapshot. No raw data is logged or exported. */
internal object RestoreSnapshot {
    private fun file(ctx:Context)=ctx.filesDir.resolve("restore-rollback.json")
    private fun journal(ctx:Context)=ctx.filesDir.resolve("restore-in-progress.json")
    private fun exists(file:java.io.File)=file.isFile || java.io.File(file.path+".bak").isFile
    private fun read(file:java.io.File):ByteArray=AtomicFile(file).openRead().use {
        val bytes=it.readBytes();check(bytes.size<=2_097_152);bytes
    }
    fun available(ctx:Context)=exists(file(ctx))
    fun isPending(ctx:Context)=exists(journal(ctx))
    private fun library(ctx:Context,name:String):java.io.File {
        require(name.matches(Regex("listening-rollback-[a-f0-9]{32}\\.json")))
        return ctx.filesDir.resolve(name)
    }
    private fun restoreLibrary(ctx:Context,payload:JSONObject) {
        if(!payload.has("listening_snapshot"))return
        val source=library(ctx,payload.getString("listening_snapshot"))
        val bytes=AtomicFile(source).openRead().use {input->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val n=input.read(buffer);if(n<0)break;check(out.size()+n<=16*1024*1024);out.write(buffer,0,n)};out.toByteArray()
        }
        com.lujinyu.xiting.listening.ListeningStore.replace(ctx,JSONObject(String(bytes,Charsets.UTF_8)))
    }
    private fun cleanLibrarySnapshots(ctx:Context) {
        val retained=try {JSONObject(String(read(file(ctx)),Charsets.UTF_8)).optString("listening_snapshot")}catch(_:Exception){return}
        ctx.filesDir.listFiles()?.filter {it.name.startsWith("listening-rollback-") && it.name.endsWith(".json") && it.name!=retained}?.forEach {AtomicFile(it).delete()}
    }
    fun capture(ctx:Context) {
        val values=JSONObject()
        for((key,value) in ctx.prefs().all) {
            val type=when(value) { is String->"s";is Int->"i";is Long->"l";is Float->"f";is Boolean->"b";is Set<*>->"a";else->continue }
            values.put(key,JSONObject().put("t",type).put("v",if(value is Set<*>)JSONArray(value.toList()) else value))
        }
        val sessions=ctx.filesDir.resolve("sessions.json")
        val payload=JSONObject().put("prefs",values).put("sessions",if(sessions.exists())sessions.readText() else JSONObject.NULL)
        val outcomes=ctx.filesDir.resolve(SessionOutcomeStore.FILE_NAME)
        payload.put("session_outcomes",if(exists(outcomes))String(read(outcomes),Charsets.UTF_8) else JSONObject.NULL)
        val libraryName="listening-rollback-"+java.util.UUID.randomUUID().toString().replace("-","")+".json"
        write(library(ctx,libraryName),com.lujinyu.xiting.listening.ListeningStore.export(ctx).toString().toByteArray(Charsets.UTF_8))
        payload.put("listening_snapshot",libraryName)
        val bytes=payload.toString().toByteArray(Charsets.UTF_8)
        write(file(ctx),bytes)
        write(journal(ctx),bytes)
    }
    fun finish(ctx:Context) { val marker=journal(ctx);AtomicFile(marker).delete();check(!exists(marker));cleanLibrarySnapshots(ctx) }
    fun recoverPending(ctx:Context):Boolean = synchronized(BackupManager) { SessionLog.withDataLock { com.lujinyu.xiting.listening.ListeningStore.locked {
        val marker=journal(ctx)
        if(!exists(marker))true else try { applyPayload(ctx,JSONObject(String(read(marker),Charsets.UTF_8)));finish(ctx);true } catch(_:Exception) { false }
    } } }
    fun undo(ctx:Context):Boolean = synchronized(BackupManager) { SessionLog.withDataLock { com.lujinyu.xiting.listening.ListeningStore.locked { undoLocked(ctx) } } }
    private fun undoLocked(ctx:Context):Boolean {
        if(OverlayService.isRunning)return false
        val source=file(ctx);if(!exists(source))return false
        return try {
            val bytes=read(source);val payload=JSONObject(String(bytes,Charsets.UTF_8))
            write(journal(ctx),bytes)
            applyPayload(ctx,payload)
            finish(ctx);AtomicFile(source).delete();check(!exists(source));true
        } catch(_:Exception) { false }
    }
    private fun applyPayload(ctx:Context,payload:JSONObject) {
            restoreLibrary(ctx,payload)
            val values=payload.getJSONObject("prefs")
            val ed=ctx.prefs().edit().clear()
            for(key in values.keys()) {
                val row=values.getJSONObject(key)
                when(row.getString("t")) {
                    "s"->ed.putString(key,row.getString("v"));"i"->ed.putInt(key,row.getInt("v"))
                    "l"->ed.putLong(key,row.getLong("v"));"f"->ed.putFloat(key,row.getDouble("v").toFloat())
                    "b"->ed.putBoolean(key,row.getBoolean("v"));"a"->ed.putStringSet(key,row.getJSONArray("v").toStringSet())
                }
            }
            val sessions=ctx.filesDir.resolve("sessions.json")
            if(payload.isNull("sessions"))AtomicFile(sessions).delete()
            else write(sessions,payload.getString("sessions").toByteArray(Charsets.UTF_8))
            if(payload.has("session_outcomes")) {
                val outcomes=ctx.filesDir.resolve(SessionOutcomeStore.FILE_NAME)
                if(payload.isNull("session_outcomes"))AtomicFile(outcomes).delete()
                else write(outcomes,payload.getString("session_outcomes").toByteArray(Charsets.UTF_8))
            }
            check(ed.commit())
    }
    internal fun write(file:java.io.File,bytes:ByteArray) {
        val atomic=AtomicFile(file);val stream=atomic.startWrite()
        try { stream.write(bytes);atomic.finishWrite(stream) }
        catch(t:Throwable) { atomic.failWrite(stream);throw t }
    }
}

