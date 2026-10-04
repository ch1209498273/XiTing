// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView

/** 关于页（二级页面）：介绍 / 隐私 / 开源 / 许可 */
class AboutActivity : Activity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<TextView>(R.id.about_version).text =
            getString(R.string.about_version_fmt, BuildConfig.VERSION_NAME, BuildConfig.BUILD_ID)
        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.about_repo).setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ch1209498273/XiTing"))
                )
            } catch (_: Exception) {
            }
        }
    }
}
