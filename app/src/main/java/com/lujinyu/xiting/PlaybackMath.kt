package com.lujinyu.xiting

import kotlin.math.ceil

/** Media positions are content milliseconds; deadlines are elapsed real milliseconds. */
internal object PlaybackMath {
    fun offset(positionMs:Long,deltaMs:Long,durationMs:Long):Long {
        val base=positionMs.coerceAtLeast(0)
        val shifted=if(deltaMs>0 && base>Long.MAX_VALUE-deltaMs)Long.MAX_VALUE else (base+deltaMs).coerceAtLeast(0)
        return if(durationMs>0)shifted.coerceAtMost(durationMs) else shifted
    }
    fun position(positionMs: Long, updateMs: Long, nowMs: Long, speed: Float,
        advancing: Boolean, durationMs: Long): Long {
        val base = positionMs.coerceAtLeast(0)
        val value = if (advancing && updateMs > 0 && nowMs > updateMs && speed.isFinite() && speed > 0f) {
            (base.toDouble() + (nowMs - updateMs).toDouble() * speed).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
        } else base
        return if (durationMs > 0) value.coerceIn(0, durationMs) else value.coerceAtLeast(0)
    }

    fun remainingMs(durationMs: Long, positionMs: Long, speed: Float): Long? {
        if (durationMs <= 0 || !speed.isFinite() || speed <= 0f) return null
        return ceil((durationMs - positionMs.coerceIn(0, durationMs)).toDouble() / speed)
            .coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
    }
}
