// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * 分享海报链路，从 MainActivity 出走（2026-10-11 瘦身第 3 步）：
 * 组装数据（累计 / 本周两种口径）→ 后台渲染 → 预览 → 存盘并拉起系统分享，
 * 外加每日分享奖励结算。
 *
 * 以 MainActivity 扩展函数形式存在，调用点零改动（同包免 import）。
 */

/** 每日分享奖励的成长值。读方还有 MainActivity.refreshPetPanel（设置页分享行文案）。 */
internal const val SHARE_GP_PER_DAY = 5

/**
 * 分享海报（累计口径）。
 *
 * 海报生成失败（低版本、无媒体库权限等）时**退回纯文本**，
 * 不能因为「发不出图」就把分享功能整个堵死。
 */
internal fun MainActivity.shareAllTime() {
        val sessions = SessionLog.sessions(this)
        val allMs = sessions.sumOf { it.durationMs }
        val mah = Stats.estimatedMah(allMs)
        val stage = PetForm.selected(this)
        val skin = PetSkins.active(this)
        val days = Streaks.distinctListenDays(sessions)
        val best = Streaks.compute(sessions).best

        val d = SharePoster.Data(
            stage = stage,
            skin = skin,
            stageName = PetView.stageName(this, stage),
            headline = fmtDur(allMs),
            headlineUnit = getString(R.string.poster_unit_listen),
            hint = getString(R.string.poster_hint_all),
            lines = listOf(
                getString(R.string.poster_row_days) to days.toString(),
                getString(R.string.poster_row_mah) to getString(R.string.fmt_mah, mah),
                getString(R.string.poster_row_streak) to getString(R.string.fmt_days, best)
            ),
            footer = getString(R.string.poster_footer_all),
            shareText = getString(R.string.share_text, fmtDur(allMs), mah, PetView.stageName(this, stage)),
            // 连续天数是这类 App 最值得炫耀的指标，给它一个角标位
            badge = if (best >= 2) getString(R.string.poster_badge_streak, best) else null
        )
        sharePoster(d, "all")
        settleDailyShare()
    }

/** 周报口径的海报 */
internal fun MainActivity.shareWeekPoster() {
        val s = weekSummary ?: return
        val stage = s.stageNow
        val d = SharePoster.Data(
            stage = stage,
            skin = PetSkins.active(this),
            stageName = PetView.stageName(this, stage),
            headline = fmtDur(s.weekMs),
            headlineUnit = getString(R.string.poster_unit_week),
            hint = getString(R.string.poster_hint_week),
            lines = listOf(
                getString(R.string.poster_row_days_week) to
                    getString(R.string.fmt_days_sessions, s.listenDays, s.sessionCount),
                getString(R.string.poster_row_mah) to getString(R.string.fmt_mah, s.mahSaved),
                getString(R.string.poster_row_gp) to s.weekGp.toString()
            ),
            footer = getString(R.string.poster_footer_week),
            shareText = getString(
                R.string.week_share_text, fmtDur(s.weekMs), s.listenDays, s.mahSaved,
                PetView.stageName(this, stage)
            ),
            badge = null
        )
        sharePoster(d, "week")
        settleDailyShare()
    }

/**
 * 分享海报：后台生成 → 预览 → 确认后才拉起系统分享。
 *
 * 为什么要预览：
 * 1. 海报有渲染失败的可能（精灵离屏渲染、字体缺失），发出去一张坏图比不发更糟；
 * 2. 排版（数字过长、明细行溢出）用户自己一眼能看出来，能直接取消重来；
 * 3. 主流 App 都是「先看图再发」，一步到位反而像在赌。
 */
internal fun MainActivity.sharePoster(d: SharePoster.Data, tag: String) {
        // 1080×1440 的 Canvas 绘制 + 一次 View 的 measure/layout/draw 约几十到上百毫秒，
        // 放主线程会掉一帧。放到后台线程，回主线程弹预览。
        Thread {
            val bmp = try {
                SharePoster.render(this, d)
            } catch (e: Exception) {
                Log.w("XiTing", "海报生成失败: $e")
                null
            }
            Handler(Looper.getMainLooper()).post {
                if (bmp == null) shareTextFallback(d.shareText)
                else showPosterPreview(bmp, d, tag)
            }
        }.start()
    }

    /**
     * 预览对话框：看图（可切 3:4 / 1:1 版式）→ 分享 / 保存 / 取消。
     *
     * ## 位图回收纪律
     * ⚠ **不能在 onDismiss 里无条件 recycle**。
     * 点「分享」/「保存」时，onClick 先启动后台线程去写盘，随后对话框 dismiss
     * 触发 onDismiss 把 bitmap 回收掉 —— 后台线程再 compress 就拿到一张
     * 已回收的位图，MediaStore 里留下一个 0 字节的 .pending- 文件，
     * 而且 UI 上还提示「保存成功」。这个竞态真机必现。
     *
     * 加了比例切换（异步重渲染）之后，回收规则扩成三条：
     * 1. 只有「取消」在 onDismiss 回收当前图（handedOff 之后 onDismiss 不再碰）；
     * 2. 走分享/保存路径的由 sendPoster 存盘后自己回收；
     * 3. 切比例被顶掉的旧图、以及**迟到的渲染结果**（弹窗已关 / 已交接 / 已有更新一代）
     *    自己回收自己 —— 那时 current 的所有权已在别处，谁也不能碰它。
     */
    internal fun MainActivity.showPosterPreview(bmp: android.graphics.Bitmap, d: SharePoster.Data, tag: String) {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()

        var current = bmp
        var handedOff = false
        var dismissed = false
        var gen = 0

        val image = android.widget.ImageView(this).apply {
            setImageBitmap(bmp)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        val scroll = android.widget.ScrollView(this).apply {
            setBackgroundColor(0xFF15171B.toInt())
            setPadding(pad, pad, pad, pad)
            addView(image, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        // 比例切换 chips：「3:4」「1:1」是比例记号，各语言通用，不进 strings.xml；
        // 海报预览区刻意不随主题（同背景 0xFF15171B），色值就地写死。
        var selRatio = PosterLayout.Ratio.PORTRAIT_3_4
        val chipRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        val chip34 = posterChip(this, "3:4", density)
        val chip11 = posterChip(this, "1:1", density)
        chipRow.addView(chip34)
        chipRow.addView(chip11)
        styleChip(chip34, true, density)

        fun switchRatio(r: PosterLayout.Ratio) {
            if (r == selRatio || handedOff || dismissed) return
            selRatio = r
            styleChip(chip34, r == PosterLayout.Ratio.PORTRAIT_3_4, density)
            styleChip(chip11, r == PosterLayout.Ratio.SQUARE_1_1, density)
            val g = ++gen
            Thread {
                val nb = try {
                    SharePoster.render(this, d, r)
                } catch (e: Exception) {
                    Log.w("XiTing", "海报生成失败: $e")
                    null
                }
                Handler(Looper.getMainLooper()).post {
                    // 迟到/过期结果自己回收自己；渲染失败保留当前图（同一数据刚渲染成功过）
                    if (g != gen || dismissed || handedOff) {
                        nb?.recycle()
                        return@post
                    }
                    if (nb == null) return@post
                    image.setImageBitmap(nb)
                    if (!current.isRecycled) current.recycle()
                    current = nb
                }
            }.start()
        }
        chip34.setOnClickListener { switchRatio(PosterLayout.Ratio.PORTRAIT_3_4) }
        chip11.setOnClickListener { switchRatio(PosterLayout.Ratio.SQUARE_1_1) }

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(chipRow)
            addView(scroll)
        }

        val dlg = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.poster_preview_title))
            .setView(root)
            .setPositiveButton(getString(R.string.poster_preview_share)) { _, _ ->
                handedOff = true
                sendPoster(current, d.shareText, tag, share = true)
            }
            // 保存：不想分享、只想把这张图留在相册的场合。
            // 海报本来就是写进公共 Pictures 的，所以「保存」只是不拉起分享面板。
            .setNeutralButton(getString(R.string.poster_preview_save)) { _, _ ->
                handedOff = true
                sendPoster(current, d.shareText, tag, share = false)
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .create()
        dlg.setOnDismissListener {
            dismissed = true
            if (!handedOff && !current.isRecycled) current.recycle()
        }
        dlg.show()
    }

/** 把预览里那张图存盘并拉起系统分享（后台做 IO）。share=false 时只存不发。 */
internal fun MainActivity.sendPoster(
        bmp: android.graphics.Bitmap,
        text: String,
        tag: String,
        share: Boolean
    ) {
        Thread {
            val uri = try {
                SharePoster.save(this, bmp, tag)
            } catch (e: Exception) {
                Log.w("XiTing", "海报保存失败: $e")
                null
            } finally {
                // 存盘结束后才回收；之前是由对话框的 onDismiss 回收的，
                // 那会在后台线程开始前就把 bitmap 释放掉（见 showPosterPreview 的注释）
                if (!bmp.isRecycled) bmp.recycle()
            }
            Handler(Looper.getMainLooper()).post {
                if (uri == null) {
                    if (share) shareTextFallback(text)
                    else Toast.makeText(this, getString(R.string.poster_save_fail), Toast.LENGTH_SHORT).show()
                    return@post
                }
                if (!share) {
                    Toast.makeText(this, getString(R.string.poster_saved), Toast.LENGTH_SHORT).show()
                    return@post
                }
                try {
                    startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            // 部分 App 只认纯文本，两个都给
                            putExtra(Intent.EXTRA_TEXT, text)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        getString(R.string.share_chooser)
                    ))
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.toast_browser_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

/** 海报出不来时的退路：纯文本也得能发出去 */
internal fun MainActivity.shareTextFallback(text: String) {
        try {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                getString(R.string.share_chooser)
            ))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.toast_browser_fail), Toast.LENGTH_SHORT).show()
        }
    }

/** 每日分享奖励结算（两个分享入口共用） */
internal fun MainActivity.settleDailyShare() {
        val prefs = prefs()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val last = prefs.getString(Prefs.LAST_SHARE_DATE, "")
        if (last != today) {
            prefs.edit().putString(Prefs.LAST_SHARE_DATE, today).apply()
            EnergyStore.add(this, SHARE_GP_PER_DAY)
            Toast.makeText(
                this, getString(R.string.toast_share_done, SHARE_GP_PER_DAY), Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, getString(R.string.toast_share_claimed), Toast.LENGTH_SHORT).show()
        }
    }

/** 预览对话框顶部的比例切换 chip：胶囊底，选中态由 [styleChip] 上色 */
private fun posterChip(ctx: Context, label: String, density: Float): android.widget.TextView =
    android.widget.TextView(ctx).apply {
        text = label
        textSize = 14f
        setPadding((22 * density).toInt(), (8 * density).toInt(), (22 * density).toInt(), (8 * density).toInt())
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (10 * density).toInt() }
    }

/** chip 选中态：亮底深字；未选中：透明底浅字（海报区刻意不随主题，色值就地定） */
private fun styleChip(chip: android.widget.TextView, selected: Boolean, density: Float) {
    chip.background = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = 20 * density
        setColor(if (selected) 0xFFE8EAED.toInt() else 0x22FFFFFF)
    }
    chip.setTextColor(if (selected) 0xFF10131A.toInt() else 0xCCFFFFFF.toInt())
}
