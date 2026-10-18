package com.lujinyu.xiting

import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper

/** Memory-only explicit target. Inactive is not destroyed; lost selections never select another app. */
internal object PlaybackSelection {
    private var curtainActive=false
    private var autoController:MediaController?=null
    private var autoDestroyed=false
    private var autoCallback:MediaController.Callback?=null
    fun beginAutomaticCurtain() { endAutomaticCurtain();curtainActive=true }
    fun endAutomaticCurtain() {
        autoCallback?.let { runCatching { autoController?.unregisterCallback(it) } }
        autoController=null;autoCallback=null;autoDestroyed=false;curtainActive=false
    }
    private fun automatic(active:List<MediaController>,systemToken:MediaSession.Token?,audioActive:Boolean):MediaController? {
        val all=active.filterNot { autoDestroyed && it.sessionToken==autoController?.sessionToken }.toMutableList()
        val previous=autoController?.takeIf { curtainActive && !autoDestroyed }
        if(previous!=null && all.none { it.sessionToken==previous.sessionToken })all.add(previous)
        val curtainId=previous?.let { c -> all.indexOfFirst { it.sessionToken==c.sessionToken }.toString() }
        val systemId=all.indexOfFirst { it.sessionToken==systemToken }.takeIf { it>=0 }?.toString()
        val id=chooseCurrentPlayback(all.mapIndexed { index,c -> CurrentPlaybackCandidate(index.toString(),c.playbackState?.state ?: 0) },curtainId,systemId,audioActive)
        val chosen=id?.toIntOrNull()?.let { all.getOrNull(it) }
        if(curtainActive && chosen!=null && chosen.sessionToken!=autoController?.sessionToken) {
            autoCallback?.let { runCatching { autoController?.unregisterCallback(it) } }
            autoController=chosen;autoDestroyed=false
            val observer=object:MediaController.Callback() { override fun onSessionDestroyed() { if(autoController===chosen)autoDestroyed=true } }
            autoCallback=observer
            try { chosen.registerCallback(observer,Handler(Looper.getMainLooper())) } catch(_:RuntimeException) { autoDestroyed=true;return null }
        }
        return chosen
    }
    private var retained: MediaController? = null
    private var destroyed = false
    private var callback: MediaController.Callback? = null
    private var preferredPackage:String?=null
    var revision=0L;private set
    val packageName:String? get()=preferredPackage
    val hasSelection:Boolean get()=token!=null || preferredPackage!=null
    var token: MediaSession.Token? = null
        set(value) {
            if (field != value) {
                revision++
                releaseController()
                destroyed = false
                preferredPackage=null
            }
            field = value
        }
    fun select(controller: MediaController) {
        if (token == controller.sessionToken && retained != null && !destroyed) return
        revision++
        releaseController()
        token = controller.sessionToken
        preferredPackage=controller.packageName
        retained = controller
        destroyed = false
        val observing = object : MediaController.Callback() {
            override fun onSessionDestroyed() { if(retained === controller) destroyed = true }
        }
        callback = observing
        try { controller.registerCallback(observing, Handler(Looper.getMainLooper())) }
        catch (_: RuntimeException) { destroyed = true }
    }
    fun resolve(active: List<MediaController>,systemToken:MediaSession.Token?=null,audioActive:Boolean=false): MediaController? {
        val chosen = token ?: return if(preferredPackage==null)automatic(active,systemToken,audioActive)
            else active.firstOrNull { it.packageName==preferredPackage }?.also { select(it) }
        if (destroyed) return null
        val live = active.firstOrNull { it.sessionToken == chosen }
        if (live != null) { select(live); return live }
        return retained?.takeIf { it.sessionToken == chosen }?.takeIf {
            try { it.playbackState != null } catch (_: RuntimeException) { false }
        }
    }
    fun cached(): MediaController? = resolve(emptyList())
    fun selectApp(pkg:String,controller:MediaController?=null) {
        require(pkg.isNotBlank())
        revision++
        if(controller!=null) { require(controller.packageName==pkg);select(controller) }
        else { clear();preferredPackage=pkg }
    }
    fun clear() { endAutomaticCurtain();revision++;token = null; releaseController(); destroyed = false;preferredPackage=null }
    private fun releaseController() {
        callback?.let { try { retained?.unregisterCallback(it) } catch (_: RuntimeException) { } }
        callback = null
        retained = null
    }
}
