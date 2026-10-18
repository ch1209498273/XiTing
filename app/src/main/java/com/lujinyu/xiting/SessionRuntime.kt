package com.lujinyu.xiting

import android.content.Context
import android.media.session.PlaybackState
import org.json.JSONObject

/** Shared immutable projection. Persistent checkpoint contains no media identity or token. */
internal object SessionRuntime {
    private const val CHECKPOINT="session_checkpoint_v1"
    private const val SCENE="listening_scene_v1"
    private var coordinator=SessionCoordinator()
    @Volatile private var current=SessionSnapshot()
    fun snapshot():SessionSnapshot=current
    @Synchronized fun selectScene(ctx:Context,scene:String) {
        if(scene !in SessionCoordinator.SCENES)return
        ctx.prefs().edit().putString(SCENE,scene).apply();coordinator.scene(scene)
        ctx.prefs().getString(CHECKPOINT,null)?.let { saved ->
            try { ctx.prefs().edit().putString(CHECKPOINT,JSONObject(saved).put("scene",scene).toString()).commit() }
            catch(_:Exception) { }
        }
        current=coordinator.snapshot
    }
    @Synchronized fun recover(ctx:Context) {
        if(OverlayService.isRunning)return
        // Settle a previous process's checkpoint before creating a new live coordinator.
        val old=ctx.prefs().getString(CHECKPOINT,null)
        if(old!=null)try {
            val o=JSONObject(old)
            SessionOutcomeStore.record(ctx,SessionOutcome(o.getString("id"),o.getLong("start"),null,
                o.getString("scene"),"interrupted","unconfirmed"))
            ctx.prefs().edit().remove(CHECKPOINT).commit()
            current=current.copy(assistant=AssistantPhase.INTERRUPTED,curtain=CurtainPhase.OFF,timer=TimerPhase.NONE,remainingMs=0)
        } catch(_:Exception) { /* Preserve malformed/unwritable checkpoint for diagnosis. */ }
    }
    @Synchronized fun started(ctx:Context) {
        recover(ctx)
        coordinator=SessionCoordinator()
        coordinator.scene(ctx.prefs().getString(SCENE,"continuous") ?: "continuous")
        publish(ctx,coordinator.snapshot.copy(assistant=AssistantPhase.RUNNING))
    }
    @Synchronized fun begin(ctx:Context) {
        if(current.id.isNotEmpty() && ctx.prefs().contains(CHECKPOINT))return
        val now=System.currentTimeMillis()
        coordinator.begin(java.util.UUID.randomUUID().toString(),now,current.scene)
        val s=coordinator.snapshot
        val checkpoint=JSONObject().put("id",s.id).put("start",now).put("scene",s.scene)
        ctx.prefs().edit().putString(CHECKPOINT,checkpoint.toString()).commit()
        current=s
    }
    @Synchronized fun publish(ctx:Context,snapshot:SessionSnapshot) {
        coordinator.update(snapshot.assistant,snapshot.curtain,snapshot.playback,snapshot.timer,snapshot.remainingMs,snapshot.pauseKeepBlack)
        current=coordinator.snapshot
    }
    @Synchronized fun pause(ctx:Context,report:PauseReport,sessionId:String=current.id) {
        if(sessionId==current.id) { coordinator.pause(report);current=coordinator.snapshot }
        // Update an already-written timer outcome without adding a second row.
        if(sessionId.isNotEmpty() && (sessionId!=current.id || !ctx.prefs().contains(CHECKPOINT))) {
            try { SessionOutcomeStore.list(ctx).firstOrNull { it.id==sessionId }?.let {
                SessionOutcomeStore.record(ctx,it.copy(pause=report.name.lowercase(java.util.Locale.ROOT)))
            } } catch(_:Exception) { }
        }
    }
    @Synchronized fun finish(ctx:Context,reason:SessionEndReason,knownTime:Boolean=true) {
        val result=coordinator.finish(reason,if(knownTime)System.currentTimeMillis() else null) ?: return
        try {
            SessionOutcomeStore.record(ctx,SessionOutcome(result.id,result.startedAt,result.endedAt,result.scene,result.reason,result.pause))
            ctx.prefs().edit().remove(CHECKPOINT).commit()
        } catch(_:Exception) { /* Keep checkpoint; never crash the service or invent a saved result. */ }
        current=coordinator.snapshot
    }
    fun playback(state:PlaybackState?):PlaybackReport=when(state?.state) {
        null->PlaybackReport.UNKNOWN
        PlaybackState.STATE_PLAYING->PlaybackReport.PLAYING
        PlaybackState.STATE_PAUSED->PlaybackReport.PAUSED
        PlaybackState.STATE_BUFFERING,PlaybackState.STATE_CONNECTING->PlaybackReport.BUFFERING
        else->PlaybackReport.INVALID
    }
}
