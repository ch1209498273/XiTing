package com.lujinyu.xiting

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

/** Reapply after navigation or dynamic binding so selected state and labels stay current. */
internal object AccessibilityUi {
    private val generatedLabels = java.util.WeakHashMap<View, String>()
    fun apply(root: View) {
        fun text(view: View): String = when (view) {
            is TextView -> view.text.toString()
            is ViewGroup -> (0 until view.childCount).map { text(view.getChildAt(it)) }.filter { it.isNotBlank() }.joinToString(", ")
            else -> ""
        }
        fun visit(view: View) {
            if (view is ImageView && !view.isClickable && view.contentDescription.isNullOrBlank())
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (view.isClickable && view.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO) {
                val target = (48 * view.resources.displayMetrics.density + .5f).toInt()
                view.minimumWidth = maxOf(view.minimumWidth,target)
                view.minimumHeight = maxOf(view.minimumHeight,target)
                view.layoutParams?.let { params ->
                    var changed = false
                    if (params.width in 1 until target) { params.width = target; changed = true }
                    if (params.height in 1 until target) { params.height = target; changed = true }
                    if (changed) view.layoutParams = params
                }
                view.isFocusable = true
                if ((view.contentDescription.isNullOrBlank() || generatedLabels[view] == view.contentDescription?.toString()) && view !is TextView)
                    text(view).takeIf { it.isNotBlank() }?.let { view.contentDescription = it; generatedLabels[view] = it }
                if (Build.VERSION.SDK_INT >= 28 && view is ViewGroup) view.isScreenReaderFocusable = true
                if (view !is Button) view.accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host,info)
                        info.className = Button::class.java.name
                        info.isSelected = host.isSelected
                        if (Build.VERSION.SDK_INT >= 30 && host.isSelected)
                            info.stateDescription = host.context.getString(R.string.support_selected)
                    }
                }
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }
}
