// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
/**
 * 分享海报。
 *
 * 为什么自己画而不是拼字符串：主流的听剧/健身类 App 分享出去的都是图片，
 * 纯文字在微信/小红书里既不好看也没有传播力。海报能带上一只**用户自己的精灵**
 * （形态 + 当前配色），这是这个 App 唯一值得晒的东西。
 *
 * 全部用框架自带的 Canvas 画，零第三方依赖（项目没有 androidx）。
 *
 * ## 为什么不用 FileProvider
 * 分享图片需要把一个 URI 授权给目标 App。本项目零依赖，没有 androidx 的
 * FileProvider，而自己写一个 ContentProvider 要处理 openFile/grantUriPermission/
 * 多进程，代码量和出错面都大得多。
 * 改用 [MediaStore] 把图写进公共 Pictures 目录，直接拿到 `content://` URI ——
 * Android 10+ 不需要任何权限，接收方靠 `FLAG_GRANT_READ_URI_PERMISSION` 读取。
 * 唯一代价是图会留在相册里，但这对「分享战绩」类内容反而是合理的。
 */
object SharePoster {

    /** 3:4 竖版。1080 宽是主流分享图宽度，1440 = 1080 × 4/3。 */
    const val W = 1080
    const val H = 1440

    private const val TAG = "XiTing"
    /** 不能是 const：Environment.DIRECTORY_PICTURES 是另一个类的 const，跨类拼接不编译 */
    private val REL_DIR = Environment.DIRECTORY_PICTURES + "/XiTing"

    /** 海报上要展示的内容。由调用方组装，绘制层不关心文案。 */
    data class Data(
        val stage: Int,
        val skin: PetSkins.Skin,
        val stageName: String,
        val headline: String,       // 主数字，如「20 时 3 分」
        val headlineUnit: String,   // 主数字的单位
        val hint: String,           // 主数字下方的小字说明（必须与口径一致）
        val lines: List<Pair<String, String>>,  // (标签, 数值)
        val footer: String,
        val shareText: String,      // 随图附带的纯文本（部分 App 只会用这个）
        val badge: String? = null    // 右上角可选角标，如「连续 12 天」
    )

    /**
     * 生成海报位图。纯计算，不碰磁盘，可单测。
     *
     * 版式（自上而下，1080×1440）：
     *   顶栏  品牌名 + 形态名（+ 可选角标）
     *   主角  精灵（宽度的 58%）+ 背后径向光晕
     *   数字  与单位同行，按可用宽度自动缩放
     *   卡片  明细收在半透明圆角卡里
     *   页脚  脚注 + 仓库地址
     *
     * ## 为什么背景不用皮肤色相铺底
     * 第一版直接用皮肤色相铺满，结果翡翠皮肤配深绿底 —— 宠物和背景同色系，
     * 对比度掉下去，整张图发闷。改成**深色中性底 + 皮肤色只做背后光晕**：
     * 无论什么皮肤宠物都能跳出来，同时又保持了「图和宠物是一套配色」。
     */
    fun render(ctx: Context, d: Data): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val hue = d.skin.hue

        // ---- 背景：深色中性渐变（不用皮肤色，理由见 KDoc）----
        p.shader = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            0xFF161A21.toInt(), 0xFF0B0E13.toInt(), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        // ---- 顶栏 ----
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = 40f
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.LEFT
        c.drawText(ctx.getString(R.string.poster_brand), PAD.toFloat(), 116f, p)
        p.typeface = android.graphics.Typeface.DEFAULT
        p.textSize = 36f
        p.color = 0xCCFFFFFF.toInt()
        p.textAlign = Paint.Align.RIGHT
        c.drawText(d.stageName, (W - PAD).toFloat(), 116f, p)

        // ---- 主角：精灵 + 背后径向光晕 ----
        val glowCx = W / 2f
        val glowCy = PET_TOP + petSize * 0.46f
        p.shader = android.graphics.RadialGradient(
            glowCx, glowCy, petSize * 0.85f,
            hsv(hue, 0.55f, 0.52f), 0x00000000, Shader.TileMode.CLAMP
        )
        c.drawCircle(glowCx, glowCy, petSize * 0.85f, p)
        p.shader = null

        d.badge?.let { drawBadge(c, p, it, PET_TOP + 6f, hue) }

        try {
            val pet = PetSkins.snapshot(ctx, d.stage, d.skin, petSize, withBar = false, pct = 0)
            c.drawBitmap(pet, (W - petSize) / 2f, PET_TOP, null)
            pet.recycle()
        } catch (e: Exception) {
            // 精灵画不出来不该让整张海报失败：留白继续，用户仍能分享数字
            Log.w(TAG, "精灵渲染失败，海报留白: $e")
        }

        // ---- 主数字 ----
        val headY = PET_TOP + petSize + 172f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.color = 0xFFFFFFFF.toInt()
        val sizes = fitHeadline(p, d.headline, d.headlineUnit)
        val startX = (W - (sizes.numW + sizes.unitW + GAP)) / 2f
        p.textAlign = Paint.Align.LEFT
        p.textSize = sizes.num
        c.drawText(d.headline, startX, headY, p)
        p.textSize = sizes.unit
        p.color = 0xCCFFFFFF.toInt()
        c.drawText(d.headlineUnit, startX + sizes.numW + GAP, headY, p)

        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT
        p.textSize = 34f
        p.color = 0xB3FFFFFF.toInt()
        c.drawText(d.hint, (W / 2f), headY + 58f, p)

        // ---- 明细卡片：收进半透明圆角矩形，视觉上才是一个「组」----
        val cardTop = headY + 112f
        val cardH = ROW_H * d.lines.size + 52f
        p.color = 0x14FFFFFF
        c.drawRoundRect(
            PAD.toFloat(), cardTop, (W - PAD).toFloat(), cardTop + cardH,
            36f, 36f, p
        )

        var y = cardTop + 72f
        p.textSize = 38f
        d.lines.forEach { (k, v) ->
            p.textAlign = Paint.Align.LEFT
            p.color = 0x99FFFFFF.toInt()
            c.drawText(k, PAD + 48f, y, p)
            p.textAlign = Paint.Align.RIGHT
            p.color = 0xFFFFFFFF.toInt()
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.drawText(v, (W - PAD - 48f).toFloat(), y, p)
            p.typeface = android.graphics.Typeface.DEFAULT
            y += ROW_H
        }

        // ---- 页脚 ----
        p.textAlign = Paint.Align.CENTER
        p.textSize = 36f
        p.color = 0x99FFFFFF.toInt()
        c.drawText(d.footer, (W / 2f), (H - 128f), p)
        p.textSize = 30f
        p.color = 0x80FFFFFF.toInt()
        c.drawText(REPO, (W / 2f), (H - 74f), p)

        return bmp
    }

    private const val REPO = "github.com/ch1209498273/XiTing"

    // ---- 版式常量。集中在这里，改版式不用翻绘制逻辑 ----
    /** 页面左右留白 */
    private const val PAD = 72
    /** 精灵边长 = W * 0.58 */
    private val petSize = (W * 0.58f).toInt()
    /** 精灵顶部 y */
    private const val PET_TOP = 196f
    /** 明细每行高 */
    private const val ROW_H = 84f
    /** 数字与单位之间的间距 */
    private const val GAP = 16f

    private class HeadSizes(val num: Float, val unit: Float, val numW: Float, val unitW: Float)

    /**
     * 把「数字 + 单位」缩到可用宽度内。
     *
     * ⚠ 必须缩放而不是固定字号：7 种语言里同一个时长写出来长度差异极大
     * （「20时3分」是 5 个全角字，"20 h 3 min" 是 9 个半角字符），
     * 固定 176px 在英文/俄文下会直接溢出画面。
     * 数字与单位的比例锁死为 2.4，整体一起缩，排版才不会走形。
     */
    private fun fitHeadline(p: Paint, num: String, unit: String): HeadSizes {
        val maxW = (W - PAD * 2).toFloat()
        var n = 176f
        while (n > 48f) {
            p.textSize = n
            val nw = p.measureText(num)
            p.textSize = n / 2.4f
            val uw = p.measureText(unit)
            if (nw + uw + GAP <= maxW) return HeadSizes(n, n / 2.4f, nw, uw)
            n -= 6f
        }
        p.textSize = 48f
        val nw = p.measureText(num)
        p.textSize = 48f / 2.4f
        return HeadSizes(48f, 48f / 2.4f, nw, p.measureText(unit))
    }

    /** 角标胶囊：底色用皮肤色相，文字用深色，任何皮肤下都读得清 */
    private fun drawBadge(c: Canvas, p: Paint, text: String, top: Float, hue: Float) {
        p.textSize = 34f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val tw = p.measureText(text)
        val padH = 34f
        val h = 66f
        val w = tw + padH * 2
        val left = (W - w) / 2f
        p.color = hsv(hue, 0.55f, 0.92f)
        c.drawRoundRect(left, top, left + w, top + h, h / 2f, h / 2f, p)
        p.color = 0xFF10131A.toInt()
        p.textAlign = Paint.Align.CENTER
        c.drawText(text, W / 2f, top + h * 0.70f, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = android.graphics.Typeface.DEFAULT
    }

    private fun hsv(h: Float, s: Float, v: Float): Int =
        Color.HSVToColor(floatArrayOf(h, s, v))

    /**
     * 写进公共 Pictures 目录并返回 `content://` URI。
     *
     * @return 失败返回 null（调用方应退回纯文本分享，而不是崩）
     */
    fun save(ctx: Context, bmp: Bitmap, nameHint: String): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null // 9 及以下分区存储限制多，暂不支持
        return try {
            val name = "XiTing-$nameHint-${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, REL_DIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: return null
            // IS_PENDING 置 0 之后才会对其它 App 可见；不置 0 对方只会拿到一个空图
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            Log.w(TAG, "海报保存失败: $e")
            null
        }
    }
}
