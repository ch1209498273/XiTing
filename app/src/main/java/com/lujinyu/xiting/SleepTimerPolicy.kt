package com.lujinyu.xiting

enum class PauseDecision { REQUEST,ALREADY_PAUSED,UNAVAILABLE }

object SleepTimerPolicy {
    // State-aware entry point; unknown states need their own regression cases.
    fun pauseDecision(available:Boolean,state:Int,supportsPause:Boolean):PauseDecision = when {
        !available -> PauseDecision.UNAVAILABLE
        state==2 -> PauseDecision.ALREADY_PAUSED
        state !in listOf(3,6,8) || !supportsPause -> PauseDecision.UNAVAILABLE
        else -> PauseDecision.REQUEST
    }
    const val MAX_DURATION_MS=600L*60_000
    fun deadline(minutes:Long,endAt:Long,wallNow:Long,elapsedNow:Long):Long {
        val duration=when {
            endAt>0 -> if(endAt<=wallNow)0L else (endAt-wallNow).coerceAtMost(MAX_DURATION_MS)
            minutes>0 -> minutes.coerceAtMost(600)*60_000
            else -> 0L
        }
        return if(duration<=0)0 else elapsedNow+duration
    }
}
