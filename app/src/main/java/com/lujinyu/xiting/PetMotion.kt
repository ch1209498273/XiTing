package com.lujinyu.xiting

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

internal enum class PetAction { NONE, IGNITE, BOUNCE, CHARGE, LIGHTNING, SPIN, WAVE, WINGS }
internal enum class PetTarget { NONE, HELP, ENERGY, PET }

/** Pure gesture/animation rules. Coordinates are dp, time is monotonic milliseconds. */
internal object PetMotion {
    data class Frame(val scaleX: Float=1f, val scaleY: Float=1f,
        val lift: Float=0f, val tilt: Float=0f, val effect: Float=0f)
    private val idle=Frame()
    fun target(help: Boolean, energy: Boolean, body: Boolean): PetTarget = when {
        help -> PetTarget.HELP
        energy -> PetTarget.ENERGY
        body -> PetTarget.PET
        else -> PetTarget.NONE
    }
    fun action(stage: Int, heldMs: Long, dx: Float, dy: Float): PetAction {
        if (abs(dy)>18f || abs(dx)>18f) {
            return if(stage==3 && abs(dx)>32f && abs(dx)>abs(dy)*1.5f) PetAction.SPIN else PetAction.NONE
        }
        return when(stage) {
            0 -> PetAction.IGNITE
            1 -> if(heldMs>=350) PetAction.CHARGE else PetAction.BOUNCE
            2 -> PetAction.LIGHTNING
            3 -> PetAction.SPIN
            else -> if(heldMs>=500) PetAction.WINGS else PetAction.WAVE
        }
    }
    fun duration(action: PetAction): Long = when(action) {
        PetAction.NONE -> 0L
        PetAction.IGNITE -> 850L
        PetAction.BOUNCE -> 1000L
        PetAction.CHARGE -> 1400L
        PetAction.LIGHTNING -> 1050L
        PetAction.SPIN -> 1500L
        PetAction.WAVE -> 1100L
        PetAction.WINGS -> 1700L
    }
    fun frame(action: PetAction, elapsed: Long, strength: Float): Frame {
        val duration=duration(action)
        if(elapsed<0 || elapsed>=duration || duration==0L) return idle
        val u=elapsed.toFloat()/duration
        val power=strength.coerceIn(.3f,1f)
        val envelope=sin(Math.PI.toFloat()*u).coerceIn(0f,1f)
        val bounce=sin(u*15f)*exp(-u*4f)*power
        return when(action) {
            PetAction.IGNITE -> Frame(1f-.05f*envelope,1f+.10f*envelope,5f*envelope,0f,envelope)
            PetAction.BOUNCE,PetAction.CHARGE -> Frame(1f-.08f*bounce,1f+.12f*bounce,
                (if(action==PetAction.CHARGE) 24f else 12f)*envelope*power,0f,envelope)
            PetAction.LIGHTNING -> Frame(1f+.035f*bounce,1f-.035f*bounce,2f*envelope,3f*bounce,envelope)
            PetAction.SPIN -> Frame(1f+.04f*envelope,1f-.025f*envelope,4f*envelope,12f*sin(u*10f)*envelope,envelope)
            PetAction.WAVE,PetAction.WINGS -> Frame(1f+.025f*envelope,1f+.025f*envelope,
                6f*envelope,5f*sin(u*8f)*envelope,envelope)
            else -> idle
        }
    }
}
