package com.lujinyu.sessiontest

import android.app.Activity
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * UAT测试桩：
 * 1. 模拟视频App——MediaPlayer循环播放 + 活跃MediaSession（响应PLAY/PAUSE媒体键）
 * 2. 模拟广告——"跳过广告"按钮：被息屏听剧的无障碍服务自动点击后隐藏，计数器留证
 */
class MainActivity : Activity() {

    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var playBtn: Button? = null
    private var adBtn: Button? = null
    private lateinit var counterText: TextView
    private var skipCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 120, 60, 60)
        }
        counterText = TextView(this).apply { text = "广告自动跳过计数: 0" }
        layout.addView(counterText)

        val b = Button(this).apply { text = "开始播放（带MediaSession）" }
        layout.addView(b)
        playBtn = b

        val ad = Button(this).apply { text = "跳过广告 3" }
        layout.addView(ad)
        adBtn = ad

        val reset = Button(this).apply { text = "重置演示（重新显示广告按钮）" }
        layout.addView(reset)

        setContentView(layout)

        val p = MediaPlayer.create(this, R.raw.tone)
        p.isLooping = true
        player = p

        val ms = MediaSession(this, "SessionTest")
        ms.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        ms.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                startPlay()
            }

            override fun onPause() {
                stopPlay()
            }
        })
        ms.isActive = true
        session = ms

        b.setOnClickListener {
            if (player?.isPlaying == true) stopPlay() else startPlay()
        }

        // 模拟广告：被无障碍自动点击后按钮消失 + 计数+1（Toast防不了截图，计数器是持久证据）
        ad.setOnClickListener {
            skipCount++
            counterText.text = "广告自动跳过计数: $skipCount"
            it.visibility = View.GONE
            Toast.makeText(this, "广告按钮被自动点击 ✓", Toast.LENGTH_SHORT).show()
        }
        reset.setOnClickListener { adBtn?.visibility = View.VISIBLE }
    }

    private fun startPlay() {
        player?.start()
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                .build()
        )
        playBtn?.text = "正在播放（点我暂停）"
        Toast.makeText(this, "SessionTest: PLAYING", Toast.LENGTH_SHORT).show()
    }

    private fun stopPlay() {
        player?.pause()
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 0, 1f)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                .build()
        )
        playBtn?.text = "已暂停（点我播放）"
    }

    /** 模拟真实视频App行为：息屏→Activity onStop→暂停播放（这正是息屏听剧要解决的痛点） */
    override fun onStop() {
        super.onStop()
        stopPlay()
    }

    override fun onDestroy() {
        player?.release()
        session?.release()
        super.onDestroy()
    }
}
