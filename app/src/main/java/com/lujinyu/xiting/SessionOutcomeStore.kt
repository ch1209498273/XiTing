package com.lujinyu.xiting

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject

/** A small public summary. Never persist media titles, notification text or tokens. */
data class SessionOutcome(val id:String,val startedAt:Long,val endedAt:Long?,val scene:String,val reason:String,val pause:String)

internal object SessionOutcomeCodec {
    private const val RETENTION_MS=60L*24*60*60*1000
    private val scenes=setOf("sleep","episode","continuous")
    private val reasons=setOf("user","timer","interrupted","unknown")
    private val pauses=setOf("none","requested","confirmed","already_paused","unavailable","failed","unconfirmed")
    private fun integer(value:Any?):Long?=when(value) { is Long->value;is Int->value.toLong();else->null }
    fun decode(rows:JSONArray,now:Long,allowLocalClockRollback:Boolean=false):List<SessionOutcome> {
        val found=LinkedHashMap<String,SessionOutcome>()
        for(i in 0 until minOf(rows.length(),10000)) {
            val row=rows.optJSONObject(i) ?: continue
            val id=row.opt("id") as? String ?: continue
            val start=integer(row.opt("startedAt")) ?: continue
            val end=if(row.has("endedAt") && row.isNull("endedAt"))null else integer(row.opt("endedAt")) ?: continue
            val scene=row.opt("scene") as? String ?: continue
            val reason=row.opt("reason") as? String ?: continue
            val pause=row.opt("pause") as? String ?: continue
            if(!id.matches(Regex("[A-Za-z0-9_-]{1,128}")) || start !in 1..253402300799999L ||
                (end!=null && (end<start || end>253402300799999L)) || scene !in scenes || reason !in reasons || pause !in pauses)continue
            if((end ?: start)<now-RETENTION_MS || (!allowLocalClockRollback && (start>now+300000L || (end!=null && end>now+300000L))))continue
            found.putIfAbsent(id,SessionOutcome(id,start,end,scene,reason,pause))
        }
        return found.values.sortedByDescending { it.endedAt ?: it.startedAt }.take(100)
    }
    fun encode(rows:List<SessionOutcome>):JSONArray=JSONArray().also { out ->
        for(row in rows)out.put(JSONObject().put("id",row.id).put("startedAt",row.startedAt)
            .put("endedAt",row.endedAt ?: JSONObject.NULL).put("scene",row.scene).put("reason",row.reason).put("pause",row.pause))
    }
    fun sanitize(rows:JSONArray,now:Long):JSONArray=encode(decode(rows,now))
    @JvmOverloads fun upsert(rows:JSONArray,item:JSONObject,now:Long,allowLocalClockRollback:Boolean=false):JSONArray {
        val old=decode(rows,now,allowLocalClockRollback)
        val valid=decode(JSONArray().put(item),now,allowLocalClockRollback).singleOrNull() ?: return encode(old)
        val existing=old.find { it.id==valid.id }
        val updated=if(existing==null)valid else if(existing.pause=="requested" &&
            valid.pause in setOf("confirmed","unconfirmed","unavailable","failed"))existing.copy(pause=valid.pause) else existing
        return encode(decode(encode(listOf(updated)+old.filter { it.id!=updated.id }),now,allowLocalClockRollback))
    }
}

object SessionOutcomeStore {
    internal const val FILE_NAME="session-outcomes.json"
    private fun file(ctx:Context)=AtomicFile(ctx.filesDir.resolve(FILE_NAME))
    private fun read(ctx:Context,strict:Boolean=false):List<SessionOutcome> {
        val atomic=file(ctx)
        if(!atomic.baseFile.exists() && !java.io.File(atomic.baseFile.path+".bak").exists())return emptyList()
        return try {
            val bytes=atomic.openRead().use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true) { val n=input.read(buffer);if(n<0)break;check(out.size()+n<=131072);out.write(buffer,0,n) }
                out.toByteArray()
            }
            val root=JSONObject(String(bytes,Charsets.UTF_8))
            check(root.optInt("v")==1) { "Unsupported outcome format" }
            SessionOutcomeCodec.decode(root.getJSONArray("rows"),System.currentTimeMillis(),allowLocalClockRollback=true)
        } catch(e:Exception) { if(strict)throw IllegalStateException("Outcome history could not be read");emptyList() }
    }
    private fun write(ctx:Context,rows:List<SessionOutcome>) {
        val bytes=JSONObject().put("v",1).put("rows",SessionOutcomeCodec.encode(rows)).toString().toByteArray(Charsets.UTF_8)
        val atomic=file(ctx);val stream=atomic.startWrite()
        try { stream.write(bytes);atomic.finishWrite(stream) }
        catch(t:Throwable) { atomic.failWrite(stream);throw t }
    }
    fun list(ctx:Context):List<SessionOutcome> = SessionLog.withDataLock { read(ctx) }
    fun record(ctx:Context,item:SessionOutcome) = SessionLog.withDataLock {
        val old=read(ctx,strict=true)
        val rows=SessionOutcomeCodec.upsert(SessionOutcomeCodec.encode(old),SessionOutcomeCodec.encode(listOf(item)).getJSONObject(0),System.currentTimeMillis(),allowLocalClockRollback=true)
        val updated=SessionOutcomeCodec.decode(rows,System.currentTimeMillis(),allowLocalClockRollback=true)
        if(updated!=old)write(ctx,updated)
    }
    fun encode(ctx:Context):JSONArray = SessionLog.withDataLock { SessionOutcomeCodec.encode(read(ctx,strict=true)) }
    fun restore(ctx:Context,rows:JSONArray) = SessionLog.withDataLock {
        write(ctx,SessionOutcomeCodec.decode(rows,System.currentTimeMillis()))
    }
    fun clear(ctx:Context) = SessionLog.withDataLock { file(ctx).delete() }
}

