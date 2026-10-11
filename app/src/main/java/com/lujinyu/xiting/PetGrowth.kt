package com.lujinyu.xiting

data class GrowthProgress(val stage:Int,val remaining:Long,val complete:Boolean)

object PetGrowth {
    val thresholds = longArrayOf(0,30,300,1500,6000)
    fun progress(collected:Long):GrowthProgress {
        val gp=collected.coerceAtLeast(0)
        val stage=thresholds.indexOfLast { gp>=it }
        return GrowthProgress(stage,if(stage==thresholds.lastIndex)0 else thresholds[stage+1]-gp,
            stage==thresholds.lastIndex)
    }
}
