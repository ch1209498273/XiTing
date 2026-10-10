// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.Application

/**
 * 进程入口：目前只装「崩溃自捕获」。
 *
 * 为什么需要它：Application 是进程里最先跑的自定义代码，挂在这里能保证
 * 之后任何组件（Activity / Service / 广播）崩了都有处理器兜底。
 * 不做语言包装 —— 各 Activity / Service 自己在 attachBaseContext 里做。
 */
class XiTingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
