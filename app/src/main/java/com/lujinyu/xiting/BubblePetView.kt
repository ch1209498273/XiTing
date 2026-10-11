package com.lujinyu.xiting

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/** Compact shared character. OverlayService retains ownership of click/drag and black-curtain pause. */
class BubblePetView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    companion object {
        const val STAGE_SPARK=0;const val STAGE_BALL=1;const val STAGE_CLOUD=2
        const val STAGE_STORM=3;const val STAGE_KING=4
        private const val FRAME_MS=100L
    }
    var stage=STAGE_BALL
        set(value) { field=value.coerceIn(0,4);invalidate() }
    var skinHue=0f
        set(value) { field=value;invalidate() }
    var skinSat=1f
        set(value) { field=value;invalidate() }
    fun applySkin(skin:PetSkins.Skin) { skinHue=skin.hue;skinSat=skin.saturation }
    private val renderer=PetRenderer()
    private val bornAt=SystemClock.uptimeMillis()
    private var animating=true
    private val visibleRect=Rect()
    private val scrollListener=android.view.ViewTreeObserver.OnScrollChangedListener { schedule() }
    private fun running()=animating && isAttachedToWindow && isShown &&
        windowVisibility==VISIBLE && ValueAnimator.areAnimatorsEnabled() && getLocalVisibleRect(visibleRect)
    private val tick=object:Runnable {
        override fun run() { if(running()) { invalidate();postDelayed(this,FRAME_MS) } }
    }
    private fun schedule() { removeCallbacks(tick);if(running())post(tick);invalidate() }
    fun setAnimating(on:Boolean) { animating=on;schedule() }
    init {
        background=android.graphics.drawable.GradientDrawable().apply {
            shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(0xB3000000.toInt())
        }
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val size=(48*resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(size,widthMeasureSpec),resolveSize(size,heightMeasureSpec))
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();viewTreeObserver.addOnScrollChangedListener(scrollListener);schedule() }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        super.onLayout(changed,left,top,right,bottom);schedule()
    }
    override fun onDetachedFromWindow() { removeCallbacks(tick);if(viewTreeObserver.isAlive)viewTreeObserver.removeOnScrollChangedListener(scrollListener);super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView:View,visibility:Int) { super.onVisibilityChanged(changedView,visibility);schedule() }
    override fun onWindowVisibilityChanged(visibility:Int) { super.onWindowVisibilityChanged(visibility);schedule() }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val time=(SystemClock.uptimeMillis()-bornAt)/1000f
        // Lite mode retains the same silhouette/face, but omits orbiting particles.
        renderer.draw(canvas,stage,width/2f,height*.46f,minOf(width,height)*.34f,
            time,skinHue,skinSat,static=!running(),lite=true)
    }
}
