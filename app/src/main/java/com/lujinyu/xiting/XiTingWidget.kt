// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 标准小部件 4×2：精灵+进度条+今日/累计+息屏 */
class XiTingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetData.refresh(context, WidgetData.Style.STANDARD)
    }
}

/** 紧凑小部件 2×2 */
class XiTingWidgetCompact : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetData.refresh(context, WidgetData.Style.COMPACT)
    }
}

/** 大号小部件 4×3：标准 + 近7天迷你柱状图 */
class XiTingWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetData.refresh(context, WidgetData.Style.LARGE)
    }
}
