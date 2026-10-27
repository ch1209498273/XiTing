package com.lujinyu.xiting.listening

import java.net.URI

data class ParsedContent(val urls:List<String>,val title:String="",val error:String?=null)
object SharedContentParser {
    fun validUrl(value:String):Boolean=try {
        val uri=URI(value)
        value.length<=8192 && uri.scheme?.lowercase() in setOf("http","https") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo==null && !value.any{it.isISOControl()}
    }catch(_:Exception){false}
    fun parse(text:String):ParsedContent {
        if(text.length>65536)return ParsedContent(emptyList(),error="too_long")
        val candidates=Regex("https?://[^\\s<>\"“”‘’《》]+",RegexOption.IGNORE_CASE).findAll(text)
            .map{it.value.trimEnd('.',',',';',')',']','}','，','。','；','！','？')}.toList()
        val urls=candidates.filter(::validUrl).distinct()
        val title=text.lineSequence().firstOrNull{it.isNotBlank()}?.replace(Regex("https?://\\S+"),"")?.trim()?.take(500).orEmpty()
        return ParsedContent(urls,title,if(urls.isEmpty())"no_link" else null)
    }
}
object RetentionPolicy {
    fun expired(endedAt:Long,now:Long,days:Int,protected:Boolean):Boolean=
        !protected && days>0 && endedAt<=now-days.toLong()*86_400_000
}
object ContentIdentityPolicy {
    // Valid only within the current observed recording, never merge historical rows by title.
    fun same(pkg:String,id:String?,otherPkg:String,otherId:String?)=
        pkg==otherPkg && !id.isNullOrBlank() && id==otherId
}
internal object ListeningCapturePolicy {
    /**
     * 「最近听过」的准入与合并键，返回（合并键 → 要写库的快照）。
     * 1) 给了可用标题：有 media id 用 media id，没有就退回标题键；
     *    [ContentIdentityPolicy.same] 另要求同包名，历史条目仍不因标题相同而合并。
     * 2) 没给可用标题（或标题只是 App 自己名字）但确实在播：只记一条「来源」条目，
     *    标题写空，列表按现有口径显示「未识别内容」。抖音、直播一类靠这条不至于全空。
     * 3) 既没可用标题又没在播：不记——挂着暂停会话的 App 不该产出记录。
     */
    fun resolve(s:ContentSnapshot):Pair<String,ContentSnapshot>? {
        if(s.pkg.isBlank())return null
        val t=s.title?.takeIf{it.isNotBlank()}
        if(t!=null && (s.identity!=null || t!=s.app))
            return (s.identity?.takeIf{it.isNotBlank()} ?: "\u0000t$t") to s
        return if(s.playing)"\u0000p${s.pkg}" to s.copy(title=null) else null
    }
}
