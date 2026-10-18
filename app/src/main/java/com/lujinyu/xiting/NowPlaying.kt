// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/**
 * 黑幕播控的数据源：当前活跃媒体会话的快照。
 *
 * 与媒体键注入（AudioManager.dispatchMediaKeyEvent，不需要权限）不同，
 * 读取剧名和 seekTo 必须走 MediaSession API，依赖可选的「通知使用权」。
 * 未授权时返回 null。自动跟随播放会话；API 33+ 在实际媒体音频活动期间
 * 也读取系统播控目标，兼容错误上报 STOPPED/NONE 的播放器。
 * 来源可识别不代表进度与暂停可用；不可信状态只显示应用名，不开放控制。
 * 已启动的定时独立持有原始会话，不随来源切换。
 */
data class NowPlaying(
    val title: String?,       // 会话标题（剧名/集名）；缺或空为 null
    val appLabel: String?,    // 播放来源 App 的展示名；读不到时显示本地化「播放器」
    val pkg: String,          // 会话所属包名（字幕采集的前台过滤用）
    val positionMs: Long,     // 当前进度（playbackState.position 已按 lastUpdateTime 折算到当前）
    val durationMs: Long,     // 总时长；读不到为 0
    val canSeek: Boolean,     // 会话是否声明支持 seekTo（±30s 按钮的兼容性探测依据）
    val playing: Boolean,
    internal val controller: android.media.session.MediaController,
    val controlsReliable:Boolean=true
) {
    /**
     * 剧名行的展示文案：「App名 · 剧名」。App 名在前（短、稳定），
     * 剧名在后随宽度截断——整行仍是一行 ellipsize，调用方无需拼串。
     */
    fun displayTitle(): String = when {
        title == null -> appLabel ?: ""
        appLabel == null || appLabel == title -> title
        else -> "$appLabel · $title"
    }
    /** seek 到绝对位置。内部先过 [clampSeekTarget]，不会越出片头片尾。 */
    fun seekTo(targetMs: Long) {
        controller.transportControls.seekTo(clampSeekTarget(targetMs, durationMs))
    }
}

/**
 * seek 目标位置：dur 缺失（≤0）时只保下限；否则夹到 [0, dur]。
 * 连点的累加逻辑在调用方（BlackOverlay.nudgeSeek），这里只管边界。
 */
internal fun clampSeekTarget(posMs: Long, durMs: Long): Long =
    if (durMs <= 0) posMs.coerceAtLeast(0)
    else posMs.coerceIn(0, durMs)

/**
 * 快照当前活跃媒体会话；没有会话或未授予通知使用权时返回 null。
 * 每次重新查询系统信号；黑幕内仅保留同一会话的暂停恢复锚点。
 */
fun Context.queryNowPlaying(): NowPlaying? {
    val lookup=playbackLookup()
    if(lookup.availability!=PlaybackAvailability.AVAILABLE)return null
    val pick=PlaybackSelection.resolve(lookup.sources,lookup.systemToken,lookup.audioActive) ?: return null
    val ps = pick.playbackState ?: return null
    val md = pick.metadata
    val reliable=playbackControlsReliable(ps.state)
    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { reliable && it.isNotBlank() }
    val appLabel = playerLabel(pick.packageName)
    return NowPlaying(
        title = title,
        appLabel = appLabel,
        pkg = pick.packageName,
        positionMs = PlaybackMath.position(ps.position, ps.lastPositionUpdateTime,
            android.os.SystemClock.elapsedRealtime(), ps.playbackSpeed,
            ps.state == PlaybackState.STATE_PLAYING,
            md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L),
        durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        canSeek = reliable && (md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)>0 && ps.actions and PlaybackState.ACTION_SEEK_TO != 0L,
        playing = ps.state == PlaybackState.STATE_PLAYING || (!reliable && lookup.audioActive && pick.sessionToken==lookup.systemToken),
        controller = pick,
        controlsReliable = reliable
    )
}
