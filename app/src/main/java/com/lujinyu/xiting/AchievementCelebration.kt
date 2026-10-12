package com.lujinyu.xiting

import android.app.AlertDialog
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** One dialog per batch; replay never evaluates or writes achievement state. */
internal fun MainActivity.showBadgeCelebration(items:List<Achievements.A>,fresh:Boolean):AlertDialog {
    val d=resources.displayMetrics.density
    fun dp(v:Int)=(v*d).toInt()
    val content=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(4),dp(20),dp(8))
    }
    val heroes=mutableListOf<BadgeView>()
    for(a in items) {
        val badge=BadgeView(this).apply {
            id=R.id.badge_hero;heroMode=true;bind(a.badge,true)
            contentDescription=Achievements.title(this@showBadgeCelebration,a)
        }
        heroes.add(badge)
        content.addView(badge,LinearLayout.LayoutParams(-1,dp(156)))
        content.addView(TextView(this).apply {
            gravity=Gravity.CENTER;text=Achievements.title(this@showBadgeCelebration,a)
            textSize=19f;setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface,android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            gravity=Gravity.CENTER;text=Achievements.desc(this@showBadgeCelebration,a)
            textSize=13f;setTextColor(getColor(R.color.text_secondary))
        })
        val icon=IconStyle.OPTIONS.first { it.unlockAchievement==a.id }
        content.addView(android.widget.ImageView(this).apply {
            setImageResource(icon.iconRes)
            contentDescription=getString(R.string.icon_style_title)+" · "+Achievements.title(this@showBadgeCelebration,a)
        },LinearLayout.LayoutParams(dp(48),dp(48)).apply { gravity=Gravity.CENTER;topMargin=dp(12) })
        content.addView(Button(this).apply {
            text=getString(R.string.ach_replay);isAllCaps=false
            setOnClickListener { badge.playCelebration() }
        })
    }
    return AlertDialog.Builder(this)
        .setTitle(if(fresh)R.string.ach_celebration_title else R.string.ach_medal_title)
        .setView(ScrollView(this).apply { addView(content) })
        .setPositiveButton(R.string.dlg_ok,null).create().apply {
            setOnShowListener { heroes.forEach { badge -> badge.postDelayed({badge.playCelebration()},120) } }
            show()
        }
}

internal fun MainActivity.queueAchievementCelebration(items:List<Achievements.A>) {
    if(items.isEmpty())return
    val p=prefs()
    val ids=(p.getStringSet(Prefs.ACH_CELEBRATION_PENDING,emptySet())?:emptySet()).toMutableSet()
    ids.addAll(items.map{it.id})
    p.edit().putStringSet(Prefs.ACH_CELEBRATION_PENDING,ids).apply()
    window.decorView.post { showPendingAchievementCelebration() }
}

internal fun MainActivity.showPendingAchievementCelebration() {
    if(isFinishing || isDestroyed || !hasWindowFocus() || OverlayService.instance?.isAnyBlackShowing()==true)return
    val p=prefs()
    val ids=p.getStringSet(Prefs.ACH_CELEBRATION_PENDING,emptySet())?:emptySet()
    if(ids.isEmpty())return
    val unlocked=Achievements.unlockedIds(this)
    val items=Achievements.ALL.filter {it.id in ids && it.id in unlocked}
    if(items.isNotEmpty())showBadgeCelebration(items,true)
    p.edit().remove(Prefs.ACH_CELEBRATION_PENDING).apply()
}
