package com.lujinyu.xiting

enum class PauseDecision { REQUEST,ALREADY_PAUSED,UNAVAILABLE }

object SleepTimerPolicy {
    const val MAX_DURATION_MS=600L*60_000
    fun deadline(minutes:Long,endAt:Long,wallNow:Long,elapsedNow:Long):Long {
        val duration=when {
            endAt>0 -> if(endAt<=wallNow)0L else (endAt-wallNow).coerceAtMost(MAX_DURATION_MS)
            minutes>0 -> minutes.coerceAtMost(600)*60_000
            else -> 0L
        }
        return if(duration<=0)0 else elapsedNow+duration
    }
    fun pauseDecision(available:Boolean,playing:Boolean,supportsPause:Boolean):PauseDecision = when {
        !available -> PauseDecision.UNAVAILABLE
        !playing -> PauseDecision.ALREADY_PAUSED
        !supportsPause -> PauseDecision.UNAVAILABLE
        else -> PauseDecision.REQUEST
    }
}
