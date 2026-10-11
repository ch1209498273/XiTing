// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

/**
 * 进程入口：崩溃自捕获 + 息屏建议通知的兜底调度。
 *
 * 为什么需要它：Application 是进程里最先跑的自定义代码，挂在这里能保证
 * 之后任何组件（Activity / Service / 广播）崩了都有处理器兜底。
 * 不做语言包装 —— 各 Activity / Service 自己在 attachBaseContext 里做。
 */
class XiTingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        scheduleSuggestJob()
    }

    /**
     * 建议通知的兜底路径（助手没跑时）：15 分钟周期任务，有媒体在播才发。
     * 开关关着时 JobService 内部直接返回。重复 schedule 是幂等更新。
     */
    private fun scheduleSuggestJob() {
        val js = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        js.schedule(
            JobInfo.Builder(1, ComponentName(this, SuggestJobService::class.java))
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build()
        )
    }
}
