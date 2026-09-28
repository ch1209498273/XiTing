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

    private var data: List<Pair<String, Long>> = emptyList() // 标签, 分钟

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

    fun setData(d: List<Pair<String, Long>>) {
        data = d
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) {
            canvas.drawText("暂无数据", width / 2f, height / 2f, labelPaint)
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
                canvas.drawRoundRect(
                    RectF(cx - barW / 2, baseline - 6f, cx + barW / 2, baseline),
                    6f, 6f, stubPaint
                )
                return@forEachIndexed
            }
            val barH = (v.toFloat() / maxV) * usable
            val top = baseline - barH
            canvas.drawRoundRect(
                RectF(cx - barW / 2, top, cx + barW / 2, baseline),
                10f, 10f, barPaint
            )
            canvas.drawText("${v}分", cx, top - 10f, valuePaint)
        }
    }
}
