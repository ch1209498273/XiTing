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
