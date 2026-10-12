package com.lujinyu.xiting

import kotlin.math.sin

/** A finite celebration, independent of wall-clock changes. */
object BadgeMotion {
    const val DURATION_MS=1800L
    data class Frame(val scale:Float,val alpha:Float,val burst:Float,val shine:Float,val active:Boolean)
    fun frame(elapsed:Long,enabled:Boolean):Frame {
        if(!enabled || elapsed>=DURATION_MS) return Frame(1f,1f,0f,0f,false)
        val t=elapsed.coerceAtLeast(0).toFloat()
        val pop=(t/600f).coerceIn(0f,1f)
        val scale=if(pop<.6f) .55f+.57f*(1f-(1f-pop/.6f)*(1f-pop/.6f))
            else 1f+.12f*(1f-(pop-.6f)/.4f)
        val burst=sin(Math.PI*((t-180f)/1400f).coerceIn(0f,1f)).toFloat().coerceIn(0f,1f)
        val shine=sin(Math.PI*((t-450f)/950f).coerceIn(0f,1f)).toFloat().coerceIn(0f,1f)
        return Frame(scale,(t/180f).coerceIn(0f,1f),burst,shine,true)
    }
}
