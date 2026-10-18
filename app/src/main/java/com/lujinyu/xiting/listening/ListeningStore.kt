package com.lujinyu.xiting.listening

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ListeningEntry(val id:String,val pkg:String,val app:String,val title:String?,val url:String?,
    val created:Long,val updated:Long,val kept:Boolean,val saved:Boolean)
internal data class ListeningMark(val id:String,val entryId:String,val at:Long,val title:String?,val pkg:String,
    val position:Long?,val kind:String,val note:String,val timerId:String?,val status:String)
internal data class ListeningPosition(val position:Long?,val duration:Long?,val at:Long)
internal data class ContentSnapshot(val pkg:String,val app:String,val title:String?,val identity:String?,
    val position:Long?,val duration:Long?,val at:Long=System.currentTimeMillis())

/** All database access is serialized; never calls backup/session locks. No open cursor escapes. */
internal object ListeningStore {
    const val AUTO="listening_auto_v1"
    const val MARK_SHOW="listening_mark_show_v1"
    const val DAYS="listening_retention_days_v1"
    const val INTRO="listening_intro_v1"
    private const val LAST_CLEAN="listening_last_clean_v1"
    private const val MAX_EXPORT=16*1024*1024
    private val gate=Any()
    fun <T> locked(block:()->T):T=synchronized(gate,block)
    private class Db(ctx:Context):SQLiteOpenHelper(ctx,"listening.db",null,1) {
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY,pkg TEXT NOT NULL,app TEXT NOT NULL,title TEXT,url TEXT,created INTEGER NOT NULL,updated INTEGER NOT NULL,kept INTEGER NOT NULL DEFAULT 0,saved INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE UNIQUE INDEX unique_url ON entries(url) WHERE url IS NOT NULL")
            db.execSQL("CREATE INDEX recent_entries ON entries(updated DESC,id)")
            db.execSQL("CREATE TABLE segments(id TEXT PRIMARY KEY,entry_id TEXT NOT NULL REFERENCES entries(id) ON DELETE CASCADE,started INTEGER NOT NULL,ended INTEGER,position INTEGER,duration INTEGER)")
            db.execSQL("CREATE INDEX segment_entry ON segments(entry_id,started DESC)")
            db.execSQL("CREATE TABLE marks(id TEXT PRIMARY KEY,entry_id TEXT NOT NULL REFERENCES entries(id) ON DELETE CASCADE,at INTEGER NOT NULL,title TEXT,pkg TEXT NOT NULL,position INTEGER,kind TEXT NOT NULL,note TEXT NOT NULL,timer_id TEXT,status TEXT NOT NULL)")
            db.execSQL("CREATE INDEX mark_entry ON marks(entry_id,at)")
            db.execSQL("CREATE INDEX timer_marks ON marks(timer_id)")
        }
        override fun onConfigure(db:SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int)=Unit
    }
    private var helper:Db?=null
    private fun db(ctx:Context):SQLiteDatabase {
        if(helper==null)helper=Db(ctx.applicationContext)
        return helper!!.writableDatabase
    }
    private fun uuid()=UUID.randomUUID().toString()
    private fun Cursor.str(name:String)=getString(getColumnIndexOrThrow(name))
    private fun Cursor.num(name:String)=getLong(getColumnIndexOrThrow(name))
    private fun Cursor.optional(name:String):Long? {val i=getColumnIndexOrThrow(name);return if(isNull(i))null else getLong(i)}
    private fun entry(c:Cursor)=ListeningEntry(c.str("id"),c.str("pkg"),c.str("app"),c.str("title"),c.str("url"),c.num("created"),c.num("updated"),c.num("kept")!=0L,c.num("saved")!=0L)
    private fun values(vararg pairs:Pair<String,Any?>)=ContentValues().apply {
        for((k,v)in pairs)when(v){null->putNull(k);is String->put(k,v);is Long->put(k,v);is Int->put(k,v);else->error("Unsupported database value")}
    }
    private inline fun <T> transaction(db:SQLiteDatabase,block:()->T):T {
        db.beginTransaction();return try {val result=block();db.setTransactionSuccessful();result} finally {db.endTransaction()}
    }
    fun list(ctx:Context,offset:Int=0):List<ListeningEntry> =locked {
        db(ctx).rawQuery("SELECT * FROM entries ORDER BY updated DESC,id LIMIT 50 OFFSET ?",arrayOf(offset.toString())).use { c->buildList {while(c.moveToNext())add(entry(c))} }
    }
    fun get(ctx:Context,id:String):ListeningEntry?=locked {
        db(ctx).rawQuery("SELECT * FROM entries WHERE id=?",arrayOf(id)).use { if(it.moveToFirst())entry(it) else null }
    }
    fun create(ctx:Context,s:ContentSnapshot):String=locked {
        val id=uuid();db(ctx).insertOrThrow("entries",null,values("id" to id,"pkg" to s.pkg,"app" to s.app,"title" to s.title?.take(500),"created" to s.at,"updated" to s.at));id
    }
    fun saveLink(ctx:Context,title:String,url:String):String=locked {
        require(SharedContentParser.validUrl(url));require(title.length<=500)
        val db=db(ctx)
        transaction(db) {
            val found=db.rawQuery("SELECT id FROM entries WHERE url=?",arrayOf(url)).use {if(it.moveToFirst())it.getString(0) else null}
            if(found!=null) {db.update("entries",values("saved" to 1),"id=?",arrayOf(found));found}
            else {val id=uuid();val now=System.currentTimeMillis();db.insertOrThrow("entries",null,values("id" to id,"pkg" to "","app" to "","title" to title.takeIf{it.isNotBlank()},"url" to url,"created" to now,"updated" to now,"saved" to 1));id}
        }
    }
    fun associate(ctx:Context,id:String,url:String,merge:Boolean=false):Boolean=locked {
        require(SharedContentParser.validUrl(url));val db=db(ctx)
        transaction(db) {
            if(get(ctx,id)==null)return@transaction false
            val conflict=db.rawQuery("SELECT id,kept FROM entries WHERE url=? AND id<>?",arrayOf(url,id)).use {
                if(it.moveToFirst())it.getString(0) to it.getInt(1) else null
            }
            if(conflict!=null && !merge)return@transaction false
            if(conflict!=null) {
                db.update("marks",values("entry_id" to id),"entry_id=?",arrayOf(conflict.first))
                db.update("segments",values("entry_id" to id),"entry_id=?",arrayOf(conflict.first))
                if(conflict.second!=0)db.update("entries",values("kept" to 1),"id=?",arrayOf(id))
                db.delete("entries","id=?",arrayOf(conflict.first))
            }
            db.update("entries",values("url" to url,"saved" to 1),"id=?",arrayOf(id))==1
        }
    }
    fun segment(ctx:Context,entryId:String,s:ContentSnapshot):String=locked {
        val id=uuid();val db=db(ctx)
        transaction(db) {
            db.insertOrThrow("segments",null,values("id" to id,"entry_id" to entryId,"started" to s.at,"position" to s.position,"duration" to s.duration))
            db.update("entries",values("updated" to s.at),"id=?",arrayOf(entryId));id
        }
    }
    fun updateSegment(ctx:Context,id:String,s:ContentSnapshot,end:Boolean=false)=locked {
        val db=db(ctx);transaction(db) {
            db.update("segments",values("position" to s.position,"duration" to s.duration,"ended" to s.at.takeIf{end}),"id=?",arrayOf(id))
            db.execSQL("UPDATE entries SET updated=? WHERE id=(SELECT entry_id FROM segments WHERE id=?)",arrayOf(s.at,id))
        }
    }
    fun latest(ctx:Context,id:String):ListeningPosition?=locked {
        db(ctx).rawQuery("SELECT position,duration,COALESCE(ended,started) AS at FROM segments WHERE entry_id=? ORDER BY started DESC LIMIT 1",arrayOf(id)).use {if(it.moveToFirst())ListeningPosition(it.optional("position"),it.optional("duration"),it.num("at")) else null}
    }
    fun mark(ctx:Context,entryId:String,s:ContentSnapshot,kind:String="manual",timerId:String?=null):String=locked {
        require(kind in setOf("manual","timer","note"));val id=uuid();val db=db(ctx)
        transaction(db) {
            db.insertOrThrow("marks",null,values("id" to id,"entry_id" to entryId,"at" to s.at,"title" to s.title?.take(500),"pkg" to s.pkg,"position" to s.position,"kind" to kind,"note" to "","timer_id" to timerId,"status" to ""))
            db.update("entries",values("updated" to s.at),"id=?",arrayOf(entryId));id
        }
    }
    fun marks(ctx:Context,id:String):List<ListeningMark> =locked {
        db(ctx).rawQuery("SELECT * FROM marks WHERE entry_id=? ORDER BY at DESC,id",arrayOf(id)).use {c->buildList {while(c.moveToNext())add(ListeningMark(c.str("id"),c.str("entry_id"),c.num("at"),c.str("title"),c.str("pkg"),c.optional("position"),c.str("kind"),c.str("note"),c.str("timer_id"),c.str("status")))}}
    }
    fun note(ctx:Context,id:String,note:String)=locked {require(note.length<=2000);db(ctx).update("marks",values("note" to note),"id=?",arrayOf(id))}
    fun settleInterruptedTimers(ctx:Context)=locked {
        db(ctx).update("marks",values("status" to "interrupted"),"kind='timer' AND status=''",null)
    }
    fun timerStatus(ctx:Context,timerId:String,status:String)=locked {db(ctx).update("marks",values("status" to status),"timer_id=?",arrayOf(timerId))}
    fun deleteMark(ctx:Context,id:String)=locked {db(ctx).delete("marks","id=?",arrayOf(id))}
    fun keep(ctx:Context,id:String,keep:Boolean)=locked {db(ctx).update("entries",values("kept" to if(keep)1 else 0),"id=?",arrayOf(id))}
    fun delete(ctx:Context,id:String)=locked {db(ctx).delete("entries","id=?",arrayOf(id))}
    fun clear(ctx:Context)=locked {db(ctx).delete("entries",null,null)}
    private const val UNPROTECTED="kept=0 AND saved=0 AND NOT EXISTS(SELECT 1 FROM marks WHERE marks.entry_id=entries.id AND (kind='manual' OR length(trim(note))>0))"
    fun expiredIds(ctx:Context,now:Long,days:Int):List<String> =locked {
        if(days==0)return@locked emptyList()
        val cutoff=now-days.toLong()*86_400_000
        db(ctx).rawQuery("SELECT id FROM entries WHERE updated<=? AND $UNPROTECTED",arrayOf(cutoff.toString())).use {c->buildList {while(c.moveToNext())add(c.getString(0))}}
    }
    fun clean(ctx:Context,ids:List<String>,now:Long,days:Int):Int=locked {
        if(days==0)return@locked 0
        val cutoff=now-days.toLong()*86_400_000;val db=db(ctx)
        transaction(db) {ids.sumOf { db.delete("entries","id=? AND updated<=? AND $UNPROTECTED",arrayOf(it,cutoff.toString())) }}
    }
    fun count(ctx:Context):Int=locked {db(ctx).rawQuery("SELECT count(*) FROM entries",null).use{it.moveToFirst();it.getInt(0)}}
    /** Versioned rows, not a SQLite file or opaque token. Import is strict and transactional. */
    fun export(ctx:Context):JSONObject=locked {
        val out=JSONObject().put("schema",1)
        for(table in listOf("entries","segments","marks")) {
            val rows=JSONArray();db(ctx).rawQuery("SELECT * FROM $table",null).use {c->while(c.moveToNext()) {
                val row=JSONObject();for(i in 0 until c.columnCount)row.put(c.getColumnName(i),when(c.getType(i)){Cursor.FIELD_TYPE_NULL->JSONObject.NULL;Cursor.FIELD_TYPE_INTEGER->c.getLong(i);else->c.getString(i)})
                rows.put(row)
            }};out.put(table,rows)
        }
        check(out.toString().toByteArray().size<=MAX_EXPORT){"Listening backup too large"};out
    }
    fun replace(ctx:Context,payload:JSONObject)=locked {
        require(payload.getInt("schema")==1);require(payload.toString().toByteArray().size<=MAX_EXPORT)
        val db=db(ctx);transaction(db) {
            db.delete("entries",null,null)
            val columns=mapOf("entries" to setOf("id","pkg","app","title","url","created","updated","kept","saved"),"segments" to setOf("id","entry_id","started","ended","position","duration"),"marks" to setOf("id","entry_id","at","title","pkg","position","kind","note","timer_id","status"))
            for((table,allowed)in columns) {
                val rows=payload.getJSONArray(table);require(rows.length()<=100_000)
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i);val cv=ContentValues()
                    for(k in row.keys()) {
                        require(k in allowed);val v=row.get(k)
                        when(v){JSONObject.NULL->cv.putNull(k);is String->{require(v.length<=8192);cv.put(k,v)};is Number->{val n=v.toLong();require(n>=0);cv.put(k,n)};else->error("Invalid library field")}
                    }
                    require(row.getString("id").length in 1..100)
                    if(table=="entries") {
                        require(row.isNull("title") || row.getString("title").length<=500)
                        if(!row.isNull("url"))require(SharedContentParser.validUrl(row.getString("url")))
                        require(row.getInt("kept") in 0..1 && row.getInt("saved") in 0..1)
                    }
                    if(table=="marks") {require(row.getString("kind") in setOf("manual","timer","note"));require(row.getString("note").length<=2000)}
                    db.insertOrThrow(table,null,cv)
                }
            }
        }
    }
}
