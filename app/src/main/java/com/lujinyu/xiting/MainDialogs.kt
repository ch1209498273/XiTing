// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * MainActivity 瘦身第 2 步（2026-10-11）：各业务弹窗与「设置页动态值」刷新。
 *
 * 以 MainActivity 扩展函数形式存在，调用点零改动（同包免 import）。
 * 依赖的 pageStats / pageSettings / tab / postRefresh 已放宽为 internal ——
 * 单模块项目里 internal 就是模块内私有，暴露面可控。
 */

/** 自定义定时的上限（分钟）= 10 小时。再大就超出了「睡前听一集」的合理范围。 */
private const val MAX_CUSTOM_TIMER_MIN = 600

/**
 * 成就明细弹窗：每条一枚手绘徽章（已点亮金色实心 / 未解锁暗色轮廓）。
 *
 * 原来是纯文本的 ✅/🔒 列表，解锁之后什么都不给 —— 用户评价「没啥意思」。
 * 现在徽章直接挂在精灵左侧身上，弹窗是它的明细视图。
 */
internal fun MainActivity.showAchievements() {
        val raw = prefs()
            .getStringSet(Prefs.ACH_UNLOCKED, emptySet()) ?: emptySet()
        // 必须走 knownUnlocked 过滤，不能直接用 raw.size ——
        // 老用户 prefs 里还留着已砍掉的 first/h1/h10/h50，直接取大小会显示「6/4」。
        val known = knownUnlocked(raw)
        val gotIds = known.map { it.id }.toSet()

        val view = layoutInflater.inflate(R.layout.dialog_achievements, null)
        view.findViewById<TextView>(R.id.ach_dlg_title).text =
            getString(R.string.ach_dlg_title_fmt, known.size, Achievements.ALL.size)
        val rows = view.findViewById<LinearLayout>(R.id.ach_rows)
        val d = resources.displayMetrics.density

        var dlgRef: android.app.AlertDialog? = null
        for ((idx, a) in Achievements.ALL.withIndex()) {
            val got = a.id in gotIds
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
            }
            row.addView(BadgeView(this).apply {
                val s = (40 * d).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
                bind(a.badge, got)
            })
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (14 * d).toInt()
                }
            }
            textCol.addView(TextView(this).apply {
                text = Achievements.title(this@showAchievements, a)
                setTextColor(if (got) getColor(R.color.text_primary) else getColor(R.color.text_hint))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            textCol.addView(TextView(this).apply {
                text = Achievements.desc(this@showAchievements, a)
                setTextColor(getColor(R.color.text_hint))
                textSize = 12f
            })
            textCol.addView(TextView(this).apply {
                text = getString(if (got) R.string.ach_row_unlocked else R.string.ach_row_locked)
                setTextColor(if (got) getColor(R.color.brand) else getColor(R.color.text_disabled))
                textSize = 11f
            })
            row.addView(textCol)
            // 点某条成就 → 关弹窗、回到「精灵可见」处、对应格脉冲一下（1.4s）
            row.isClickable = true
            val tv = android.util.TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                row.setBackgroundResource(tv.resourceId)
            }
            row.setOnClickListener {
                dlgRef?.dismiss()
                (pageStats as? android.widget.ScrollView)?.smoothScrollTo(0, 0)
                val pet = pageStats.findViewById<PetView>(R.id.pet_view)
                pet?.postDelayed({ pet.pulseBadge(idx) }, 400)
            }
            rows.addView(row)
        }

        val dlg = android.app.AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(getString(R.string.dlg_ok), null)
            .create()
        dlgRef = dlg
        dlg.show()
    }

/**
 * 自定义定时分钟数。
 *
 * 输入限 1..600（10 小时）：下限避免 0 被当成「取消」，上限防止误输入
 * 一个巨大的数，结果定时形同虚设。
 */
internal fun MainActivity.showCustomTimerDialog() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.timer_custom_hint)
            setPadding((16 * resources.displayMetrics.density).toInt(),
                       (12 * resources.displayMetrics.density).toInt(),
                       (16 * resources.displayMetrics.density).toInt(),
                       (12 * resources.displayMetrics.density).toInt())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.timer_custom))
            .setView(input)
            .setPositiveButton(getString(R.string.dlg_ok)) { _, _ ->
                val min = input.text.toString().trim().toIntOrNull()
                if (min == null || min < 1) {
                    Toast.makeText(this, getString(R.string.timer_custom_bad), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val clamped = min.coerceAtMost(MAX_CUSTOM_TIMER_MIN)
                startService(setTimerIntent(clamped.toLong()))
                Toast.makeText(
                    this, getString(R.string.toast_timer_set, clamped), Toast.LENGTH_SHORT
                ).show()
                postRefresh()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

/**
 * 悬浮球样式：只决定「文字 / 精灵」。
 *
 * 形态与配色已归图鉴统一管（PetForm + PetSkins）。这里再放一份 pet_0..pet_4
 * 就是两套状态各说各话 —— 早期正因如此：在设置里选了形态，主页精灵纹丝不动。
 * 所以这里只留二选一，形态跟着图鉴走。
 */
internal fun MainActivity.showBubbleStyleDialog() {
    val usesPet = PetForm.bubbleUsesPet(this)
    val labels = arrayOf(
        getString(R.string.bubble_default),
        getString(R.string.gallery_form_label) + " · " + PetView.stageName(this, PetForm.selected(this))
    )
    android.app.AlertDialog.Builder(this)
        .setTitle(getString(R.string.bubble_style_title))
        .setSingleChoiceItems(labels, if (usesPet) 1 else 0) { d, which ->
            PetForm.setBubbleUsesPet(this, which == 1)
            OverlayService.instance?.rebuildBubble()
            refreshBubbleStyleValue()
            d.dismiss()
        }
        .setNegativeButton(getString(R.string.dlg_cancel), null)
        .show()
}

/**
 * 语言选择。
 *
 * 选中后调用 recreate()：因为本项目不用 AppCompat（零依赖），
 * 框架没有内置的「配置变更后自动刷新文案」机制。与其手动重建整棵视图树
 * （很容易漏掉某个已经 setText 过的地方），不如让系统重建一次 ——
 * attachBaseContext 会重新走 AppLocales.wrap()，新的配置从头到尾生效。
 *
 * 重建会丢掉当前 tab 位置，所以先记住、onCreate 后恢复。
 */
internal fun MainActivity.showLangDialog() {
    val tags = AppLocales.SUPPORTED.toTypedArray()
    val labels = AppLocales.labels(this)
    val cur = AppLocales.current(this)
    val checked = tags.indexOf(cur).let { if (it < 0) 0 else it }
    android.app.AlertDialog.Builder(this)
        .setTitle(getString(R.string.lang_title))
        .setSingleChoiceItems(labels, checked) { d, which ->
            if (tags[which] != cur) {
                AppLocales.set(this, tags[which])
                // 悬浮球/通知等常驻文案住在服务里，不会自己跟着语言变；
                // 主动请服务重建一次系统级文案（配置变更回调之外的双保险）。
                OverlayService.instance?.refreshLocale()
                // 记住当前 tab，recreate() 后回到原处
                prefs().edit().putInt(Prefs.LAST_TAB, tab).apply()
                recreate()
            }
            d.dismiss()
        }
        .setNegativeButton(getString(R.string.dlg_cancel), null)
        .show()
}

/** 设置页「语言」行右侧的当前值 */
internal fun MainActivity.refreshLangValue() {
    val tv = pageSettings.findViewById<TextView>(R.id.lang_value) ?: return
    val tags = AppLocales.SUPPORTED
    val labels = AppLocales.labels(this)
    val i = tags.indexOf(AppLocales.current(this))
    tv.text = if (i in labels.indices) labels[i] else getString(R.string.lang_system)
}

/** 设置页「悬浮球样式」行的预览与当前值（形态/配色跟随图鉴的选择） */
internal fun MainActivity.refreshBubbleStyleValue() {
    val usesPet = PetForm.bubbleUsesPet(this)
    // 行内直接显示当前悬浮球的真实样子 + 名称（形态/配色跟随图鉴的选择）
    val box = pageSettings.findViewById<LinearLayout>(R.id.bubble_style_preview)
    box.removeAllViews()
    val density = resources.displayMetrics.density
    val size = (52 * density).toInt()
    val stage = PetForm.selected(this)
    val skin = PetSkins.active(this)
    if (usesPet) {
        box.addView(
            BubblePetView(this).apply {
                this.stage = stage
                applySkin(skin)
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
        )
        pageSettings.findViewById<TextView>(R.id.bubble_style_value).text =
            getString(R.string.name_pair_fmt, PetView.stageName(this, stage), PetSkins.name(this, skin))
    } else {
        box.addView(
            TextView(this).apply {
                // 预览必须用和真实悬浮球**同一个字符串**：之前用的是 bubble_label_off
                // （「Выкл. экран」），而真球用 bubble_label_plain（「Выкл」），
                // 于是设置页里看到的是两行字的小椭圆，跟真球对不上。
                text = getString(R.string.bubble_label_plain)
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0xB3000000.toInt())
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
        )
        pageSettings.findViewById<TextView>(R.id.bubble_style_value).text = getString(R.string.bubble_value_default)
    }
}
