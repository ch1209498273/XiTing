package com.lujinyu.xiting

internal data class WakeLockSnapshot(val held: Boolean, val heldMs: Long, val totalMs: Long)

/** Elapsed-time accounting for one process; this does not measure battery drain. */
internal class WakeLockMeter(private val timeoutMs:Long=Long.MAX_VALUE) {
    private var acquiredAt: Long? = null
    private var completedMs = 0L

    @Synchronized fun acquired(nowElapsed: Long) {
        expire(nowElapsed)
        if (acquiredAt == null) acquiredAt = nowElapsed.coerceAtLeast(0)
    }

    @Synchronized fun released(nowElapsed: Long) {
        expire(nowElapsed)
        val start = acquiredAt ?: return
        completedMs = addSaturated(completedMs, (nowElapsed.coerceAtLeast(0) - start).coerceAtLeast(0))
        acquiredAt = null
    }

    @Synchronized fun snapshot(nowElapsed: Long): WakeLockSnapshot {
        expire(nowElapsed)
        val duration = acquiredAt?.let { (nowElapsed.coerceAtLeast(0) - it).coerceAtLeast(0) } ?: 0
        return WakeLockSnapshot(acquiredAt != null, duration, addSaturated(completedMs, duration))
    }

    private fun addSaturated(a: Long, b: Long): Long = if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
    private fun expire(now:Long) {
        val start=acquiredAt ?: return
        if(now>=start && now-start>=timeoutMs) {
            completedMs=addSaturated(completedMs,timeoutMs);acquiredAt=null
        }
    }
}

/** Service hooks must follow the actual successful acquire/release of the non-reference-counted lock. */
internal object WakeLockMetrics {
    private val meter = WakeLockMeter(OverlayService.WAKE_LOCK_TIMEOUT_MS)
    fun acquired(nowElapsed: Long) = meter.acquired(nowElapsed)
    fun released(nowElapsed: Long) = meter.released(nowElapsed)
    fun snapshot(nowElapsed: Long): WakeLockSnapshot = meter.snapshot(nowElapsed)
}
