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
    private var achievementClickOnly=false
    private var medalOnly=false
    private var upgradeOnly=false
    private var incrementOnly=false
    private var incrementDialogs=false
    private var v50Only=false
    private var correctionOnly=false
    private var flowOnly=false
    private var flowPart:String?=null
    override fun onCreate(arguments:Bundle?) { flowPart=arguments?.getString("flowPart");flowOnly=arguments?.getString("flowOnly")=="true";correctionOnly=arguments?.getString("correctionOnly")=="true";v50Only=arguments?.getString("v50Only")=="true";incrementDialogs=arguments?.getString("incrementDialogs")=="true";incrementOnly=arguments?.getString("incrementOnly")=="true";upgradeOnly=arguments?.getString("upgradeOnly")=="true";achievementClickOnly=arguments?.getString("achievementClickOnly")=="true";medalOnly=arguments?.getString("medalOnly")=="true";super.onCreate(arguments);start() }
    override fun onStart() {
        val result=Bundle()
        try {
            if(flowOnly) { ListeningFlowDeviceChecks(this).run(flowPart);result.putString("stream","PASS: listening flow device checks");finish(Activity.RESULT_OK,result);return }
            if(correctionOnly) { UiCorrectionDeviceChecks(this).run();result.putString("stream","PASS: v4 page hierarchy restored; no home console/scenario stack; direct theme/backup settings");finish(Activity.RESULT_OK,result);return }
            if(v50Only) {
                V50SessionDeviceChecks(this).run();UiCorrectionDeviceChecks(this).run()
                result.putString("stream","PASS: v5.0 outcome backup/rollback, checkpoint recovery, idempotence, original target pause confirmation/timeout and restored UI hierarchy")
                finish(Activity.RESULT_OK,result);return
            }
            if(incrementDialogs) { IncrementDialogChecks(this).run();result.putString("stream","PASS: 96 actual dynamic dialogs across 8 languages and 2 app themes; backup/history scroll paths; screenshots cached");finish(Activity.RESULT_OK,result);return }
            if(incrementOnly) {
                ReliabilityDeviceChecks(this).run();result.putString("stream","PASS: complete v3 backup, original expiry, archived totals, v1 compatibility, durable undo, invalid backup isolation, automatic backup switch, recovery checkpoints, history delete journal, CPU benchmark, presets, actual light/dark resources, accessibility semantics and first-success failure routing");finish(Activity.RESULT_OK,result);return
            }
            if(upgradeOnly) {
                UpgradeDeviceChecks(this).run();result.putString("stream","PASS: upgrade navigation/timer validation/backup cancellation, original episode service pause/speed/seek/change, 288 native layout variants (8 languages x 2 themes x 3 sizes/fonts x 6 layouts)");finish(Activity.RESULT_OK,result);return
            }
            if(achievementClickOnly) {
                testLockedAchievementClicksKeepListOpen();testUnlockedAchievementClicksOpenDetails();result.putString("stream","PASS: four locked clicks retain list; four unlocked clicks open details and replay; state preserved");finish(Activity.RESULT_OK,result);return
            }
            if(medalOnly) {
                testGoldMedalCanvasAndDialog();result.putString("stream","PASS: medal pixel regression");finish(Activity.RESULT_OK,result);return
            }
            testFiveActualGesturesAndCancel()
            testHelpAndEnergyDoNotPokeOrRepeatClick()
            testActualCanvasAtlasAllStagesSkinsAndMotion()
            testInitialLayoutStartsAndVisibilityStopsAnimation()
            testLockedPreviewDoesNotChangeGrowthOrOutfit()
            testPauseIsBoundToTheOriginalSession()
            testEpisodeTracksOriginalTimeline()
            testDemoDoesNotRecordListening()
            testServiceTimerAndDemoLifecycle()
            testBadgeCelebrationLifecycleAndReducedMotion()
            testGoldMedalCanvasAndDialog()
            testLauncherResourcesMasksAndSwitching()
            testLockedAchievementClicksKeepListOpen()
            testUnlockedAchievementClicksOpenDetails()
            result.putString("stream","PASS: 14 device checks (gestures, routing, Canvas/skins, lifecycle, preview isolation, targeted pause, original episode, demo isolation, full service timer/demo, medal lifecycle/reduced motion, gold medal Canvas/dialog, launcher resources/masks/switching, locked and unlocked achievements).")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) {
            result.putString("stream","FAIL: "+t.stackTraceToString())
            finish(Activity.RESULT_CANCELED,result)
        }
    }
    private fun assertEquals(expected:Any,actual:Any) { check(expected==actual){"expected=$expected actual=$actual"} }
    private fun assertTrue(value:Boolean) { check(value) }
    fun testEpisodeTracksOriginalTimeline() {
        lateinit var session:android.media.session.MediaSession
        lateinit var target:EpisodeTimerTarget
        var changed=0
        main {
            session=android.media.session.MediaSession(targetContext,"episode-upgrade")
            session.setMetadata(android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"episode-1")
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,600_000).build())
            fun state(kind:Int,pos:Long,speed:Float)=android.media.session.PlaybackState.Builder()
                .setState(kind,pos,speed,android.os.SystemClock.elapsedRealtime()).build()
            session.setPlaybackState(state(android.media.session.PlaybackState.STATE_PLAYING,100_000,2f))
            session.isActive=true
            target=EpisodeTimerTarget(android.media.session.MediaController(targetContext,session.sessionToken))
            val reading=target.read()
            assertTrue(reading.valid && reading.advancing)
            assertTrue(reading.remainingMs in 249_000L..250_000L)
            target.observe { changed++ }
            session.setPlaybackState(state(android.media.session.PlaybackState.STATE_PAUSED,200_000,0f))
        }
        SystemClock.sleep(100)
        main {
            assertTrue(changed>0)
            val paused=target.read()
            assertTrue(paused.valid && !paused.advancing)
            session.setPlaybackState(android.media.session.PlaybackState.Builder()
                .setState(android.media.session.PlaybackState.STATE_PLAYING,400_000,1f,
                    android.os.SystemClock.elapsedRealtime()).build())
            assertTrue(target.read().remainingMs in 199_000L..200_000L)
            session.setMetadata(android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"episode-2")
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,600_000).build())
            assertTrue(!target.read().valid)
            target.close();session.release()
        }
    }
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
                val controller=OverlayService::class.java.getDeclaredField("timerController").apply { isAccessible=true }.get(service) as TimerController
                service.showTimerResult();assertTrue(controller.result!=0)
                service.toggleOverlay();service.showTimerResult();assertEquals(0,controller.result)
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
        lateinit var dialog:android.app.AlertDialog
        SystemClock.sleep(850)
        main {
            val host=android.widget.LinearLayout(activity)
            pet=PetView(activity);bubble=BubblePetView(activity)
            host.addView(pet,android.widget.LinearLayout.LayoutParams(320,320))
            host.addView(bubble,android.widget.LinearLayout.LayoutParams(180,180))
            dialog=android.app.AlertDialog.Builder(activity).setView(host).create()
            dialog.show()
        }
        waitForIdleSync();SystemClock.sleep(150)
        main {
            fun scheduled(v:View,name:String):Boolean {
                val field=v.javaClass.getDeclaredField(name);field.isAccessible=true
                return v.handler.hasCallbacks(field.get(v) as Runnable)
            }
            check(scheduled(pet,"animationTick")) { "Pet animation not scheduled: attached=${pet.isAttachedToWindow} shown=${pet.isShown} focus=${pet.hasWindowFocus()} size=${pet.width}x${pet.height}" }
            assertTrue(scheduled(bubble,"tick"))
            pet.visibility=View.GONE;bubble.visibility=View.GONE
            assertTrue(!scheduled(pet,"animationTick"));assertTrue(!scheduled(bubble,"tick"))
            pet.visibility=View.VISIBLE;bubble.visibility=View.VISIBLE
        }
        waitForIdleSync();SystemClock.sleep(150)
        main { dialog.dismiss();activity.finish() }
    }
    fun testBadgeCelebrationLifecycleAndReducedMotion() {
        val activity=startActivitySync(android.content.Intent(targetContext,MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        lateinit var badge:BadgeView
        lateinit var host:android.app.AlertDialog
        // Let the activity finish its delayed restore check before giving the test its own focused window.
        SystemClock.sleep(850)
        fun scheduled():Boolean {
            val field=BadgeView::class.java.getDeclaredField("celebrationTick").apply { isAccessible=true }
            return badge.handler?.hasCallbacks(field.get(badge) as Runnable)==true
        }
        main {
            badge=BadgeView(activity).apply { heroMode=true;bind(Badges.Kind.CROWN,true) }
            host=android.app.AlertDialog.Builder(activity).setView(badge).create()
            host.show()
            host.window!!.setLayout(420,420)
        }
        waitForIdleSync()
        val focusDeadline=SystemClock.elapsedRealtime()+3000
        var ready=false
        while(!ready && SystemClock.elapsedRealtime()<focusDeadline) {
            main { ready=badge.isAttachedToWindow && badge.isShown && badge.hasWindowFocus() }
            if(!ready)SystemClock.sleep(30)
        }
        main {
            check(ready) { "Badge test window never acquired focus" }
            badge.playCelebration();assertTrue(scheduled())
        }
        SystemClock.sleep(1950)
        main {
            assertTrue(!scheduled());badge.playCelebration();assertTrue(scheduled())
            badge.visibility=View.GONE;assertTrue(!scheduled())
            badge.visibility=View.VISIBLE;badge.bind(Badges.Kind.FLAME,false)
            badge.playCelebration();assertTrue(!scheduled())
            badge.bind(Badges.Kind.FLAME,true)
        }
        val old=android.provider.Settings.Global.getString(targetContext.contentResolver,"animator_duration_scale")?:"1"
        fun scale(value:String) {
            uiAutomation.executeShellCommand("settings put global animator_duration_scale "+value).use { fd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
            }
            SystemClock.sleep(250)
        }
        try {
            scale("0")
            main {
                assertTrue(!android.animation.ValueAnimator.areAnimatorsEnabled())
                badge.playCelebration();assertTrue(!scheduled())
            }
        } finally { scale(old) }
        main { host.dismiss();activity.finish();assertTrue(!scheduled()) }
    }
    fun testGoldMedalCanvasAndDialog() {
        main {
            val sample=Bitmap.createBitmap(420,180,Bitmap.Config.ARGB_8888)
            val c=Canvas(sample);c.drawColor(Color.WHITE)
            Badges.draw(c,90f,90f,60f,Badges.Kind.CROWN,true,1f)
            // Locked drawing resets the shared paint, as another row would.
            Badges.draw(c,-100f,-100f,20f,Badges.Kind.CROWN,false)
            Badges.draw(c,300f,90f,60f,Badges.Kind.CROWN,true,0f)
            assertEquals(sample.getPixel(130,90),sample.getPixel(340,90))
            sample.recycle()
            val bitmap=Bitmap.createBitmap(1200,700,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=24f }
            for(dark in 0..1) {
                paint.color=if(dark==0)0xFFF8F6F0.toInt() else 0xFF151820.toInt()
                canvas.drawRect(0f,dark*350f,1200f,(dark+1)*350f,paint)
                for((i,kind) in Badges.Kind.entries.withIndex()) {
                    val x=150f+i*300f;val y=dark*350f+140f
                    Badges.draw(canvas,x,y,78f,kind,true)
                    Badges.draw(canvas,x,y+140f,32f,kind,false)
                    paint.color=if(dark==0)0xFF604218.toInt() else 0xFFF5D591.toInt()
                    canvas.drawText(kind.name,x-70f,dark*350f+32f,paint)
                }
            }
            File(targetContext.cacheDir,"badge-gold-proof.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        val activity=startActivitySync(android.content.Intent(targetContext,MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val before=Achievements.unlockedIds(targetContext)
        lateinit var dialog:android.app.AlertDialog
        main { dialog=(activity as MainActivity).showBadgeCelebration(listOf(Achievements.ALL[3]),false) }
        waitForIdleSync();SystemClock.sleep(450)
        main {
            val decor=dialog.window!!.decorView
            val bitmap=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            File(targetContext.cacheDir,"badge-celebration-proof.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            val badge=dialog.findViewById<BadgeView>(R.id.badge_hero)
            assertTrue(badge.unlocked);assertTrue(badge.heroMode)
            badge.playCelebration();dialog.dismiss();activity.finish()
        }
        assertEquals(before,Achievements.unlockedIds(targetContext))
    }
    fun testLauncherResourcesMasksAndSwitching() {
        main {
            val options=IconStyle.OPTIONS.drop(1)
            val atlas=Bitmap.createBitmap(1200,1050,Bitmap.Config.ARGB_8888)
            val c=Canvas(atlas);c.drawColor(0xFFF5F4EF.toInt())
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=0xFF243330.toInt();textSize=20f}
            for((column,o) in options.withIndex()) {
                val info=targetContext.packageManager.getActivityInfo(o.component,android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
                assertEquals(o.iconRes,info.icon)
                val adaptive=targetContext.getDrawable(o.iconRes) as android.graphics.drawable.AdaptiveIconDrawable
                c.drawText(o.id.uppercase(),column*300f+30,28f,paint)
                for(row in 0..3) {
                    val icon=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888)
                    val ic=Canvas(icon)
                    val mask=android.graphics.Path()
                    when(row) {
                        0 -> mask.addCircle(72f,72f,72f,android.graphics.Path.Direction.CW)
                        1,3 -> mask.addRoundRect(0f,0f,144f,144f,34f,34f,android.graphics.Path.Direction.CW)
                        else -> {
                            mask.moveTo(72f,0f);mask.cubicTo(132f,0f,144f,12f,144f,72f)
                            mask.cubicTo(144f,132f,132f,144f,72f,144f)
                            mask.cubicTo(12f,144f,0f,132f,0f,72f)
                            mask.cubicTo(0f,12f,12f,0f,72f,0f);mask.close()
                        }
                    }
                    ic.clipPath(mask)
                    if(row==3 && android.os.Build.VERSION.SDK_INT>=33) {
                        ic.drawColor(0xFF23332D.toInt())
                        val mono=adaptive.monochrome!!
                        mono.setTint(0xFFF4DB9F.toInt());mono.setBounds(-36,-36,180,180);mono.draw(ic)
                        // Dedicated silhouette must not include the medal disk.
                        val sample=Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888)
                        mono.setBounds(0,0,108,108);mono.draw(Canvas(sample))
                        assertEquals(0,Color.alpha(sample.getPixel(54,26)))
                        assertEquals(0,Color.alpha(sample.getPixel(25,54)));sample.recycle()
                    } else {
                        adaptive.background.setBounds(-36,-36,180,180);adaptive.background.draw(ic)
                        adaptive.foreground.setBounds(-36,-36,180,180);adaptive.foreground.draw(ic)
                    }
                    val y=row*250f+45f;val x=column*300f+30f
                    c.drawBitmap(icon,x,y,null)
                    // Native 48/32px reductions expose small-size recognition.
                    c.drawBitmap(icon,null,android.graphics.RectF(x+174,y+20,x+222,y+68),null)
                    c.drawBitmap(icon,null,android.graphics.RectF(x+180,y+88,x+212,y+120),null)
                    c.drawText(arrayOf("CIRCLE","ROUNDED","SQUIRCLE","THEMED")[row],x,y+180,paint)
                    icon.recycle()
                }
            }
            File(targetContext.cacheDir,"launcher-gold-proof.png").outputStream().use {atlas.compress(Bitmap.CompressFormat.PNG,100,it)}
            atlas.recycle()
            val pm=targetContext.packageManager
            val old=IconStyle.OPTIONS.associate {it.component to pm.getComponentEnabledSetting(it.component)}
            val got=Achievements.unlockedIds(targetContext)
            try {
                for(o in IconStyle.OPTIONS) {
                    IconStyle.apply(targetContext,o.id)
                    val intent=android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(targetContext.packageName)
                    val launchers=pm.queryIntentActivities(intent,0)
                    assertEquals(1,launchers.size);assertEquals(o.component.className,launchers.single().activityInfo.name)
                    assertEquals(o.id,IconStyle.currentId(targetContext))
                }
            } finally {
                // Enable the original launcher first, then restore every exact component state.
                val original=IconStyle.OPTIONS.firstOrNull {old[it.component]==android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED}
                    ?:IconStyle.OPTIONS.first()
                pm.setComponentEnabledSetting(original.component,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,android.content.pm.PackageManager.DONT_KILL_APP)
                for(o in IconStyle.OPTIONS)pm.setComponentEnabledSetting(o.component,old.getValue(o.component),android.content.pm.PackageManager.DONT_KILL_APP)
            }
            assertEquals(got,Achievements.unlockedIds(targetContext))
        }
    }
    fun testLockedAchievementClicksKeepListOpen() {
        val activity=startActivitySync(android.content.Intent(targetContext,MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        main {
            val p=targetContext.prefs()
            val original=p.getStringSet(Prefs.ACH_UNLOCKED,emptySet())?.toSet()?:emptySet()
            val energy=EnergyStore.collectedTotal(targetContext)
            val icon=IconStyle.currentId(targetContext)
            var dialog:android.app.AlertDialog?=null
            try {
                // Synthetic emulator fixture only; always restore the original state.
                p.edit().putStringSet(Prefs.ACH_UNLOCKED,emptySet()).commit()
                dialog=activity.showAchievements()
                val rows=dialog.findViewById<android.widget.LinearLayout>(R.id.ach_rows)
                assertEquals(4,rows.childCount)
                for(i in 0 until rows.childCount) {
                    rows.getChildAt(i).performClick()
                    check(dialog.isShowing) { "Locked achievement $i closed the achievement list" }
                    check(!activity.isFinishing && !activity.isDestroyed)
                    assertTrue(Achievements.unlockedIds(targetContext).isEmpty())
                    assertEquals(energy,EnergyStore.collectedTotal(targetContext))
                    assertEquals(icon,IconStyle.currentId(targetContext))
                }
            } finally {
                dialog?.dismiss()
                p.edit().putStringSet(Prefs.ACH_UNLOCKED,original).commit()
                activity.finish()
            }
        }
    }
    fun testUnlockedAchievementClicksOpenDetails() {
        val activity=startActivitySync(android.content.Intent(targetContext,MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val p=targetContext.prefs()
        val original=p.getStringSet(Prefs.ACH_UNLOCKED,emptySet())?.toSet()?:emptySet()
        val fixture=Achievements.ALL.map {it.id}.toSet()
        try {
            main { p.edit().putStringSet(Prefs.ACH_UNLOCKED,fixture).commit() }
            for(i in Achievements.ALL.indices) {
                main {
                    val list=activity.showAchievements()
                    list.findViewById<android.widget.LinearLayout>(R.id.ach_rows).getChildAt(i).performClick()
                    check(!list.isShowing) { "Unlocked achievement did not open detail" }
                    check(!activity.isFinishing)
                }
                waitForIdleSync()
                val deadline=SystemClock.uptimeMillis()+3000
                var replay:android.view.accessibility.AccessibilityNodeInfo?=null
                while(replay==null && SystemClock.uptimeMillis()<deadline) {
                    replay=uiAutomation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByText(targetContext.getString(R.string.ach_replay))?.firstOrNull()
                    if(replay==null)SystemClock.sleep(50)
                }
                if(replay==null) {
                    fun describe(node:android.view.accessibility.AccessibilityNodeInfo?):String {
                        if(node==null)return "NO ACTIVE WINDOW"
                        return "${node.packageName}: ${node.text} ${node.contentDescription}\n"+
                            (0 until node.childCount).joinToString("") { describe(node.getChild(it)) }
                    }
                    File(targetContext.cacheDir,"achievement-detail-failure.txt").writeText(describe(uiAutomation.rootInActiveWindow))
                }
                check(replay!=null) { "Unlocked achievement $i has no replay detail; see cached active-window evidence" }
                check(replay.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                waitForIdleSync()
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                waitForIdleSync()
                assertEquals(fixture,Achievements.unlockedIds(targetContext))
            }
        } finally {
            main { p.edit().putStringSet(Prefs.ACH_UNLOCKED,original).commit();activity.finish() }
        }
    }
    private fun main(block:()->Unit) {
        var failure:Throwable?=null
        runOnMainSync { try { block() } catch(t:Throwable) { failure=t } }
        failure?.let { throw it }
    }
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
        return f.get(v)?.toString()?:"NONE"
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
