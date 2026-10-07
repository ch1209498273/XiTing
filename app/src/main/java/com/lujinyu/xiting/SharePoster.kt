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
        val headline: String,       // 主数字，如「12 小时」
        val headlineUnit: String,   // 主数字的单位
        val hint: String,           // 主数字下方的小字说明（必须与口径一致）
        val lines: List<Pair<String, String>>,  // (标签, 数值)
        val footer: String,
        val shareText: String       // 随图附带的纯文本（部分 App 只会用这个）
    )

    /**
     * 生成海报位图。纯计算，不碰磁盘，可单测。
     *
     * 版式（自上而下）：
     *   顶部  品牌行 + 精灵（居中，尺寸约为宽度的 42%）
     *   中部  主数字（超大号）
     *   下方  明细行（标签 · 数值）
     *   底部  脚注 + 仓库地址
     */
    fun render(ctx: Context, d: Data): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // ---- 背景：垂直渐变，用精灵当前配色的主色系，让图和宠物是一套的 ----
        val hue = d.skin.hue
        val top = hsv(hue, 0.30f, 0.34f)
        val bottom = hsv(hue, 0.42f, 0.16f)
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        // ---- 顶部：品牌 ----
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 46f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.LEFT
        c.drawText(ctx.getString(R.string.poster_brand), 72f, 128f, p)
        p.typeface = android.graphics.Typeface.DEFAULT
        p.textSize = 34f
        p.color = 0xCCFFFFFF.toInt()
        p.textAlign = Paint.Align.RIGHT
        c.drawText(d.stageName, (W - 72).toFloat(), 128f, p)

        // ---- 精灵：复用图鉴/小组件同款离屏渲染 ----
        val petSize = (W * 0.46f).toInt()
        try {
            val pet = PetSkins.snapshot(ctx, d.stage, d.skin, petSize, withBar = false, pct = 0)
            c.drawBitmap(pet, (W - petSize) / 2f, 190f, null)
            pet.recycle()
        } catch (e: Exception) {
            // 精灵画不出来不该让整张海报失败：留白继续，用户仍能分享数字
            Log.w(TAG, "精灵渲染失败，海报留白: $e")
        }

        // ---- 主数字 ----
        val headY = 190f + petSize + 190f
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = 168f
        p.color = 0xFFFFFFFF.toInt()
        // 单位单独画小一号跟在后面，所以这里要量出数字宽度再排版
        val numW = p.measureText(d.headline)
        p.textSize = 60f
        val unitW = p.measureText(d.headlineUnit)
        val totalW = numW + unitW + 18f
        val startX = (W - totalW) / 2f
        p.textAlign = Paint.Align.LEFT
        p.textSize = 168f
        c.drawText(d.headline, startX, headY, p)
        p.textSize = 60f
        p.color = 0xCCFFFFFF.toInt()
        c.drawText(d.headlineUnit, startX + numW + 18f, headY, p)

        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT
        p.textSize = 38f
        p.color = 0xB3FFFFFF.toInt()
        c.drawText(d.hint, (W / 2f), headY + 62f, p)

        // ---- 明细行 ----
        var y = headY + 168f
        p.textSize = 40f
        d.lines.forEach { (k, v) ->
            p.textAlign = Paint.Align.LEFT
            p.color = 0xB3FFFFFF.toInt()
            c.drawText(k, 140f, y, p)
            p.textAlign = Paint.Align.RIGHT
            p.color = 0xFFFFFFFF.toInt()
            p.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.drawText(v, (W - 140).toFloat(), y, p)
            p.typeface = android.graphics.Typeface.DEFAULT
            // 分隔线
            p.color = 0x26FFFFFF
            p.strokeWidth = 2f
            c.drawLine(140f, y + 26f, (W - 140).toFloat(), y + 26f, p)
            y += 92f
        }

        // ---- 底部 ----
        p.textAlign = Paint.Align.CENTER
        p.textSize = 34f
        p.color = 0x99FFFFFF.toInt()
        c.drawText(d.footer, (W / 2f), (H - 148f), p)
        p.textSize = 28f
        p.color = 0x66FFFFFF.toInt()
        c.drawText(REPO, (W / 2f), (H - 96f), p)

        return bmp
    }

    private const val REPO = "github.com/ch1209498273/XiTing"

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
