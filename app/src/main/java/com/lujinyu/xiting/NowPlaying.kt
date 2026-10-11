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
 * **未授权时 [queryNowPlaying] 返回 null，调用方整体降级**——
 * 剧名不显示、±30s 按钮隐藏，但媒体键照常可用（与通知角标同一套可选契约）。
 *
 * 选会话的口径：优先**正在播放**的；多个在播或全都不播时取播放状态更新最新的
 * （= 当前活跃的那个）。这与 MainActivity.finishThisEpisode 的「剩余最短」口径
 * 不同（那边服务定时，这边服务展示/seek），不要合并。
 */
data class NowPlaying(
    val title: String?,       // 会话标题（剧名/集名）；缺或空为 null
    val positionMs: Long,     // 当前进度（playbackState.position 已按 lastUpdateTime 折算到当前）
    val durationMs: Long,     // 总时长；读不到为 0
    val canSeek: Boolean,     // 会话是否声明支持 seekTo（±30s 按钮的兼容性探测依据）
    val playing: Boolean,
    private val controller: android.media.session.MediaController
) {
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
 * 每次 [queryNowPlaying] 都重新查——MediaController 会随会话更替失效，不缓存。
 */
fun Context.queryNowPlaying(): NowPlaying? {
    val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        ?: return null
    val sessions = try {
        mgr.getActiveSessions(ComponentName(this, NotificationListener::class.java))
    } catch (_: SecurityException) {
        return null // 未授予通知使用权：整体降级，媒体键注入不受影响
    }
    val pick = sessions.orEmpty()
        .filter { it.playbackState != null }
        .maxByOrNull { c ->
            val ps = c.playbackState!!
            // 在播的排在前面；同组内取播放状态最新的（lastPositionUpdateTime 是
            // uptime ms，远小于 2^62，拼在标志位后面不串号）
            (if (ps.state == PlaybackState.STATE_PLAYING) 1L shl 62 else 0L) +
                ps.lastPositionUpdateTime
        } ?: return null
    val ps = pick.playbackState ?: return null
    val md = pick.metadata
    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
    return NowPlaying(
        title = title,
        positionMs = ps.position.coerceAtLeast(0),
        durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        canSeek = ps.actions and PlaybackState.ACTION_SEEK_TO != 0L,
        playing = ps.state == PlaybackState.STATE_PLAYING,
        controller = pick
    )
}
