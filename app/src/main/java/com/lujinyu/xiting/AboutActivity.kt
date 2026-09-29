package com.lujinyu.xiting

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView

/** 关于页：版本、介绍、隐私、开源地址、许可 */
class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.about_version).text =
            "版本 ${BuildConfig.VERSION_NAME} · 构建ID ${BuildConfig.BUILD_ID}"

        findViewById<View>(R.id.about_repo).setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ch1209498273/XiTing"))
                )
            } catch (_: Exception) {}
        }
    }
}
