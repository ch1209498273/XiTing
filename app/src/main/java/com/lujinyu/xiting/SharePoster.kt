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
        // ⚠ 两个文本分列左右，**必须互相让位**。
        // 西班牙语的品牌名「Escucha con pantalla apagada」（原 40f）配上
        // 形态名「Espíritu de tempestad」（原 36f）合计约 1043px，
        // 而画布可用宽度只有 936px（1080 减左右各 72）—— 会直接在中间重叠。
        // 中文下看不到（品牌名「息屏听剧」才 4 字），纯中文验证时漏掉的就是这类。
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = BRAND_SIZE
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.LEFT
        val brand = ctx.getString(R.string.poster_brand)
        val brandW = p.measureText(brand)
        c.drawText(brand, PAD.toFloat(), 116f, p)

        p.typeface = android.graphics.Typeface.DEFAULT
        p.color = 0xCCFFFFFF.toInt()
        p.textAlign = Paint.Align.RIGHT
        // 品牌优先保留，形态名用剩余宽度：先逐档缩小，仍放不下再截断。
        val availStage = (W - PAD * 2) - brandW - HEADER_GAP
        var stageSize = STAGE_SIZE
        while (stageSize > STAGE_MIN_SIZE) {
            p.textSize = stageSize
            if (p.measureText(d.stageName) <= availStage) break
            stageSize -= 2f
        }
        p.textSize = stageSize
        val stageText = if (p.measureText(d.stageName) <= availStage) {
            d.stageName
        } else {
            // 缩到下限还放不下（极端长词）：按可用宽度截断并加省略号。
            // ⚠ 不用 TextUtils.ellipsize —— 它要求第二个参数是 TextPaint，
            // 而这里只有一个普通 Paint（用它会直接编译不过）。
            // Paint.breakText 能算出「多少字符恰好塞进 maxWidth」，不必新建画具、
            // 也就不会引入两套度量差异。
            val ell = "…"
            val budget = (availStage - p.measureText(ell)).coerceAtLeast(10f)
            val n = p.breakText(d.stageName, true, budget, null)
            d.stageName.substring(0, n.coerceIn(1, d.stageName.length)) + ell
        }
        c.drawText(stageText, (W - PAD).toFloat(), 116f, p)

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
        val cardTop = headY + 100f
        val cardH = ROW_H * d.lines.size + 52f
        p.color = 0x14FFFFFF
        c.drawRoundRect(
            PAD.toFloat(), cardTop, (W - PAD).toFloat(), cardTop + cardH,
            36f, 36f, p
        )

        var y = cardTop + 68f
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

        // ---- 页脚：基线在卡片底之下，行间距 52px（36px 字体行高约 43px）----
        // 坐标是**推演**出来的，不是估的：卡片底 = cardTop + cardH，
        // 页脚两行必须落在它与 H 之间。
        val cardBottom = cardTop + cardH
        p.textAlign = Paint.Align.CENTER
        p.textSize = 34f
        p.color = 0x99FFFFFF.toInt()
        c.drawText(d.footer, (W / 2f), cardBottom + 46f, p)
        p.textSize = 28f
        p.color = 0x80FFFFFF.toInt()
        c.drawText(REPO, (W / 2f), cardBottom + 98f, p)

        return bmp
    }

    private const val REPO = "github.com/ch1209498273/XiTing"

    // ---- 版式常量。集中在这里，改版式不用翻绘制逻辑 ----
    //
    // ⚠ 这些数字是**互相约束**的：H=1440 要同时装下 顶栏(116) + 宠物 + 主数字 +
    // 卡片 + 两行页脚。改任何一个都要重新推演总高，否则就会出现「页脚压在卡片上」。
    //   116 顶栏基线
    // + 200 (PET_TOP 196 + 宠物 598)  ->  宠物底 794
    // + 172                        ->  主数字基线 966
    // + 100                        ->  卡片顶 1066
    // + 76*3+52 = 280               ->  卡片底 1346
    // + 46 / 98                    ->  页脚两行基线 1392 / 1444（H 之内）
    /** 页面左右留白 */
    private const val PAD = 72
    /** 精灵边长 = W * 0.52（0.58 时卡片底到 1410，页脚两行只剩 30px 放不下） */
    private val petSize = (W * 0.52f).toInt()
    /** 精灵顶部 y */
    private const val PET_TOP = 196f
    /** 明细每行高 */
    private const val ROW_H = 76f
    /** 数字与单位之间的间距 */
    private const val GAP = 16f
    /** 顶栏：品牌名字号 */
    private const val BRAND_SIZE = 40f
    /** 顶栏：形态名字号（放不下时会逐档缩到 STAGE_MIN_SIZE） */
    private const val STAGE_SIZE = 36f
    /** 顶栏：形态名的下限字号，再小就不读了，改用截断 */
    private const val STAGE_MIN_SIZE = 22f
    /** 顶栏：品牌名与形态名之间至少留出的空白 */
    private const val HEADER_GAP = 24f

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
        // 上限从 176 降到 150：第一版 176px 时「20时31分」几乎占满整行，
        // 右侧的单位「本周」被挤到边缘，看着局促。
        var n = 150f
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

            // IS_PENDING 置 0 之后才会对其它 App 可见；不置 0 对方只会拿到一个空图，
            // 而且文件在文件管理器里显示成 `.pending-xxx` 前缀，相册根本看不到。
            //
            // ⚠ update 必须**只带 IS_PENDING** 一列：若沿用 insert 时那个 ContentValues
            // （含 RELATIVE_PATH / DISPLAY_NAME），部分 ROM 的 MediaProvider 会在
            // 这里因路径冲突而静默失败，update 返回 0 而我们不检查 —— 症状就是
            // 「提示保存成功、相册里啥也没有」。
            val done = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            val updated = try {
                resolver.update(uri, done, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "IS_PENDING 置 0 抛异常: $e")
                0
            }
            if (updated <= 0) {
                // 置位没成功就回滚：否则会留下一个永久不可见的 .pending- 文件占着空间
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                Log.w(TAG, "IS_PENDING 置 0 失败（update=$updated），已删除残留行")
                return null
            }
            uri
        } catch (e: Exception) {
            Log.w(TAG, "海报保存失败: $e")
            null
        }
    }
}
