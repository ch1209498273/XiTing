// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast

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

        // 崩溃日志：仅在存在时显示（点按复制，长按清除）。
        // 复制走系统剪贴板、不经网络 —— 与「零权限」定位一致。
        val crashRow = findViewById<TextView>(R.id.about_crash)
        if (CrashLog.last(this) == null) {
            crashRow.visibility = View.GONE
        } else {
            crashRow.setOnClickListener {
                val log = CrashLog.last(this)
                if (log != null) {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("XiTing", log))
                    Toast.makeText(this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                }
            }
            crashRow.setOnLongClickListener {
                CrashLog.clear(this)
                crashRow.visibility = View.GONE
                true
            }
        }
    }
}
