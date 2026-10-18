package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.lujinyu.xiting.listening.*
import org.json.JSONObject

/** Destructive fixtures are emulator-only; actual third-party UAT is recorded separately. */
internal class ListeningLibraryDeviceChecks(private val i:Instrumentation) {
    private val ctx=i.targetContext
    private fun main(block:()->Unit)=i.runOnMainSync(block)
    private fun await(message:String,test:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+12000
        while(SystemClock.elapsedRealtime()<end){if(test())return;SystemClock.sleep(100)}
        error(message)
    }
    fun run(part:String) {
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val startup=java.util.concurrent.CountDownLatch(1)
        ListeningWork.submit({startup.countDown()})
        check(startup.await(5,java.util.concurrent.TimeUnit.SECONDS))
        val baseline=ListeningStore.export(ctx)
        val oldPrefs=ctx.prefs().all.toMap()
        try {
            ListeningStore.clear(ctx)
            when(part){"library_ui"->ui();"library_recording"->recording();"library_perf"->performance();else->data()}
        } finally {
            ListeningStore.replace(ctx,baseline)
            val edit=ctx.prefs().edit().clear()
            for((k,v)in oldPrefs)when(v){is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is String->edit.putString(k,v);is Set<*>->{@Suppress("UNCHECKED_CAST") edit.putStringSet(k,v as Set<String>)}}
            check(edit.commit())
        }
    }
    private fun data() {
        val now=100L*86_400_000
        fun snapshot(title:String="sample")=ContentSnapshot("any.platform","Any platform",title,"id",1000,10000,1)
        val unprotected=ListeningStore.create(ctx,snapshot())
        val protected=ListeningStore.create(ctx,snapshot())
        val preview=ListeningStore.expiredIds(ctx,now,90)
        check(preview.containsAll(listOf(unprotected,protected)))
        ListeningStore.mark(ctx,protected,snapshot())
        check(ListeningStore.clean(ctx,preview,now,90)==1){"Preview race deleted a manual mark"}
        check(ListeningStore.get(ctx,protected)!=null)
        val a=ListeningStore.saveLink(ctx,"link","https://unlisted.example/watch?id=1")
        check(ListeningStore.saveLink(ctx,"do not override","https://unlisted.example/watch?id=1")==a)
        check(ListeningStore.get(ctx,a)?.title=="link")
        check(!ListeningStore.associate(ctx,protected,"https://unlisted.example/watch?id=1"))
        check(runCatching{ListeningStore.saveLink(ctx,"bad","javascript:alert(1)")}.isFailure)
        val unknown=ListeningStore.create(ctx,ContentSnapshot("","",null,null,null,null))
        val exported=ListeningStore.export(ctx)
        ListeningStore.clear(ctx);ListeningStore.replace(ctx,exported)
        check(ListeningStore.get(ctx,unknown)?.title==null)
        check(ListeningStore.marks(ctx,protected).size==1)
        val broken=JSONObject(exported.toString());broken.getJSONArray("marks").getJSONObject(0).put("entry_id","missing")
        check(runCatching{ListeningStore.replace(ctx,broken)}.isFailure)
        check(ListeningStore.count(ctx)==3){"Failed import destroyed original data"}
        val timerOnly=ListeningStore.create(ctx,snapshot())
        ListeningStore.mark(ctx,timerOnly,snapshot(),"timer","timer-id")
        check(timerOnly in ListeningStore.expiredIds(ctx,now,90)){"Automatic timer origin wrongly protects forever"}
        val mark=ListeningStore.marks(ctx,timerOnly).single()
        ListeningStore.note(ctx,mark.id,"user note")
        check(timerOnly !in ListeningStore.expiredIds(ctx,now,90))
        ListeningStore.note(ctx,mark.id,"")
        check(timerOnly in ListeningStore.expiredIds(ctx,now,90))
        ListeningStore.settleInterruptedTimers(ctx)
        check(ListeningStore.marks(ctx,timerOnly).single().status=="interrupted")
        // Snapshot/journal includes library, and a malformed incoming DB rolls everything back.
        val backup=BackupManager.buildBackupJson(ctx)
        check(backup.getInt("v")==5 && backup.has("listening_library"))
        val count=ListeningStore.count(ctx)
        val bad=JSONObject(backup.toString()).put("listening_library",broken)
        check(!BackupManager.restore(ctx,bad))
        check(ListeningStore.count(ctx)==count && !RestoreSnapshot.isPending(ctx))
        val old=JSONObject(backup.toString());old.remove("listening_library");old.put("v",4)
        check(BackupManager.restore(ctx,old));check(ListeningStore.count(ctx)==count)
        ListeningStore.clear(ctx)
        check(RestoreSnapshot.undo(ctx));check(ListeningStore.count(ctx)==count){"Undo did not restore library"}
        RestoreSnapshot.capture(ctx)
        ListeningStore.clear(ctx)
        check(RestoreSnapshot.recoverPending(ctx));check(ListeningStore.count(ctx)==count){"Startup recovery lost library"}
        check(ListeningStore.associate(ctx,protected,"https://unlisted.example/watch?id=1",true))
        check(ListeningStore.get(ctx,a)==null)
        check(ListeningStore.get(ctx,protected)?.url=="https://unlisted.example/watch?id=1")
        check(ListeningStore.marks(ctx,protected).size==1){"Explicit merge lost a mark"}
    }
    private fun performance() {
        val rows=org.json.JSONArray();val marks=org.json.JSONArray();val now=System.currentTimeMillis()
        for(n in 0 until 10000) {
            rows.put(JSONObject().put("id","perf-$n").put("pkg","any.platform").put("app","Any platform").put("title","Public sample $n").put("url",JSONObject.NULL).put("created",now).put("updated",now+n).put("kept",0).put("saved",0))
            for(m in 0..1)marks.put(JSONObject().put("id","mark-$n-$m").put("entry_id","perf-$n").put("at",now).put("title","Public sample $n").put("pkg","any.platform").put("position",1000).put("kind","manual").put("note","").put("timer_id",JSONObject.NULL).put("status",""))
        }
        ListeningStore.replace(ctx,JSONObject().put("schema",1).put("entries",rows).put("segments",org.json.JSONArray()).put("marks",marks))
        val start=SystemClock.elapsedRealtime();check(ListeningStore.list(ctx).size==50)
        val elapsed=SystemClock.elapsedRealtime()-start
        ctx.filesDir.resolve("listening-perf-result.json").writeText(JSONObject().put("entries",10000).put("marks",20000).put("firstPageQueryMs",elapsed).put("environment","emulator").toString())
        check(elapsed<500){"First page query exceeded 500ms: $elapsed"}
    }
    private fun find(root:View,tag:String):View? {
        if(root.tag==tag)return root
        if(root is ViewGroup)for(index in 0 until root.childCount)find(root.getChildAt(index),tag)?.let{return it}
        return null
    }
    private fun ui() {
        ctx.prefs().edit().putBoolean(ListeningStore.INTRO,true).commit()
        fun open():ListeningActivity=i.startActivitySync(Intent(ctx,ListeningActivity::class.java).apply {
            action=Intent.ACTION_SEND;type="text/plain";putExtra(Intent.EXTRA_TEXT,"Sample title https://unlisted.example/watch?id=2");addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }) as ListeningActivity
        var a=open()
        main {check(find(a.window.decorView,"listen_save")!=null);find(a.window.decorView,"listen_cancel")!!.performClick()}
        SystemClock.sleep(300);check(ListeningStore.count(ctx)==0){"Cancel wrote a record"}
        val oldList=i.startActivitySync(Intent(ctx,ListeningActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ListeningActivity
        await("Initial empty list did not render") {var ready=false;main{ready=find(oldList.window.decorView,"listen_empty")!=null};ready}
        a=open()
        main{find(a.window.decorView,"listen_save")!!.performClick()}
        await("Share save failed"){ListeningStore.count(ctx)==1}
        await("Details never appeared") {var ready=false;main{ready=find(a.window.decorView,"listen_open_content")!=null};ready}
        val monitor=i.addMonitor(ListeningActivity::class.java.name,null,false)
        main {a.recreate()}
        a=i.waitForMonitorWithTimeout(monitor,5000) as? ListeningActivity ?: error("Preview/detail recreation did not return")
        i.removeMonitor(monitor)
        await("Detail lost on recreation"){var ready=false;main{ready=find(a.window.decorView,"listen_open_content")!=null};ready}
        // Relaunch from the real homepage entry after closing preview/detail.
        main{a.finish()}
        await("Existing list stayed empty after external share") {
            var refreshed=false
            main{refreshed=find(oldList.window.decorView,"listen_entry_"+ListeningStore.list(ctx).first().id)!=null}
            refreshed
        }
        main{oldList.finish()}
        val home=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {main{home.findViewById<View>(R.id.listening_recent).performClick()};SystemClock.sleep(500)}finally{main{home.finish()}}
    }
    private fun recording() {
        lateinit var session:android.media.session.MediaSession
        lateinit var black:BlackOverlay
        val previous=PlaybackSelection.token
        main {
            ctx.prefs().edit().putBoolean(ListeningStore.AUTO,true).putBoolean(ListeningStore.MARK_SHOW,false).commit()
            session=android.media.session.MediaSession(ctx,"library-generic").apply {
                setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Generic media sample").putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"item-1").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,600000).build())
                setPlaybackState(android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PLAYING,15000,1f).build());isActive=true
            }
            PlaybackSelection.token=session.sessionToken
            black=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            black.show{}
        }
        try {
            await("Automatic reliable title was not recorded"){ListeningStore.count(ctx)==1}
            main {
                val root=BlackOverlay::class.java.getDeclaredField("frame").apply{isAccessible=true}.get(black) as View
                check(find(root,"listening_mark")==null){"Mark exposed by default"}
                session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Generic media sample").putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"item-2").build())
            }
            await("Content identity change failed to split"){ListeningStore.count(ctx)==2}
            var marked=false
            main{ListeningRecorder.mark(ctx){marked=it}}
            await("Manual mark did not save"){marked}
            check(ListeningStore.list(ctx).sumOf{ListeningStore.marks(ctx,it.id).size}==1)
            main {
                session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Same title without identity").build())
                check(ListeningRecorder.capture(ctx)?.identity==null) {"Title was promoted into content identity"}
            }
            SystemClock.sleep(3500)
            check(ListeningStore.count(ctx)==2){"Missing identity was automatically merged or recorded"}
            marked=false;main{ListeningRecorder.mark(ctx){marked=it}}
            await("Unknown identity manual mark failed"){marked}
            check(ListeningStore.count(ctx)==3){"Manual mark attached to stale content"}
            val timerSnapshot=ContentSnapshot("any.platform","Any platform","Timer sample","id",5000,60000)
            var first="";var second=""
            main {first=ListeningRecorder.acceptedTimer(ctx,timerSnapshot);ListeningRecorder.timerEnded(ctx,"ended",first);second=ListeningRecorder.acceptedTimer(ctx,timerSnapshot);ListeningRecorder.timerEnded(ctx,"pause_confirmed",first)}
            await("Late pause callback did not update original timer"){
                val marks=ListeningStore.list(ctx).flatMap{ListeningStore.marks(ctx,it.id)}
                marks.any{it.timerId==first && it.status=="pause_confirmed"} && marks.any{it.timerId==second && it.status.isEmpty()}
            }
        }finally{main{black.hide();session.release();PlaybackSelection.token=previous}}
    }
}
