package com.lujinyu.xiting

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import java.io.File

/** Run on the emulator with landscape/dense font system settings; saves app-only previews. */
internal class IncrementDialogChecks(private val i:Instrumentation) {
    private fun main(block:()->Unit) { var error:Throwable?=null;i.runOnMainSync { try { block() } catch(t:Throwable){error=t} };error?.let { throw it } }
    fun run() {
        val ctx=i.targetContext;val before=ctx.prefs().getString(Prefs.LANG,null)
        val mode=AppearanceSettings.current(ctx);val tab=ctx.prefs().getInt(Prefs.LAST_TAB,0)
        try {
            for(lang in listOf("zh-CN","en","ja","ko","de","fr","es","ru")) for(theme in listOf("light","dark")) {
                ctx.prefs().edit().putString(Prefs.LANG,lang).commit();AppearanceSettings.set(ctx,theme)
                val activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                try {
                    i.waitForIdleSync()
                    for(kind in listOf("backup","history","diagnostics","first-success","appearance","presets")) {
                        lateinit var dialog:AlertDialog
                        main { dialog=when(kind) {
                            "backup"->BackupManagement.show(activity) {}
                            "history"->HistoryManagement(activity) {}.show()
                            "diagnostics"->SupportDiagnostics.show(activity)
                            "first-success"->FirstSuccessFlow.confirm(activity)
                            "appearance"->activity.showAppearanceSettings()
                            else ->activity.showListeningPresets()
                        } }
                        i.waitForIdleSync()
                        main {
                            val root=dialog.window!!.decorView
                            check(root.width>0 && root.height>0)
                            if(kind=="backup" || kind=="history") {
                                fun scroll(v:View):ScrollView?=if(v is ScrollView && v.isShown)v else if(v is ViewGroup)
                                    (0 until v.childCount).firstNotNullOfOrNull { scroll(v.getChildAt(it)) } else null
                                val container=scroll(root) ?: error("No scrolling path for $kind")
                                container.isSmoothScrollingEnabled=false
                                container.fullScroll(View.FOCUS_DOWN)
                                check(container.height>0 && container.getChildAt(0).height>0) { "$lang/$theme/$kind has no visible scrolling content" }
                                fun actions(v:View):List<View> = (if(v.isClickable)listOf(v) else emptyList())+
                                    (if(v is ViewGroup)(0 until v.childCount).flatMap { actions(v.getChildAt(it)) } else emptyList())
                                val last=actions(container.getChildAt(0)).lastOrNull() ?: error("No actions in $kind")
                                last.requestRectangleOnScreen(android.graphics.Rect(0,0,last.width,last.height),true)
                                val visible=android.graphics.Rect()
                                check(last.getGlobalVisibleRect(visible) && visible.height()>=minOf(last.height,container.height)) {
                                    "$lang/$theme/$kind last action is clipped after scrolling"
                                }
                            }
                            check(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { it.visibility!=View.VISIBLE || it.width>0 } ?: true)
                            val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
                            root.draw(Canvas(bitmap))
                            File(ctx.cacheDir,"increment-$lang-$theme-$kind.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                            bitmap.recycle();dialog.dismiss()
                        }
                        i.waitForIdleSync()
                    }
                } finally { main { activity.finish() };i.waitForIdleSync() }
            }
        } finally {
            ctx.prefs().edit().putString(Prefs.LANG,before).putInt(Prefs.LAST_TAB,tab).commit()
            AppearanceSettings.set(ctx,mode)
        }
    }
}
