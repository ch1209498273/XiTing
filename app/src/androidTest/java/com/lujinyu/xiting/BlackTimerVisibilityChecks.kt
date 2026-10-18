package com.lujinyu.xiting

import android.app.Instrumentation
import android.os.SystemClock
import android.view.View

internal class BlackTimerVisibilityChecks(private val i:Instrumentation) {
    private val ctx get()=i.targetContext
    private fun main(block:()->Unit) { var error:Throwable?=null;i.runOnMainSync {try{block()}catch(t:Throwable){error=t}};error?.let{throw it} }
    fun run() {
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val key=Prefs.BLACK_TIMER_SHOW
        val prefs=ctx.prefs();val existed=prefs.contains(key);val previous=prefs.getBoolean(key,false)
        val baseline=BackupManager.buildBackupJson(ctx)
        var overlay:BlackOverlay?=null
        fun show():View {
            main {
                overlay=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false).also{it.show{}}
                BlackOverlay::class.java.getDeclaredMethod("wake").apply{isAccessible=true}.invoke(overlay)
            }
            return BlackOverlay::class.java.getDeclaredField("frame").apply{isAccessible=true}.get(overlay) as View
        }
        try {
            prefs.edit().remove(key).commit()
            val activity=i.startActivitySync(android.content.Intent(ctx,MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            try {
                i.waitForIdleSync()
                i.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("android:id/button1")
                    ?.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                main {
                    activity.findViewById<View>(R.id.nav_settings).performClick()
                    val toggle=activity.findViewById<android.widget.Switch>(R.id.switch_black_timer)
                    check(toggle.isShown && !toggle.isChecked) {"Settings timer switch not visible or not OFF by default"}
                    toggle.performClick();check(prefs.getBoolean(key,false)) {"Settings switch did not persist ON"}
                    toggle.performClick();check(!prefs.getBoolean(key,true)) {"Settings switch did not persist OFF"}
                }
            } finally {main {activity.finish()}}
            var root=show()
            main {
                val setup=root.findViewWithTag<View>("flow_timer_setup")
                check(!setup.isShown) {"Timer entry is visible by default and remains an accidental tap target"}
                setup.performClick()
                check(root.findViewWithTag<View>("flow_timer_editor")==null) {"Hidden timer entry opened editor"}
                check(root.findViewById<View>(R.id.black_return_button).isShown) {"Return button hidden with timer"}
                ctx.startForegroundService(android.content.Intent(ctx,OverlayService::class.java))
                overlay!!.hide();overlay=null
            }
            val serviceReady=SystemClock.elapsedRealtime()+6000
            while(OverlayService.instance==null && SystemClock.elapsedRealtime()<serviceReady)SystemClock.sleep(40)
            check(OverlayService.instance!=null) {"Assistant service did not start"}
            prefs.edit().putBoolean(key,true).commit()
            root=show()
            main {
                check(root.findViewWithTag<View>("flow_timer_setup").isShown)
                root.findViewWithTag<View>("flow_timer_setup").performClick()
                check(root.findViewWithTag<View>("flow_timer_editor")!=null)
                prefs.edit().putBoolean(key,false).commit();overlay!!.refreshTimerDisplay()
                check(root.findViewWithTag<View>("flow_timer_editor")==null)
                check(!root.findViewWithTag<View>("flow_timer_setup").isShown)
                check(root.findViewById<View>(R.id.black_return_button).isShown)
                overlay!!.hide();overlay=null
            }
            val ready=java.util.concurrent.CountDownLatch(1);var accepted:TimerAcceptance?=null
            main {TimerRequests.submit(ctx,TimerRequest(3,pauseKeepBlack=false)){accepted=it;ready.countDown()}}
            check(ready.await(6,java.util.concurrent.TimeUnit.SECONDS)&&accepted?.accepted==true) {"Timer not accepted: $accepted"}
            prefs.edit().putBoolean(key,false).commit()
            root=show()
            main {
                check(!root.findViewWithTag<View>("flow_timer_setup").isShown)
                check(!root.findViewById<View>(R.id.black_timer_extend).isShown)
                check(!root.findViewById<View>(R.id.black_timer_cancel).isShown)
                check(OverlayService.instance!!.timerRemainingMs()>0) {"Hiding timer cancelled active deadline"}
                overlay!!.hide();overlay=null
            }
            prefs.edit().putBoolean(key,true).commit()
            root=show()
            main {
                check(root.findViewById<View>(R.id.black_timer_extend).isShown)
                check(OverlayService.instance!!.timerRemainingMs()>0)
                check(CompleteBackup.encode(prefs).getBoolean(key)) {"Timer display preference missing from backup"}
            }
        } finally {
            main {overlay?.hide();ctx.startService(android.content.Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))}
            val until=SystemClock.elapsedRealtime()+5000
            while(OverlayService.isRunning&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(30)
            check(BackupManager.restore(ctx,baseline))
            prefs.edit().apply {if(existed)putBoolean(key,previous) else remove(key)}.commit()
        }
    }
}
