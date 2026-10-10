// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃自捕获（零依赖、零网络）。
 *
 * 这个应用是 sideload 分发的（酷安 / 蒲公英 / GitHub），没有 Play Console 的
 * 崩溃回收站。用户在反馈里说「闪退了」时，唯一能拿到现场信息的途径就是这里：
 * 把最后一次未捕获异常连同版本/机型落一个文件，用户在「关于页」一键复制。
 *
 * 隐私：只写本机私有目录 filesDir，不含任何个人数据；覆盖式保存（只留最近一次），
 * 不累积。显示与复制都必须由用户主动操作。
 */
object CrashLog {

    private const val FILE_NAME = "crash-last.txt"
    private const val MAX_CHARS = 16_000

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    /** 上次崩溃的文本（没有则 null） */
    fun last(ctx: Context): String? {
        val f = file(ctx)
        return if (!f.exists()) null else f.readText().takeIf { it.isNotBlank() }
    }

    fun clear(ctx: Context) {
        try {
            file(ctx).delete()
        } catch (_: Exception) {
        }
    }

    /** 在 Application.onCreate 里调用一次：装全局兜底，先存档、再交还原处理器 */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                file(app).writeText(build(t, e))
            } catch (_: Exception) {
                // 存档失败绝不能影响崩溃本身的上报路径
            }
            prev?.uncaughtException(t, e)
        }
    }

    private fun build(t: Thread, e: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { e.printStackTrace(it) }
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val head = StringBuilder()
            .append("XiTing crash report\n")
            .append("time: ").append(ts).append('\n')
            .append("version: ").append(BuildConfig.VERSION_NAME)
            .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            .append("build: ").append(BuildConfig.BUILD_ID).append('\n')
            .append("device: ").append(Build.MODEL)
            .append(" / Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            .append("thread: ").append(t.name).append("\n---\n")
        val full = head.toString() + sw.toString()
        return if (full.length > MAX_CHARS) full.take(MAX_CHARS) else full
    }
}
