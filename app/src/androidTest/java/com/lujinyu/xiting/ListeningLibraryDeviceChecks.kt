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
        // 先删陈旧结果：失败的一轮不能留下上一轮的 JSON 冒充成功证据。
        ctx.filesDir.resolve("listening-perf-result.json").delete()
        // 首屏 = 打开列表页到 PAGE_SIZE 行全部挂上且可点，每行还要取播放器图标。
        // 500ms 是 OPPO 真机目标；模拟器只记录同口径基线，不在这里断言手机指标。
        val visible=ctx.packageManager.getInstalledApplications(0).map{it.packageName}.distinct()
        val iconPkgs=(visible+listOf(ctx.packageName)).take(8)
        val now=System.currentTimeMillis()
        val out=ctx.filesDir.resolve("listening-perf-result.json")
        fun report(f:(JSONObject)->Unit) { val j=JSONObject().put("entries",10000).put("pageSize",ListeningStore.PAGE_SIZE).put("iconPackageCount",iconPkgs.size).put("targetMs",500).put("targetDevice","OPPO").put("phoneTargetMeasured",false).put("environment","emulator").put("metric","warm list first screen, median of 3").put("model",android.os.Build.MODEL).put("hardware",android.os.Build.HARDWARE).put("sdkInt",android.os.Build.VERSION.SDK_INT);f(j);out.writeText(j.toString()) }
        val rows=org.json.JSONArray();val marks=org.json.JSONArray()
        for(n in 0 until 10000) {
            rows.put(JSONObject().put("id","perf-$n").put("pkg",iconPkgs[n%iconPkgs.size]).put("app","Any platform").put("title","Public sample $n").put("url",JSONObject.NULL).put("created",now).put("updated",now+n).put("kept",0).put("saved",0))
            for(m in 0..1)marks.put(JSONObject().put("id","mark-$n-$m").put("entry_id","perf-$n").put("at",now).put("title","Public sample $n").put("pkg","any.platform").put("position",1000).put("kind","manual").put("note","").put("timer_id",JSONObject.NULL).put("status",""))
        }
        ListeningStore.replace(ctx,JSONObject().put("schema",1).put("entries",rows).put("segments",org.json.JSONArray()).put("marks",marks))
        // onStop 会提交整库自动备份；10k 条时它会排在列表查询前，隔离掉才能测列表本身。
        ctx.prefs().edit().putBoolean(ListeningStore.INTRO,true).putBoolean("auto_backup_enabled",false).commit()
        val queryStart=SystemClock.elapsedRealtime();check(ListeningStore.list(ctx).size==ListeningStore.PAGE_SIZE){"Seeded first page is not ${ListeningStore.PAGE_SIZE} rows"}
        val queryMs=SystemClock.elapsedRealtime()-queryStart
        fun launch():Long {
            val start=SystemClock.elapsedRealtime()
            val list=i.startActivitySync(Intent(ctx,ListeningActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ListeningActivity
            var rendered=0;var timedOut=true
            while(SystemClock.elapsedRealtime()-start<20000) {
                main{rendered=countTag(list.window.decorView,"listen_entry_")}
                if(rendered>=ListeningStore.PAGE_SIZE){timedOut=false;break}
                SystemClock.sleep(50)
            }
            val ms=SystemClock.elapsedRealtime()-start
            main{list.finish()}
            check(!timedOut){"First screen rendered only $rendered of ${ListeningStore.PAGE_SIZE} rows in 20s"}
            return ms
        }
        val cold=launch()
        SystemClock.sleep(300)
        val samples=List(3){launch().also{SystemClock.sleep(300)}}
        val warm=samples.sorted()[1]
        report{it.put("marks",20000).put("firstPageQueryMs",queryMs).put("coldFirstScreenMs",cold).put("warmSamplesMs",org.json.JSONArray(samples)).put("firstScreenInteractiveMs",warm)}
        check(queryMs<500){"First page query exceeded 500ms: $queryMs"}
    }
    private fun countTag(root:View,prefix:String):Int {
        var n=if(root.tag?.toString()?.startsWith(prefix)==true)1 else 0
        if(root is ViewGroup)for(index in 0 until root.childCount)n+=countTag(root.getChildAt(index),prefix)
        return n
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
            // 只给标题、不给 media id 的播放器是多数；不记就等于「最近听过」只有 B 站能用。
            await("Title without identity was not recorded"){ListeningStore.count(ctx)==3}
            main { session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Same title without identity").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,300_000).build()) }
            SystemClock.sleep(3_500)
            check(ListeningStore.count(ctx)==3){"Repeated title without identity split into extra entries"}
            // 播放器谎报状态时仍然记录标题，只是不把可疑进度写进去。
            main {
                session.setPlaybackState(android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_REWINDING,0,1f).build())
                session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Unreliable state title").build())
            }
            await("Unreliable playback state dropped the title entirely"){ListeningStore.count(ctx)==4}
            check(ListeningStore.list(ctx).any{it.title=="Unreliable state title"}) {"Unreliable state title was stored under another name"}
            check(ListeningStore.latest(ctx,ListeningStore.list(ctx).first{it.title=="Unreliable state title"}.id)?.position==null) {"Suspicious position was recorded as a resume point"}
            marked=false;main{ListeningRecorder.mark(ctx){marked=it}}
            await("Unknown identity manual mark failed"){marked}
            check(ListeningStore.count(ctx)==4){"Manual mark created an entry the recorder already made"}
            check(ListeningStore.list(ctx).sumOf{ListeningStore.marks(ctx,it.id).size}==2)
            // 档 2：在播但系统不给可用标题（或标题只是 App 自己名字）→ 记一条「未识别内容」来源条目，同一个 App 只滚动一条。
            main {
                session.setPlaybackState(android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PLAYING,0,1f).build())
                session.setMetadata(android.media.MediaMetadata.Builder().putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,300_000).build())
            }
            val sourceBase=ListeningStore.count(ctx)
            await("Playing session without any title produced no source row"){ListeningStore.count(ctx)==sourceBase+1}
            val source=ListeningStore.list(ctx).first{it.title==null}
            SystemClock.sleep(3_500)
            check(ListeningStore.count(ctx)==sourceBase+1){"Title-less playback added a new row on every poll"}
            main { session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,ctx.playerLabel(ctx.packageName)).build()) }
            SystemClock.sleep(3_500)
            check(ListeningStore.count(ctx)==sourceBase+1){"App-name-only title became a content row"}
            check(ListeningStore.get(ctx,source.id)!!.title==null){"Source row stored the app name as content"}
            // 会话闪断（广告、缓冲、切后台）后同一条内容续写原条目，不得记成两行（真机 YouTube 已复现）。
            val base=ListeningStore.count(ctx)
            fun episode(show:Boolean)=android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE,if(show)"Episode across ad break" else "")
                .putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"ad-break").build()
            main { session.setMetadata(episode(true)) }
            await("New episode was not recorded"){ListeningStore.count(ctx)==base+1}
            main { session.setMetadata(episode(false)) }
            SystemClock.sleep(3_500)
            main { session.setMetadata(episode(true)) }
            SystemClock.sleep(3_500)
            check(ListeningStore.count(ctx)==base+1){"Session dropout split one episode into two rows"}
            val resumed=ListeningStore.list(ctx).first{it.title=="Episode across ad break"}
            val segs=ListeningStore.export(ctx).getJSONArray("segments")
            var segments=0;for(k in 0 until segs.length())if(segs.getJSONObject(k).getString("entry_id")==resumed.id)segments+=1
            check(segments>=2){"Resumed content did not append a new segment"}
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
