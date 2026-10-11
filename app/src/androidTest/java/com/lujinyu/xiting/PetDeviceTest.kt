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
            result.putString("stream","PASS: 4 device checks (gestures, routing, Canvas/skins, animation lifecycle).")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) {
            result.putString("stream","FAIL: "+t.stackTraceToString())
            finish(Activity.RESULT_CANCELED,result)
        }
    }
    private fun assertEquals(expected:Any,actual:Any) { check(expected==actual){"expected=$expected actual=$actual"} }
    private fun assertTrue(value:Boolean) { check(value) }
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
