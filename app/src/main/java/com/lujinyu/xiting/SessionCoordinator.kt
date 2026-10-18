package com.lujinyu.xiting

enum class AssistantPhase { IDLE, STARTING, RUNNING, STOPPING, INTERRUPTED }
enum class CurtainPhase { OFF, LOCKED, AWAKE }
enum class PlaybackReport { UNKNOWN, PLAYING, PAUSED, BUFFERING, INVALID }
enum class TimerPhase { NONE, FIXED, EPISODE, WAITING, EXECUTING, ENDED }
enum class PauseReport { NONE, REQUESTED, CONFIRMED, ALREADY_PAUSED, UNAVAILABLE, FAILED, UNCONFIRMED }
enum class SessionEndReason { USER, TIMER, INTERRUPTED, UNKNOWN }

data class SessionSnapshot(
    val id:String="", val assistant:AssistantPhase=AssistantPhase.IDLE,
    val curtain:CurtainPhase=CurtainPhase.OFF,val playback:PlaybackReport=PlaybackReport.UNKNOWN,
    val timer:TimerPhase=TimerPhase.NONE,val remainingMs:Long=0,
    val pauseKeepBlack:Boolean=false,val scene:String="continuous",val pause:PauseReport=PauseReport.NONE
)
internal data class SessionCompletion(val id:String,val startedAt:Long,val endedAt:Long?,val scene:String,val reason:String,val pause:String)

/** Serial, Android-independent state authority. Playback and curtain are separate facts. */
internal class SessionCoordinator {
    var snapshot=SessionSnapshot(); private set
    private var startedAt=0L
    private var finished=true
    @Synchronized fun begin(id:String,now:Long,scene:String) {
        if(!finished)return
        check(id.isNotBlank() && now>=0)
        finished=false;startedAt=now
        snapshot=snapshot.copy(id=id,scene=scene.takeIf { it in SCENES } ?: "continuous",pause=PauseReport.NONE)
    }
    @Synchronized fun update(assistant:AssistantPhase,curtain:CurtainPhase,playback:PlaybackReport,
        timer:TimerPhase,remainingMs:Long,pauseKeepBlack:Boolean) {
        snapshot=snapshot.copy(assistant=assistant,curtain=curtain,playback=playback,timer=timer,
            remainingMs=if(timer==TimerPhase.NONE || timer==TimerPhase.ENDED)0 else remainingMs.coerceIn(0,SleepTimerPolicy.MAX_DURATION_MS),pauseKeepBlack=pauseKeepBlack)
    }
    @Synchronized fun pause(value:PauseReport) { snapshot=snapshot.copy(pause=value) }
    @Synchronized fun scene(value:String) { if(value in SCENES)snapshot=snapshot.copy(scene=value) }
    @Synchronized fun finish(reason:SessionEndReason,now:Long?):SessionCompletion? {
        if(finished)return null
        finished=true
        return SessionCompletion(snapshot.id,startedAt,now?.takeIf { it>=startedAt },snapshot.scene,
            reason.name.lowercase(java.util.Locale.ROOT),snapshot.pause.name.lowercase(java.util.Locale.ROOT))
    }
    companion object { val SCENES=setOf("sleep","episode","continuous") }
}
