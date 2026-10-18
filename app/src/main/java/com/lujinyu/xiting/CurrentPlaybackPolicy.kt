package com.lujinyu.xiting

internal data class CurrentPlaybackCandidate(val id:String,val state:Int)
/** The system's audio-routed session also covers players that report STOPPED while sounding.
 * A paused historical session is never promoted by the global audio flag. */
internal fun chooseCurrentPlayback(sources:List<CurrentPlaybackCandidate>,curtainId:String?=null,
    systemId:String?=null,audioActive:Boolean=false):String? {
    if(audioActive) sources.firstOrNull { it.id==systemId && it.state in listOf(0,1,3,6,8) }?.let { return it.id }
    val playing=sources.filter { it.state==3 }
    if(playing.size==1)return playing.single().id
    if(playing.size>1)return null
    return sources.firstOrNull { it.id==curtainId && it.state==2 }?.id
}

internal fun playbackControlsReliable(state:Int):Boolean = state in listOf(2,3,6,8)
