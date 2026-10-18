package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.util.Locale

/** Attached windows and real click/scroll paths; not just inflate/measure checks. */
internal class ListeningPanelChecks(private val i:Instrumentation) {
    private fun main(action:()->Unit) { var failure:Throwable?=null;i.runOnMainSync { try { action() }catch(t:Throwable){failure=t} };failure?.let { throw it } }
    fun run() {
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        for(lang in listOf("zh","en","ja","ko","de","fr","es","ru"))for(dark in listOf(false,true)) {
            val config=Configuration(i.targetContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(lang));uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if(dark)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val ctx=ContextThemeWrapper(i.targetContext.createConfigurationContext(config),R.style.Theme_XiTing)
            lateinit var overlay:BlackOverlay
            lateinit var root:View
            main {
                overlay=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false)
                overlay.show { }
                root=BlackOverlay::class.java.getDeclaredField("frame").apply { isAccessible=true }.get(overlay) as View
                val time=SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                    val event=MotionEvent.obtain(time,time+20,action,100f,100f,0);root.dispatchTouchEvent(event);event.recycle()
                }
            }
            try {
                SystemClock.sleep(400);i.waitForIdleSync()
                main { check(overlay.isAwake);root.findViewWithTag<View>("flow_timer_setup").performClick() }
                i.waitForIdleSync()
                fun visible(tag:String):View {
                    lateinit var view:View
                    main { view=root.findViewWithTag<View>(tag);view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true) }
                    i.waitForIdleSync()
                    main {
                        val rect=Rect();check(view.isShown && view.getGlobalVisibleRect(rect) && rect.height()==view.height && view.isEnabled) {
                            "$lang/$dark/$tag clipped: visible=$rect height=${view.height}"
                        }
                    }
                    return view
                }
                for(tag in listOf("flow_preset_15","flow_preset_30","flow_preset_60","flow_preset_custom"))visible(tag)
                main { visiblePlaceholder(root,"flow_preset_custom").performClick() }
                i.waitForIdleSync();visible("flow_step_-1");visible("flow_step_1")
                visible("flow_action_return");visible("flow_timer_confirm")
                main {
                    val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
                    File(i.targetContext.cacheDir,"flow-panels/panel-$lang-${if(dark)"dark" else "light"}.png").apply { parentFile!!.mkdirs() }
                        .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                }
                val cancel=visible("flow_timer_close")
                main { cancel.performClick();check(root.findViewWithTag<View>("flow_timer_editor")==null);check(overlay.isAwake) }
            } finally { main { overlay.hide() } }
        }
    }
    private fun visiblePlaceholder(root:View,tag:String)=root.findViewWithTag<View>(tag)
}
