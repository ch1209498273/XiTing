package com.lujinyu.xiting

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Specific rows stay beside related v4 settings, without another tools menu. */
internal fun MainActivity.bindReliabilityUi() {
    fun addAfter(anchor:Int,title:Int,tagValue:String,action:()->Unit) {
        val old=pageSettings.findViewById<View>(anchor)
        val parent=old.parent as LinearLayout
        val d=resources.displayMetrics.density
        val row=LinearLayout(this).apply {
            tag=tagValue;orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            minimumHeight=(56*d).toInt();isClickable=true;isFocusable=true
            setPadding(0,(10*d).toInt(),0,(10*d).toInt())
            val value=android.util.TypedValue()
            if(theme.resolveAttribute(android.R.attr.selectableItemBackground,value,true))setBackgroundResource(value.resourceId)
            addView(TextView(this@bindReliabilityUi).apply {
                text=getString(title);textSize=15f;setTextColor(getColor(R.color.text_primary))
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(TextView(this@bindReliabilityUi).apply {
                text="›";textSize=22f;setTextColor(getColor(R.color.text_hint));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            setOnClickListener { action() }
        }
        parent.addView(row,parent.indexOfChild(old)+1)
    }
    addAfter(R.id.row_lang,R.string.fix_theme,"setting_theme") { showAppearanceSettings() }
    addAfter(R.id.row_restore,R.string.rel_backup_manage,"setting_backups") { BackupManagement.show(this) { postRefresh();recreate() } }
}
