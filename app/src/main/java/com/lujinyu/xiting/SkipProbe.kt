// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/trace.json
package com.lujinyu.xiting

/**
 * 黑幕切集键的判定。
 *
 * 背景：`PlaybackState.actions` 只是**声明**。不少播放器（尤其短视频 App）在
 * `onMediaButtonEvent` 里响应上一集/下一集，却不写 `ACTION_SKIP_TO_*` 位——
 * 只看能力位会把它们误判成"不支持"，于是键凭空消失（2026-10-29 用户报障）。
 * 反过来只看声明又会出现"点了没反应"的假按钮（v5.1.1 修过的那个缺陷）。
 *
 * 折中：键常驻显示；没声明的方向先注入媒体键**赌一次**，用内容签名判定有没有生效，
 * 没生效就判死、转灰不再可点。判死只对"这一条内容"负责，换内容或超时即复活。
 */
internal enum class SkipVerdict { WORKED, DEAD, INCONCLUSIVE }

/** 按下后该走哪条路。 */
internal enum class SkipRoute { TRANSPORT, INJECT, NOTHING }

/** 纯判定，不碰 Android API，便于单测。 */
internal object SkipProbePolicy {
    const val DIRECTION_PREVIOUS = 0
    const val DIRECTION_NEXT = 1

    /**
     * 内容签名：标题与 media id 至少有一个才可比。
     * 都不给（抖音 feed 常这样）就返回 null——**取不到证据不等于不支持**，
     * 这种方向保持可点，不做判死，避免把"我们读不到"说成"它不行"。
     */
    fun signature(title:String?, mediaId:String?):String? = when {
        !title.isNullOrBlank() && !mediaId.isNullOrBlank() -> "$title|$mediaId"
        !title.isNullOrBlank() -> title
        !mediaId.isNullOrBlank() -> mediaId
        else -> null
    }

    /** 判死优先于声明：判过不响就不再动它，哪怕这次会话补上了能力位也先按死处理。 */
    fun route(declared:Boolean, dead:Boolean):SkipRoute = when {
        dead -> SkipRoute.NOTHING
        declared -> SkipRoute.TRANSPORT
        else -> SkipRoute.INJECT
    }

    /** 只有前后都取到可比签名且完全相同，才允许下"不支持"的结论。 */
    fun verdict(before:String?, after:String?):SkipVerdict = when {
        before==null || after==null -> SkipVerdict.INCONCLUSIVE
        before!=after -> SkipVerdict.WORKED
        else -> SkipVerdict.DEAD
    }
}

/** 进程内的判死表：按「包名+方向」记。不写盘、不进备份——持久化会把改版后的播放器冤枉死。 */
internal object SkipProbe {
    /** 判死只保 10 分钟：同一内容里连按没有意义，但播放器中途支持了还能自己回来。 */
    private const val TTL_MS = 600_000L
    private val deadUntil = HashMap<String,Long>()
    private var signature:String?=null

    /** 内容一变，之前的判死全部作废：上一条不响不代表这一条不响。 */
    fun noteContent(sig:String?) {
        if(sig!=signature) { signature=sig; deadUntil.clear() }
    }

    fun isDead(pkg:String,direction:Int,now:Long):Boolean {
        val key="$pkg:$direction"
        val until=deadUntil[key] ?: return false
        if(until<=now) { deadUntil.remove(key); return false }
        return true
    }

    fun markDead(pkg:String,direction:Int,now:Long) { deadUntil["$pkg:$direction"]=now+TTL_MS }

    /** 测试隔离用；黑幕撤幕不清（同一内容重开不该再赌一次）。 */
    fun reset() { deadUntil.clear(); signature=null }
}
