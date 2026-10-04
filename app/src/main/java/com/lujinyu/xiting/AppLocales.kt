// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * per-app 语言应用助手。
 * Android 13+ 的 LocaleManager 设置的应用语言，平台 Activity/Service 不会自动
 * 反映到资源解析（AppCompat 才有内置支持）——这里在 attachBaseContext 时手动
 * 把系统里保存的应用语言包进 Configuration。
 */
object AppLocales {

    fun wrap(ctx: Context): Context {
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
        return ctx
    }
}
