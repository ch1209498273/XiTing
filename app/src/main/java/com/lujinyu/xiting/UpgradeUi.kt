package com.lujinyu.xiting

import android.view.View
import android.widget.ImageView
import android.widget.TextView

/** V4 navigation and primary actions; media details are a small optional row. */
internal fun MainActivity.bindUpgradeUi() {
    // 卡片只报来源与能力，不跳任何界面：选择播放器的流程在 v5.0.1 已撤掉，
    // 留着可点+水波就是指向不存在界面的死入口
    with(pageHome.findViewById<View>(R.id.playback_source_card)) {
        setOnClickListener(null);isClickable=false;isFocusable=false;foreground=null
    }
    bindReliabilityUi()
    applyHomeEntryOrder()
}

/**
 * 首页入口排序的真正消费者。
 * 只在参与排序的槽位之间重排：黑幕卡、最近听过、节能统计这些没被 key 覆盖的节点原地不动，
 * 否则"把播放器排到第一"会把整页顺序搅乱。权限是一组两个节点（标题+面板），一起搬。
 */
internal fun MainActivity.applyHomeEntryOrder() {
    val container=(pageHome as? android.widget.ScrollView)?.getChildAt(0) as? android.view.ViewGroup ?: return
    fun topOf(id:Int):android.view.View? {
        var v:android.view.View?=pageHome.findViewById(id)
        while(v!=null && v.parent!==container) v=v.parent as? android.view.View
        return v
    }
    val groups=HomeEntryOrder.keys(this).map { key -> when(key) {
        "timer" -> listOfNotNull(topOf(R.id.timer_chip))
        "player" -> listOfNotNull(topOf(R.id.playback_source_card))
        "demo" -> listOfNotNull(topOf(R.id.btn_first_use))
        else -> listOfNotNull(topOf(R.id.home_permissions_title),topOf(R.id.home_permissions_panel))
    } }.filter { it.isNotEmpty() }
    val moving=groups.flatten().distinct()
    if(moving.size<2) return
    val slots=moving.map { container.indexOfChild(it) }
    if(slots.any { it<0 }) return                       // 结构变了就别乱动
    val ordered=groups.fold(mutableListOf<android.view.View>()) { acc,g -> acc.addAll(g);acc }
    if(ordered.size!=moving.size) return
    if(moving.sortedBy { container.indexOfChild(it) }==ordered) return   // 已经是目标顺序
    val sortedSlots=slots.sorted()
    ordered.forEach { container.removeView(it) }
    // 按升序插回原槽位：第 i 个位置前有 sortedSlots[i]-i 个未参与节点和 i 个已插回的参与节点，
    // 两者相加恰好等于 sortedSlots[i]，所以直接用原索引即可
    ordered.forEachIndexed { i,v -> container.addView(v,sortedSlots[i]) }
}
internal fun MainActivity.refreshUpgradeUi() {
    OverlayService.instance?.refreshSessionSnapshot()
    pageSettings.findViewById<TextView>(R.id.backup_last_success)?.text=
        BackupManager.lastSuccessfulBackupAt(this)?.let {
            getString(R.string.ui_last_backup_fmt,java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))
        } ?: getString(R.string.ui_no_backup)
    val np=try { queryNowPlaying() } catch(_:RuntimeException) { null }
    val status=playbackLookup().availability
    val row=pageHome.findViewById<View>(R.id.playback_source_card)
    val selectedPackage=PlaybackSelection.packageName
    // 权限本身交给首页「通知使用权」那行，卡片只承担「来源+能力」与「所选来源现在怎么了」；
    // 两者都不成立时不显示，否则这里会退回「请选择播放器」这种没有下文的话
    val showCard=np!=null || PlaybackSelection.hasSelection
    row.visibility=if(showCard)View.VISIBLE else View.GONE
    if(showCard) {
        pageHome.findViewById<TextView>(R.id.playback_source_title).text=
            selectedPackage?.let { playerLabel(it) } ?: np?.appLabel ?: getString(R.string.player_choice_none)
        pageHome.findViewById<TextView>(R.id.playback_capabilities).apply {
            text=if(np!=null)PlaybackApp(np.pkg,np.appLabel.orEmpty(),np.controller).description(this@refreshUpgradeUi)
                else getString(status.messageResource())
            visibility=View.VISIBLE
        }
        row.findViewWithTag<ImageView>("player_source_icon")?.setImageDrawable((selectedPackage ?: np?.pkg)?.let { playerIcon(it) })
    }
    AccessibilityUi.apply(window.decorView)
}
