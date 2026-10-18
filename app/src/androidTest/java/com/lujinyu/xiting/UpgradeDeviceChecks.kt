package com.lujinyu.xiting

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import java.io.File
import java.util.Locale
import org.json.JSONObject

/** Android framework interaction and renderer proofs; synthetic data, emulator only. */
internal class UpgradeDeviceChecks(private val i:Instrumentation) {
    private val ctx get()=i.targetContext
    private fun main(block:()->Unit) {
        var failure:Throwable?=null
        i.runOnMainSync { try { block() } catch(t:Throwable) { failure=t } }
        failure?.let { throw it }
    }
    private fun await(test:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+5000
        while(!test() && SystemClock.elapsedRealtime()<until)SystemClock.sleep(40)
        check(test()) { "upgrade condition timed out" }
    }
    fun run() { navigationTimerBackup();episodeService();renderMatrix() }
    private fun navigationTimerBackup() {
        val p=ctx.prefs();val oldMode=p.getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false)
        val oldTab=p.getInt(Prefs.LAST_TAB,0)
        val a=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var dialog:AlertDialog
        try {
            i.waitForIdleSync()
            main {
                for((nav,page) in listOf(R.id.nav_home to R.id.page_home,R.id.nav_stats to R.id.page_stats,R.id.nav_settings to R.id.page_settings)) {
                    a.findViewById<View>(nav).performClick()
                    check(a.findViewById<View>(page).visibility==View.VISIBLE)
                    check(a.findViewById<View>(nav).isSelected)
                }
                a.findViewById<View>(R.id.nav_home).performClick()
                dialog=a.showTimerOptions()
            }
            i.waitForIdleSync()
            main {
                val input=dialog.findViewById<EditText>(R.id.timer_minutes_input)
                // Validation keeps the panel and the previously armed timer untouched.
                for(value in listOf("0","601","999999999999999999999")) {
                    input.setText(value);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    check(dialog.isShowing && input.error!=null) { "timer invalid $value closed=${!dialog.isShowing} error=${input.error}" }
                }
                fun returnMode(v:View) {
                    if(v is RadioGroup) v.check(v.getChildAt(0).id)
                    if(v is android.view.ViewGroup)for(n in 0 until v.childCount)returnMode(v.getChildAt(n))
                }
                returnMode(dialog.window!!.decorView)
                input.setText("15");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                check(!dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled) { "Timer should await service acknowledgement" }
                var confirmed=false
                val before=EnergyStore.collectedTotal(ctx)
                val backup=BackupManager.buildBackupJson(ctx)
                val restore=BackupFlow.confirmRestore(a,backup) { confirmed=true }
                check(restore.isShowing)
                restore.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                check(!confirmed && EnergyStore.collectedTotal(ctx)==before)
                val foreign=JSONObject(backup.toString()).put("device","synthetic-other-device")
                val other=BackupFlow.confirmRestore(a,foreign) { confirmed=true }
                other.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();check(!confirmed)
            }
            await { !dialog.isShowing && (OverlayService.instance?.timerRemainingMs() ?: 0)>0 }
            main {
                val service=OverlayService.instance!!
                val before=service.timerRemainingMs();service.extendTimer()
                check(service.timerRemainingMs() in (before+895_000)..(before+900_000))
                service.cancelTimer();check(service.timerRemainingMs()==0L)
            }
        } finally {
            main { OverlayService.instance?.cancelTimer();a.finish();p.edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,oldMode).putInt(Prefs.LAST_TAB,oldTab).apply() }
        }
    }
    private fun episodeService() {
        lateinit var session:android.media.session.MediaSession
        val p=ctx.prefs();val old=p.getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false)
        fun state(kind:Int,pos:Long,speed:Float) {
            session.setPlaybackState(android.media.session.PlaybackState.Builder()
                .setActions(android.media.session.PlaybackState.ACTION_PAUSE or android.media.session.PlaybackState.ACTION_SEEK_TO)
                .setState(kind,pos,speed,SystemClock.elapsedRealtime()).build())
        }
        try {
            main {
                p.edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false).apply()
                session=android.media.session.MediaSession(ctx,"synthetic-upgrade-episode")
                session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"one")
                    .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,600_000).build())
                state(android.media.session.PlaybackState.STATE_PLAYING,100_000,2f);session.isActive=true
                ctx.startForegroundService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_SET_TIMER)
                    .putExtra(OverlayService.EXTRA_END_AT,System.currentTimeMillis()+250_000)
                    .putExtra(OverlayService.EXTRA_TIMER_SESSION,session.sessionToken)
                    .putExtra(OverlayService.EXTRA_TIMER_EPISODE,true))
            }
            await { (OverlayService.instance?.timerRemainingMs() ?: 0) in 249_000..255_000 }
            main { state(android.media.session.PlaybackState.STATE_PAUSED,200_000,0f) }
            await { OverlayService.instance?.timerWaitsForPlayback()==true }
            val frozen=OverlayService.instance!!.timerRemainingMs();SystemClock.sleep(1200)
            check(kotlin.math.abs(OverlayService.instance!!.timerRemainingMs()-frozen)<100)
            main { state(android.media.session.PlaybackState.STATE_PLAYING,400_000,1f) }
            await { OverlayService.instance?.timerWaitsForPlayback()==false && OverlayService.instance!!.timerRemainingMs() in 199_000..205_000 }
            main { session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"two")
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,600_000).build()) }
            await { OverlayService.instance?.timerRemainingMs()==0L }
        } finally { main { session.release();OverlayService.instance?.cancelTimer();p.edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,old).apply() } }
    }
    private fun renderMatrix() {
        main {
            val layouts=listOf(R.layout.page_home,R.layout.page_stats,R.layout.page_settings,R.layout.widget_compact,R.layout.widget_main,R.layout.widget_large)
            val proof=File(ctx.cacheDir,"upgrade-uat").apply { mkdirs() }
            for(language in listOf("zh","en","de","es","fr","ja","ko","ru")) {
                val sheet=Bitmap.createBitmap(960,1440,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(sheet);canvas.drawColor(android.graphics.Color.GRAY)
                val paint=Paint().apply { color=android.graphics.Color.WHITE;textSize=12f }
                var index=0
                for(night in listOf(false,true)) for(shape in 0..2) {
                    val config=Configuration(ctx.resources.configuration).apply {
                        setLocale(Locale(language));fontScale=when(shape){0->1f;1->1.5f;else->1.3f}
                        uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(night)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    val themed=ContextThemeWrapper(ctx.createConfigurationContext(config),R.style.Theme_XiTing)
                    val density=themed.resources.displayMetrics.density
                    val width=((if(shape==2)640 else if(shape==1)320 else 390)*density).toInt()
                    val height=((if(shape==2)320 else 760)*density).toInt()
                    for(layout in layouts) {
                        val v=LayoutInflater.from(themed).inflate(layout,null)
                        val style=when(layout) { R.layout.widget_main->WidgetData.Style.STANDARD;R.layout.widget_large->WidgetData.Style.LARGE;R.layout.widget_compact->WidgetData.Style.COMPACT;else->null }
                        if(style!=null)WidgetData.buildViews(themed,style,themed).reapply(themed,v)
                        v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,width,height)
                        check(v.measuredWidth==width && v.measuredHeight==height)
                        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap))
                        val x=(index%6)*160f;val y=(index/6)*240f
                        canvas.drawBitmap(bitmap,null,android.graphics.RectF(x,y+20,x+160,y+240),null)
                        canvas.drawText("$language ${if(night)"dark" else "light"} $shape ${ctx.resources.getResourceEntryName(layout)}",x+2,y+14,paint)
                        bitmap.recycle();index++
                    }
                }
                File(proof,"matrix-$language.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG,100,it) };sheet.recycle()
            }
        }
    }
}
