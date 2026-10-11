// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.widget.Toast

/**
 * 精灵图鉴（换肤选择器）与换肤后的全局同步，从 MainActivity 出走（2026-10-11 瘦身第 2 步）。
 *
 * 以 MainActivity 扩展函数的形式存在：调用点零改动（同包免 import），
 * 但要求被跨文件访问的成员（pageStats / renderStats / refreshHomeStats）放宽为 internal ——
 * 单模块项目里 internal 等价于模块内私有，暴露面可控。
 */

/**
 * 精灵图鉴：可视化换肤选择器。
 *
 * 此前这里是个纯文本 AlertDialog，只有「好的」一个按钮 —— 也就是**只能看不能换**，
 * KDoc 写的「已解锁可穿戴」名不副实；PetSkins.wear() 的唯一调用点是调试广播，
 * 正式版里根本走不到，pet_skin 键只写不进。
 *
 * 现在每行渲染真实精灵缩略图（PetSkins.snapshot，小部件已在用），
 * 点击已解锁的即换上并同步刷新精灵 / 悬浮球 / 小组件；未解锁的置灰并提示条件。
 */
internal fun MainActivity.showSkinGallery() {
        val sessions = SessionLog.sessions(this)
        val totalMs = sessions.sumOf { it.durationMs }
        val maxSingleMs = sessions.maxOfOrNull { it.durationMs } ?: 0L
        val streakDays = Streaks.compute(sessions).current
        val mah = Stats.estimatedMah(totalMs)
        val gp = EnergyStore.collectedTotal(this).toLong()

        // 先评估一次配色，保证刚达成的立刻出现在「已解锁」里
        PetSkins.evaluate(this, PetSkins.SkinProgress(totalMs, maxSingleMs, streakDays, mah, gp))
            .forEach {
                Toast.makeText(this, getString(R.string.toast_skin_unlock, PetSkins.name(this, it)),
                    Toast.LENGTH_SHORT).show()
            }
        val unlockedSkinIds = PetSkins.unlockedIds(this)

        val view = layoutInflater.inflate(R.layout.dialog_skin_picker, null)
        val preview = view.findViewById<android.widget.ImageView>(R.id.preview)
        val previewLabel = view.findViewById<android.widget.TextView>(R.id.preview_label)
        val growthHint = view.findViewById<android.widget.TextView>(R.id.growth_hint)
        val formRow = view.findViewById<android.widget.LinearLayout>(R.id.form_row)
        val skinRow = view.findViewById<android.widget.LinearLayout>(R.id.skin_row)

        val d = resources.displayMetrics.density
        val thumbPx = (56 * d).toInt()

        // 当前选择：形态 + 配色
        var curForm = PetForm.selected(this)
        var curSkin = PetSkins.active(this)

        // 选中态描边：重建时按当前选择直接画上去
        fun selectedBg(selected: Boolean) = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 12 * d
            if (selected) {
                setColor(0x1420B26B)
                setStroke((1.5 * d).toInt().coerceAtLeast(1), 0xFF20B26B.toInt())
            } else {
                setColor(0x0A000000)
            }
        }

        /** 顶部大图 + 形态/配色文案 + 成长提示。 */
        fun refreshPreview() {
            preview.setImageBitmap(
                PetSkins.snapshot(this@showSkinGallery, curForm, curSkin, (110 * d).toInt(), withBar = false, pct = 0)
            )
            previewLabel.text =
                getString(R.string.name_pair_fmt, PetView.stageName(this@showSkinGallery, curForm), PetSkins.name(this@showSkinGallery, curSkin))
            val maxed = curForm >= PetForm.unlockedStage(this)
            growthHint.text = if (maxed) getString(R.string.gallery_form_maxed)
            else getString(R.string.gallery_form_next, PetForm.requiredFor(curForm + 1))
        }

        // 形态区的缩略图要随配色变、配色区的缩略图要随形态变，所以两行都必须能整体重建。
        // 之前它们只在打开弹窗时渲染一次，于是「上面选了形态，下面配色还是旧形态」——
        // 用户反馈的第二个问题。重建时保留当前滚动位置，免得跳回起点。
        fun rebuildRows() {
            val formScroll = formRow.parent as? android.widget.HorizontalScrollView
            val skinScroll = skinRow.parent as? android.widget.HorizontalScrollView
            val formX = formScroll?.scrollX ?: 0
            val skinX = skinScroll?.scrollX ?: 0

            formRow.removeAllViews()
            skinRow.removeAllViews()

            // ---- 形态区：成长值决定能选到哪一形态，选哪个由用户 ----
            for (i in 0..PetView.STAGE_KING) {
                val unlocked = PetForm.isUnlocked(this@showSkinGallery, i)
                val box = android.widget.LinearLayout(this@showSkinGallery).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    val pad = (6 * d).toInt()
                    setPadding(pad, pad, pad, pad)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (8 * d).toInt() }
                }
                box.setBackground(selectedBg(i == curForm))
                box.addView(android.widget.ImageView(this@showSkinGallery).apply {
                    // 形态缩略图用当前配色，一眼看出「这个形态穿上现在这身是什么样」
                    setImageBitmap(PetSkins.snapshot(this@showSkinGallery, i, curSkin, thumbPx, withBar = false, pct = 0))
                    alpha = if (unlocked) 1f else 0.3f
                })
                box.addView(android.widget.TextView(this@showSkinGallery).apply {
                    text = if (unlocked) PetView.stageName(this@showSkinGallery, i)
                           else getString(R.string.gallery_locked_short, PetView.stageName(this@showSkinGallery, i))
                    textSize = 11f
                    setTextColor(getColor(R.color.text_secondary))
                })
                box.setOnClickListener {
                    if (!PetForm.select(this@showSkinGallery, i)) {
                        Toast.makeText(this@showSkinGallery,
                            getString(R.string.gallery_form_locked_toast, PetForm.requiredFor(i)),
                            Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    curForm = i
                    rebuildRows()
                    applySkinEverywhere()
                    Toast.makeText(this@showSkinGallery, getString(R.string.gallery_worn, PetView.stageName(this@showSkinGallery, i)),
                        Toast.LENGTH_SHORT).show()
                }
                formRow.addView(box)
            }

            // ---- 配色区 ----
            PetSkins.ALL.forEach { skin ->
                val unlocked = skin.id in unlockedSkinIds
                val box = android.widget.LinearLayout(this@showSkinGallery).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    val pad = (6 * d).toInt()
                    setPadding(pad, pad, pad, pad)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (8 * d).toInt() }
                }
                box.setBackground(selectedBg(skin.id == curSkin.id))
                box.addView(android.widget.ImageView(this@showSkinGallery).apply {
                    // 配色缩略图用当前形态
                    setImageBitmap(PetSkins.snapshot(this@showSkinGallery, curForm, skin, thumbPx, withBar = false, pct = 0))
                    alpha = if (unlocked) 1f else 0.3f
                })
                box.addView(android.widget.TextView(this@showSkinGallery).apply {
                    text = if (unlocked) PetSkins.name(this@showSkinGallery, skin) else "🔒"
                    textSize = 11f
                    setTextColor(getColor(R.color.text_secondary))
                })
                box.setOnClickListener {
                    if (!unlocked) {
                        Toast.makeText(this@showSkinGallery,
                            getString(R.string.gallery_locked, PetSkins.name(this@showSkinGallery, skin), PetSkins.cond(this@showSkinGallery, skin)),
                            Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    curSkin = skin
                    PetSkins.wear(this@showSkinGallery, skin)
                    rebuildRows()
                    applySkinEverywhere()
                }
                skinRow.addView(box)
            }

            formScroll?.scrollTo(formX, 0)
            skinScroll?.scrollTo(skinX, 0)
            // 顶部大图也在这里刷，不在各个点击回调里单独调。
            // 上一版把 refreshPreview() 只放在打开弹窗时（而且一放就是两遍，明显是改了一半），
            // 两个点击回调都忘了调 —— 于是「下面选了形态/配色，上面大图纹丝不动」。
            // 收拢到重建函数末尾只有这一个调用点，以后新增交互不会再漏。
            refreshPreview()
        }

        rebuildRows()

        // 弹窗引用用局部变量即可：没有任何点击回调需要主动关闭它
        //（换肤的收尾走 rebuildRows + applySkinEverywhere，不 dismiss）。
        val dlg = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.gallery_title))
            .setView(view)
            .setPositiveButton(getString(R.string.dlg_ok), null)
            .create()
        dlg.show()
    }

/** 形态/配色变更后同步：主页精灵 / 悬浮球 / 三档小组件 */
internal fun MainActivity.applySkinEverywhere() {
    renderStats()
    refreshHomeStats()
    OverlayService.instance?.rebuildBubble()
    WidgetData.refreshAll(this)
}
