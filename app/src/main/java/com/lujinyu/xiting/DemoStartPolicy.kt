package com.lujinyu.xiting
internal enum class DemoStartDecision { START, PERMISSION, BUSY, MUTED, PLAYBACK }
internal fun demoStartDecision(overlay:Boolean,busy:Boolean,volume:Int,otherPlaying:Boolean):DemoStartDecision=when {
 !overlay -> DemoStartDecision.PERMISSION
 busy -> DemoStartDecision.BUSY
 volume<=0 -> DemoStartDecision.MUTED
 otherPlaying -> DemoStartDecision.PLAYBACK
 else -> DemoStartDecision.START
}
