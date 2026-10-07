package com.lujinyu.xiting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** 近7天息屏时长柱状图（自绘，无依赖） */
class BarChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var data: List<Pair<String, Long>> = emptyList() // 标签, 毫秒

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E8E5A.toInt()
    }
    private val stubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE3E6EA.toInt()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5F6570.toInt()
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111418.toInt()
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    // 预分配的柱子矩形：onDraw 里每次 new RectF 会在每帧、每根柱子都分配一次
    // （7 根柱子 = 每帧 7 个对象），切标签页反复 invalidate 时会持续给 GC 添活。
    // 绘制是只读操作，一个复用实例足够。
    private val barRect = RectF()

    fun setData(d: List<Pair<String, Long>>) {
        data = d
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) {
            canvas.drawText(context.getString(R.string.chart_no_data), width / 2f, height / 2f, labelPaint)
            return
        }
        val maxV = (data.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
        val n = data.size
        val slot = width / n.toFloat()
        val barW = slot * 0.4f
        val baseline = height - 52f
        val chartTop = 44f
        val usable = baseline - chartTop - 34f

        data.forEachIndexed { i, (label, v) ->
            val cx = slot * i + slot / 2
            canvas.drawText(label, cx, height - 16f, labelPaint)
            if (v <= 0) {
                barRect.set(cx - barW / 2, baseline - 6f, cx + barW / 2, baseline)
                canvas.drawRoundRect(barRect, 6f, 6f, stubPaint)
                return@forEachIndexed
            }
            val barH = (v.toFloat() / maxV) * usable
            val top = baseline - barH
            barRect.set(cx - barW / 2, top, cx + barW / 2, baseline)
            canvas.drawRoundRect(barRect, 10f, 10f, barPaint)
            canvas.drawText(fmtDur(v), cx, top - 10f, valuePaint)
        }
    }

    /** 柱顶数值：按毫秒实际值显示，秒/分钟/小时自动档 */
    private fun fmtDur(ms: Long): String = when {
        ms < 60_000L -> "${ms / 1000}秒"
        ms < 3_600_000L -> "${ms / 60_000}分钟"
        // 必须显式传 Locale：土耳其语区等会把 '.' 当成 '.'以外的东西，
        // 产出「1,5小时」这类错位的数字（lint DefaultLocale）
        else -> String.format(java.util.Locale.getDefault(), "%.1f小时", ms / 3_600_000.0)
    }
}
