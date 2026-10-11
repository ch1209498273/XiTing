package com.lujinyu.xiting

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Preview owns no growth/outfit state. Wearing remains in the gallery's unlocked flow. */
internal fun showPetPreview(context:Context,initialStage:Int=0):AlertDialog {
    val density=context.resources.displayMetrics.density
    fun dp(n:Int)=(n*density).toInt()
    val content=LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(4),dp(16),dp(12))
    }
    val label=TextView(context).apply {
        gravity=Gravity.CENTER;textSize=15f;setTextColor(context.getColor(R.color.brand))
    }
    val hint=TextView(context).apply {
        gravity=Gravity.CENTER;textSize=13f;setTextColor(context.getColor(R.color.text_secondary))
    }
    val pet=PetView(context).apply {
        id=R.id.pet_preview_live;previewOnly=true;hideProgress=true
        applySkin(PetSkins.active(context))
    }
    fun choose(stage:Int) {
        pet.stage=stage
        label.text=PetView.stageName(context,stage)
        hint.text=PetView.interactionHint(context,stage)
        pet.contentDescription="${label.text} · ${hint.text}"
        pet.startAnimating()
    }
    content.addView(label)
    content.addView(pet,LinearLayout.LayoutParams(-1,dp(230)))
    content.addView(hint)
    val row=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL }
    for(stage in 0..4) row.addView(Button(context).apply {
        text=PetView.stageName(context,stage);isAllCaps=false
        setOnClickListener { choose(stage) }
    })
    content.addView(HorizontalScrollView(context).apply { addView(row);isHorizontalScrollBarEnabled=false })
    content.addView(TextView(context).apply {
        text=context.getString(R.string.gallery_preview_only);textSize=12f
        setTextColor(context.getColor(R.color.text_secondary))
    })
    choose(initialStage.coerceIn(0,4))
    return AlertDialog.Builder(context).setTitle(R.string.gallery_try_all)
        .setView(ScrollView(context).apply { addView(content) })
        .setPositiveButton(R.string.dlg_ok,null).create().apply { show() }
}
