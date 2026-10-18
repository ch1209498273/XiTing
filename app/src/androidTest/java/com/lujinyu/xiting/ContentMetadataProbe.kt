package com.lujinyu.xiting

import android.app.Instrumentation
import android.media.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

/** Read-only public-content probe; no synthetic sessions or user state mutations. */
internal object ContentMetadataProbe {
    fun run(i: Instrumentation) {
        val ctx=i.targetContext
        val lookup=ctx.playbackLookup()
        val rows=JSONArray()
        for (source in lookup.sources.filter { it.packageName in listOf("tv.danmaku.bili","com.qiyi.video","com.ss.android.ugc.aweme.mobile") }) {
            val md=source.metadata;val ps=source.playbackState
            val id=md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            val uri=md?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
            rows.put(JSONObject().put("package",source.packageName).put("state",ps?.state ?: -1)
                .put("title",md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: JSONObject.NULL)
                .put("positionMs",ps?.position ?: -1).put("durationMs",md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0)
                .put("hasMediaId",!id.isNullOrBlank()).put("hasHttpMediaUri",uri?.let { it.startsWith("https://")||it.startsWith("http://") } ?: false))
        }
        ctx.cacheDir.resolve("content-return-metadata.json").writeText(JSONObject()
            .put("availability",lookup.availability.name).put("sources",rows).toString())
    }
}
