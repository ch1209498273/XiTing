// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）
package com.lujinyu.xiting

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Shared medal renderer, with a finite visible-only celebration. */
class BadgeView @JvmOverloads constructor(
    context:Context,attrs:AttributeSet?=null,defStyleAttr:Int=0
):View(context,attrs,defStyleAttr) {
    var kind=Badges.Kind.HOURGLASS
        set(value) { field=value;invalidate() }
    var unlocked=false
        set(value) { field=value;if(!value)stopCelebration();invalidate() }
    var heroMode=false
        set(value) { field=value;invalidate() }
    private var celebrationFrom=0L
    private var celebrating=false
    private val effect=Paint(Paint.ANTI_ALIAS_FLAG)
    private val celebrationTick=object:Runnable {
        override fun run() {
            if(!celebrating || !isAttachedToWindow || !isShown || !hasWindowFocus()) { stopCelebration();return }
            if(!BadgeMotion.frame(SystemClock.uptimeMillis()-celebrationFrom,ValueAnimator.areAnimatorsEnabled()).active) {
                stopCelebration();return
            }
            invalidate();postDelayed(this,32)
        }
    }
    fun bind(k:Badges.Kind,u:Boolean,glow:Boolean=false) {
        stopCelebration();kind=k;unlocked=u
        if(glow && u)playCelebration()
    }
    fun playCelebration() {
        stopCelebration()
        if(!unlocked || !ValueAnimator.areAnimatorsEnabled() || !isAttachedToWindow || !isShown || !hasWindowFocus()) return
        celebrationFrom=SystemClock.uptimeMillis();celebrating=true
        invalidate();postDelayed(celebrationTick,32)
    }
    private fun stopCelebration() {
        celebrating=false;removeCallbacks(celebrationTick);invalidate()
    }
    override fun onDetachedFromWindow() { stopCelebration();super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView:View,visibility:Int) {
        super.onVisibilityChanged(changedView,visibility)
        if(visibility!=VISIBLE)stopCelebration()
    }
    override fun onWindowVisibilityChanged(visibility:Int) {
        super.onWindowVisibilityChanged(visibility)
        if(visibility!=VISIBLE)stopCelebration()
    }
    override fun onWindowFocusChanged(hasWindowFocus:Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if(!hasWindowFocus)stopCelebration()
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val r=minOf(width,height)*(if(heroMode).27f else .40f)
        if(r<=1f)return
        val cx=width/2f;val cy=height/2f
        val elapsed=if(celebrating)SystemClock.uptimeMillis()-celebrationFrom else BadgeMotion.DURATION_MS
        val frame=BadgeMotion.frame(elapsed,ValueAnimator.areAnimatorsEnabled())
        if(heroMode && unlocked && frame.burst>.01f) {
            effect.style=Paint.Style.STROKE;effect.strokeWidth=r*.025f
            effect.color=0xFFE5B447.toInt();effect.alpha=(frame.burst*120).toInt()
            canvas.drawCircle(cx,cy,r*(1.2f+elapsed.coerceAtMost(1800)/1800f*.45f),effect)
            effect.style=Paint.Style.FILL
            val travel=(elapsed/1800f).coerceIn(0f,1f)
            for(i in 0 until 12) {
                val a=(i*Math.PI/6+kind.ordinal*.22).toFloat()
                val distance=r*(1.2f+travel*.50f)
                val x=cx+cos(a)*distance;val y=cy+sin(a)*distance
                val size=r*(.025f+(.01f*(i%3)))
                effect.alpha=(frame.burst*220).toInt()
                when(kind) {
                    Badges.Kind.HOURGLASS -> canvas.drawCircle(x,y,size,effect)
                    Badges.Kind.FLAME -> canvas.drawOval(x-size,y-size*2,x+size,y+size*2,effect)
                    Badges.Kind.TARGET -> {
                        effect.style=Paint.Style.STROKE;canvas.drawCircle(x,y,size*1.5f,effect);effect.style=Paint.Style.FILL
                    }
                    Badges.Kind.CROWN -> {
                        canvas.drawRect(x-size*2,y-size*.4f,x+size*2,y+size*.4f,effect)
                        canvas.drawRect(x-size*.4f,y-size*2,x+size*.4f,y+size*2,effect)
                    }
                }
            }
        }
        val save=canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(frame.alpha*255).toInt())
        canvas.scale(frame.scale,frame.scale,cx,cy)
        Badges.draw(canvas,cx,cy,r,kind,unlocked,frame.burst*.5f)
        if(unlocked && frame.shine>.01f) {
            effect.style=Paint.Style.STROKE;effect.strokeWidth=r*.045f
            effect.color=0xFFFFF5D3.toInt();effect.alpha=(frame.shine*200).toInt()
            canvas.drawArc(cx-r*.9f,cy-r*.9f,cx+r*.9f,cy+r*.9f,195f+elapsed/9f,42f,false,effect)
            effect.style=Paint.Style.FILL
        }
        canvas.restoreToCount(save)
    }
}
