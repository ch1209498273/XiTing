package com.lujinyu.xiting

/** Owns the timer's original media targets and closes them as a unit. Deadlines are monotonic. */
internal class TimerController {
    @Volatile var deadline=0L
    var pauseKeepBlack=false
    var target:TimerPlaybackTarget?=null
    var episode:EpisodeTimerTarget?=null
    var waiting=false
    var remaining=0L
    var graceAt=0L
    var result=0
    fun closeEpisode() { episode?.close();episode=null;waiting=false;graceAt=0 }
    fun cancel() { closeEpisode();deadline=0;target?.close();target=null;result=0 }
}
