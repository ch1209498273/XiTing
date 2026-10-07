// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * per-app 语言应用助手。
 *
 * 两条路径，按优先级：
 * 1. **Android 13+** 用系统的 [android.app.LocaleManager]。用户在系统「应用语言」里
 *    改过后，平台会记住；但它**不会**自动影响资源解析（那是 AppCompat 的活儿，
 *    而本项目零依赖，不用 AppCompat），所以这里手动把系统里保存的语言包进
 *    Configuration。
 * 2. **Android 12 及以下**系统没有 per-app 语言，只能自己存。这里用
 *    [Prefs.LANG] 记录 tag，在 [wrap] 时套一个 ConfigurationContext。
 *
 * 两条路径都只影响**资源解析**（getString 走哪套 values-xxx），
 * 不影响代码逻辑 —— 所以切换语言不需要重启 App。
 */
object AppLocales {

    /** 空串 = 跟随系统 */
    const val SYSTEM = ""

    /** 支持的语言。tag 必须与 res/values-xxx 目录名一致。 */
    val SUPPORTED = listOf(SYSTEM, "zh", "en", "ja")

    fun current(ctx: Context): String =
        ctx.prefs().getString(Prefs.LANG, SYSTEM) ?: SYSTEM

    /**
     * 写入语言选择。
     *
     * API 33+ 同时写进系统的 LocaleManager，这样系统设置里也能看到同步状态；
     * 低版本只能靠本地的 pref（已足够 —— wrap() 每次都会读）。
     */
    fun set(ctx: Context, tag: String) {
        ctx.prefs().edit().putString(Prefs.LANG, tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val lm = ctx.getSystemService(Context.LOCALE_SERVICE) as android.app.LocaleManager
                val locales = if (tag == SYSTEM) LocaleList.getEmptyLocaleList()
                              else LocaleList.forLanguageTags(tag)
                lm.applicationLocales = locales
            } catch (_: Exception) {
                // 系统拒绝（如 OEM 限制）时静默降级：本地 pref 仍然生效
            }
        }
    }

    fun wrap(ctx: Context): Context {
        // 13+：以系统记录为准（用户可能在系统设置里改过）
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val lm = ctx.getSystemService(Context.LOCALE_SERVICE) as android.app.LocaleManager
                val locales = lm.applicationLocales
                if (!locales.isEmpty) {
                    val cfg = Configuration(ctx.resources.configuration)
                    cfg.setLocales(locales)
                    return ctx.createConfigurationContext(cfg)
                }
            } catch (_: Exception) {
            }
        }
        // 低版本（或系统里没设）：读我们自己的 pref
        val tag = current(ctx)
        if (tag.isEmpty() || tag == Locale.getDefault().toLanguageTag()) return ctx
        return applyTag(ctx, tag)
    }

    private fun applyTag(ctx: Context, tag: String): Context {
        return try {
            val cfg = Configuration(ctx.resources.configuration)
            val loc = Locale.forLanguageTag(tag)
            Locale.setDefault(loc)
            cfg.setLocales(LocaleList(loc))
            ctx.createConfigurationContext(cfg)
        } catch (_: Exception) {
            ctx
        }
    }
}
