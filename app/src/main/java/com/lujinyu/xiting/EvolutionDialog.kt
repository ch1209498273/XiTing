package com.lujinyu.xiting

import android.app.AlertDialog
import android.animation.ValueAnimator
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

internal fun MainActivity.maybeShowEvolution(stage:Int):Boolean {
    val seen=prefs().getInt(Prefs.LAST_SEEN_STAGE,-1)
    if(seen<0) { prefs().edit().putInt(Prefs.LAST_SEEN_STAGE,stage).apply();return false }
    if(stage<=seen) return false
    if(showEvolution(seen,stage)==null) return false
    prefs().edit().putInt(Prefs.LAST_SEEN_STAGE,stage).apply()
    return true
}

/** Presentation only; dismissal and disabled system animations both stop the finite transition. */
internal fun MainActivity.showEvolution(from:Int,to:Int):AlertDialog? {
    if(isFinishing || isDestroyed || !hasWindowFocus() || evolutionDialog?.isShowing==true || OverlayService.instance?.isAnyBlackShowing()==true) return null
    val d=resources.displayMetrics.density
    val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding((16*d).toInt(),0,(16*d).toInt(),(16*d).toInt()) }
    val pet=PetView(this).apply { previewOnly=true;hideProgress=true;stage=from;applySkin(PetSkins.active(this@showEvolution)) }
    body.addView(pet,LinearLayout.LayoutParams(-1,(220*d).toInt()))
    body.addView(TextView(this).apply { text=getString(R.string.evolve_fmt,PetView.stageName(this@showEvolution,from),PetView.stageName(this@showEvolution,to));textSize=18f;gravity=Gravity.CENTER;setTextColor(getColor(R.color.text_primary)) })
    val change=Runnable { pet.stage=to;pet.alpha=1f;pet.startAnimating() }
    val stop=Runnable { pet.thumbMode=true;pet.startAnimating() }
    return AlertDialog.Builder(this).setTitle(R.string.ui_growth_title).setView(body).setPositiveButton(R.string.dlg_ok,null).create().apply {
        evolutionDialog=this
        setOnShowListener {
            if(ValueAnimator.areAnimatorsEnabled()) { pet.startAnimating();pet.postDelayed(change,650);pet.postDelayed(stop,2400) }
            else { pet.stage=to;pet.thumbMode=true;pet.startAnimating() }
        }
        setOnDismissListener { pet.removeCallbacks(change);pet.removeCallbacks(stop);pet.animate().cancel();pet.thumbMode=true;pet.startAnimating();evolutionDialog=null }
        show()
    }
}
