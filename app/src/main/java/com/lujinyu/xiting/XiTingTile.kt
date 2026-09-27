package com.lujinyu.xiting

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * 下拉快捷设置磁贴：
 * - 助手未运行时点击：启动服务并直接进入黑幕（一键听剧）
 * - 运行中点击：黑幕/恢复 切换
 */
class XiTingTile : TileService() {

    companion object {
        private const val TAG = "XiTing"
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        Log.d(TAG, "tile clicked, running=${OverlayService.isRunning}")
        if (!OverlayService.isRunning) {
            startForegroundService(Intent(this, OverlayService::class.java))
            main.postDelayed({
                OverlayService.instance?.toggleOverlay()
                refresh()
            }, 800)
        } else {
            OverlayService.instance?.toggleOverlay()
            main.postDelayed({ refresh() }, 300)
        }
    }

    private fun refresh() {
        val active = OverlayService.instance?.isAnyBlackShowing() == true
        qsTile?.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }
}
