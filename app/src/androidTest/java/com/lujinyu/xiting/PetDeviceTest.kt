package com.lujinyu.xiting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import java.io.File

/** Runs the actual Android Canvas renderer/gesture dispatch without modifying user prefs. */
@Suppress("DEPRECATION")
class PetDeviceTest : Instrumentation() {
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);start() }
    override fun onStart() {
        val result=Bundle()
        try {
            testFiveActualGesturesAndCancel()
            testHelpAndEnergyDoNotPokeOrRepeatClick()
            testActualCanvasAtlasAllStagesSkinsAndMotion()
            testInitialLayoutStartsAndVisibilityStopsAnimation()
            testLockedPreviewDoesNotChangeGrowthOrOutfit()
            testPauseIsBoundToTheOriginalSession()
            testDemoDoesNotRecordListening()
            testServiceTimerAndDemoLifecycle()
            result.putString("stream","PASS: 8 device checks (gestures, routing, Canvas/skins, lifecycle, preview isolation, targeted pause, demo isolation, full service timer/demo).")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) {
            result.putString("stream","FAIL: "+t.stackTraceToString())
            finish(Activity.RESULT_CANCELED,result)
        }
    }
    private fun assertEquals(expected:Any,actual:Any) { check(expected==actual){"expected=$expected actual=$actual"} }
    private fun assertTrue(value:Boolean) { check(value) }
    fun testLockedPreviewDoesNotChangeGrowthOrOutfit() {
        val activity=startActivitySync(android.content.Intent(targetContext,MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        var before:List<Any?> = emptyList()
        main {
            val prefs=targetContext.prefs()
            PetForm.selected(targetContext)
            before=listOf(EnergyStore.collectedTotal(targetContext),PetForm.selected(targetContext),prefs.getString(Prefs.PET_SKIN,null))
            val dialog=showPetPreview(activity,4)
            val pet=dialog.findViewById<PetView>(R.id.pet_preview_live)
            assertEquals(4,pet.stage);assertTrue(pet.previewOnly)
            pet.performClick()
            dialog.dismiss()
            assertEquals(before,listOf(EnergyStore.collectedTotal(targetContext),PetForm.selected(targetContext),prefs.getString(Prefs.PET_SKIN,null)))
            activity.finish()
        }
    }
    fun testPauseIsBoundToTheOriginalSession() {
        val latch=java.util.concurrent.CountDownLatch(1)
        lateinit var first:android.media.session.MediaSession
        lateinit var second:android.media.session.MediaSession
        var secondPauses=0
        main {
            fun state()=android.media.session.PlaybackState.Builder()
                .setActions(android.media.session.PlaybackState.ACTION_PAUSE)
                .setState(android.media.session.PlaybackState.STATE_PLAYING,0,1f).build()
            first=android.media.session.MediaSession(targetContext,"original").apply {
                setCallback(object:android.media.session.MediaSession.Callback() {
                    override fun onPause() { latch.countDown() }
                },android.os.Handler(android.os.Looper.getMainLooper()))
                setPlaybackState(state());isActive=true
            }
            val target=TimerPlaybackTarget(android.media.session.MediaController(targetContext,first.sessionToken))
            second=android.media.session.MediaSession(targetContext,"newer").apply {
                setCallback(object:android.media.session.MediaSession.Callback() { override fun onPause(){secondPauses++} })
                setPlaybackState(state());isActive=true
            }
            assertEquals(PauseDecision.REQUEST,target.pause())
        }
        assertTrue(latch.await(3,java.util.concurrent.TimeUnit.SECONDS))
        main {
            assertEquals(0,secondPauses)
            first.setPlaybackState(android.media.session.PlaybackState.Builder()
                .setState(android.media.session.PlaybackState.STATE_PAUSED,0,0f).build())
            val target=TimerPlaybackTarget(android.media.session.MediaController(targetContext,first.sessionToken))
            assertEquals(PauseDecision.ALREADY_PAUSED,target.pause())
            first.release();second.release()
        }
    }
    fun testDemoDoesNotRecordListening() {
        val before=SessionLog.sessions(targetContext).size
        val gp=EnergyStore.collectedTotal(targetContext)
        lateinit var overlay:BlackOverlay
        main {
            overlay=BlackOverlay(targetContext,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false)
            overlay.show {}
            assertTrue(overlay.isShowing)
        }
        SystemClock.sleep(150)
        main { overlay.hide() }
        assertEquals(before,SessionLog.sessions(targetContext).size)
        assertEquals(gp,EnergyStore.collectedTotal(targetContext))
    }
    fun testServiceTimerAndDemoLifecycle() {
        val activity=startActivitySync(android.content.Intent(targetContext,MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val prefs=targetContext.prefs()
        val oldMode=prefs.getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false)
        val before=SessionLog.sessions(targetContext).size
        val energy=EnergyStore.collectedTotal(targetContext)
        val pause=java.util.concurrent.CountDownLatch(1)
        lateinit var session:android.media.session.MediaSession
        fun send(action:String,configure:(android.content.Intent)->Unit={}) {
            val intent=android.content.Intent(targetContext,OverlayService::class.java).setAction(action)
            configure(intent);targetContext.startForegroundService(intent)
        }
        fun awaitCondition(condition:()->Boolean) {
            val until=SystemClock.elapsedRealtime()+4000
            while(!condition() && SystemClock.elapsedRealtime()<until) SystemClock.sleep(30)
            assertTrue(condition())
        }
        try {
            main {
                session=android.media.session.MediaSession(targetContext,"timer-service-proof").apply {
                    setCallback(object:android.media.session.MediaSession.Callback() {
                        override fun onPause() { pause.countDown() }
                    },android.os.Handler(android.os.Looper.getMainLooper()))
                    setPlaybackState(android.media.session.PlaybackState.Builder()
                        .setActions(android.media.session.PlaybackState.ACTION_PAUSE)
                        .setState(android.media.session.PlaybackState.STATE_PLAYING,0,1f).build())
                    isActive=true
                }
                prefs.edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,true).commit()
                send(OverlayService.ACTION_SET_TIMER) {
                    it.putExtra(OverlayService.EXTRA_END_AT,System.currentTimeMillis()+1500)
                    it.putExtra(OverlayService.EXTRA_TIMER_SESSION,session.sessionToken)
                }
            }
            awaitCondition { OverlayService.instance?.timerRemainingMs()?.let{it>0}==true }
            main { OverlayService.instance!!.toggleOverlay() }
            assertTrue(pause.await(4,java.util.concurrent.TimeUnit.SECONDS))
            main {
                val service=OverlayService.instance!!
                assertTrue(service.isAnyBlackShowing())
                // Result must wait while the curtain is still visible.
                val result=OverlayService::class.java.getDeclaredField("timerResult").apply { isAccessible=true }
                service.showTimerResult();assertTrue(result.getInt(service)!=0)
                service.toggleOverlay();service.showTimerResult();assertEquals(0,result.getInt(service))
                prefs.edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false).commit()
                service.toggleOverlay()
                send(OverlayService.ACTION_SET_TIMER) { it.putExtra(OverlayService.EXTRA_END_AT,System.currentTimeMillis()+700) }
            }
            awaitCondition { OverlayService.instance?.isAnyBlackShowing()==false }
            // Use the actual service action and ten-second callback; no direct overlay shortcut.
            main {
                val service=OverlayService.instance!!
                val field=OverlayService::class.java.getDeclaredField("onSegStartUwh").apply { isAccessible=true }
                field.set(service,123L)
                activity.finish()
                send(OverlayService.ACTION_START_DEMO)
            }
            awaitCondition { OverlayService.instance?.isAnyBlackShowing()==true }
            main {
                val service=OverlayService.instance!!
                val field=OverlayService::class.java.getDeclaredField("onSegStartUwh").apply { isAccessible=true }
                assertEquals(nullValue,field.get(service)?:nullValue)
                // Arming a timer during the demo must not affect its lifecycle.
                send(OverlayService.ACTION_SET_TIMER) { it.putExtra(OverlayService.EXTRA_MINUTES,1L) }
            }
            SystemClock.sleep(10_300)
            main {
                val service=OverlayService.instance!!
                assertTrue(!service.isAnyBlackShowing());assertEquals(0L,service.timerRemainingMs())
                val handler=OverlayService::class.java.getDeclaredField("main").apply { isAccessible=true }.get(service) as android.os.Handler
                val tick=OverlayService::class.java.getDeclaredField("powerTick").apply { isAccessible=true }.get(service) as Runnable
                assertTrue(handler.hasCallbacks(tick))
                assertTrue(prefs.getBoolean(Prefs.DEMO_FEEDBACK_PENDING,false))
            }
            // The short normal curtains above are below SessionLog's minimum duration.
            assertEquals(before,SessionLog.sessions(targetContext).size)
            assertEquals(energy,EnergyStore.collectedTotal(targetContext))
        } finally {
            main {
                try { session.release() } catch(_:UninitializedPropertyAccessException) {}
                prefs.edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,oldMode)
                    .putBoolean(Prefs.DEMO_FEEDBACK_PENDING,false).commit()
                targetContext.stopService(android.content.Intent(targetContext,OverlayService::class.java))
                activity.finish()
            }
        }
    }
    private val nullValue="<null>"
    fun testInitialLayoutStartsAndVisibilityStopsAnimation() {
        val activity=startActivitySync(android.content.Intent(targetContext,MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        lateinit var pet:PetView
        lateinit var bubble:BubblePetView
        main {
            val host=android.widget.LinearLayout(activity)
            pet=PetView(activity);bubble=BubblePetView(activity)
            host.addView(pet,android.widget.LinearLayout.LayoutParams(320,320))
            host.addView(bubble,android.widget.LinearLayout.LayoutParams(180,180))
            activity.setContentView(host)
        }
        waitForIdleSync();SystemClock.sleep(150)
        main {
            fun scheduled(v:View,name:String):Boolean {
                val field=v.javaClass.getDeclaredField(name);field.isAccessible=true
                return v.handler.hasCallbacks(field.get(v) as Runnable)
            }
            assertTrue(scheduled(pet,"animationTick"));assertTrue(scheduled(bubble,"tick"))
            pet.visibility=View.GONE;bubble.visibility=View.GONE
            assertTrue(!scheduled(pet,"animationTick"));assertTrue(!scheduled(bubble,"tick"))
            pet.visibility=View.VISIBLE;bubble.visibility=View.VISIBLE
        }
        waitForIdleSync();SystemClock.sleep(150)
        main { activity.finish() }
    }
    private fun main(block:()->Unit)=runOnMainSync(block)
    private fun view(stage:Int):PetView {
        var result:PetView?=null
        main { result=PetView(targetContext).apply {
            this.stage=stage
            measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY))
            layout(0,0,900,540)
        } }
        return result!!
    }
    private fun send(v:PetView,type:Int,x:Float,y:Float) {
        main {
            val now=SystemClock.uptimeMillis()
            val e=MotionEvent.obtain(now,now,type,x,y,0)
            v.onTouchEvent(e);e.recycle()
        }
    }
    private fun action(v:PetView):String {
        val f=PetView::class.java.getDeclaredField("activeAction");f.isAccessible=true
        return f.get(v).toString()
    }
    fun testFiveActualGesturesAndCancel() {
        for(s in 0..4) {
            val v=view(s)
            send(v,MotionEvent.ACTION_DOWN,450f,270f)
            if(s==1||s==4) SystemClock.sleep(600)
            if(s==3) send(v,MotionEvent.ACTION_MOVE,600f,270f)
            send(v,MotionEvent.ACTION_UP,if(s==3)600f else 450f,270f)
            assertEquals(arrayOf("IGNITE","CHARGE","LIGHTNING","SPIN","WINGS")[s],action(v))
        }
        val v=view(1)
        send(v,MotionEvent.ACTION_DOWN,450f,270f)
        send(v,MotionEvent.ACTION_CANCEL,450f,270f)
        send(v,MotionEvent.ACTION_UP,450f,270f)
        assertEquals("NONE",action(v))
    }
    fun testHelpAndEnergyDoNotPokeOrRepeatClick() {
        val v=view(0);var help=0
        main { v.onHelp={help++} }
        // height=540: bar is y=135..405, help center y=451.
        send(v,MotionEvent.ACTION_DOWN,69f,451f);send(v,MotionEvent.ACTION_UP,69f,451f)
        assertEquals(1,help);assertEquals("NONE",action(v))
        send(v,MotionEvent.ACTION_DOWN,69f,270f);send(v,MotionEvent.ACTION_UP,69f,270f)
        assertEquals("NONE",action(v))
        var clicks=0
        main { v.setOnClickListener { clicks++ } }
        send(v,MotionEvent.ACTION_DOWN,450f,270f);send(v,MotionEvent.ACTION_UP,450f,270f)
        assertEquals(1,clicks)
    }
    fun testActualCanvasAtlasAllStagesSkinsAndMotion() {
        main {
            val bitmap=Bitmap.createBitmap(1200,760,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);canvas.drawColor(0xFFF7F4EF.toInt())
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF34334D.toInt();textSize=22f }
            val r=PetRenderer()
            val labels=arrayOf("Lv.1 IGNITE","Lv.2 CHARGE","Lv.3 LIGHTNING","Lv.4 SPIN","Lv.5 WINGS")
            for(s in 0..4) {
                r.draw(canvas,s,s*240f+120f,170f,48f+s*9f,.75f,0f,1f)
                canvas.drawText(labels[s],s*240f+30f,300f,paint)
                val a=PetMotion.action(s,700,if(s==3)60f else 0f,0f)
                r.draw(canvas,s,s*240f+120f,440f,48f+s*9f,60.4f,0f,1f,action=a,elapsed=400)
                // Reuse instance across palettes and static snapshots to exercise cache invalidation.
                for(h in floatArrayOf(0f,45f,105f,180f,240f,330f)) {
                    val temp=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888)
                    r.draw(Canvas(temp),s,80f,70f,50f,0f,h,1f,static=true)
                    assertTrue(temp.getPixel(80,70)!=Color.TRANSPARENT);temp.recycle()
                }
            }
            canvas.drawText("Android Canvas: idle / interactions at 60s uptime / compact silhouettes",30f,650f,paint)
            for(s in 0..4) r.draw(canvas,s,s*240f+120f,710f,25f,0f,0f,1f,static=true,lite=true)
            File(targetContext.cacheDir,"pet-visual-proof.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
}
