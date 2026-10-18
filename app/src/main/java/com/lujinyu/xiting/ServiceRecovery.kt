package com.lujinyu.xiting

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.SystemClock
import android.provider.Settings

internal object RecoveryPolicy {
    fun remaining(deadline:Long,now:Long,savedBoot:Int,currentBoot:Int):Long =
        if(savedBoot!=currentBoot || deadline<=now)0 else (deadline-now).coerceAtMost(SleepTimerPolicy.MAX_DURATION_MS)
}

internal object ServiceRecovery {
    private const val ACTIVE="recovery_active"
    private const val NOTICE="recovery_notice"
    private const val DEADLINE="recovery_deadline"
    private const val EPISODE="recovery_episode"
    private const val BOOT="recovery_boot"
    private fun boot(ctx:Context)=Settings.Global.getInt(ctx.contentResolver,Settings.Global.BOOT_COUNT,-1)
    fun started(ctx:Context) {
        val p=ctx.prefs();val interrupted=p.getBoolean(ACTIVE,false)
        p.edit().putBoolean(ACTIVE,true).putBoolean(NOTICE,p.getBoolean(NOTICE,false)||interrupted).commit()
    }
    fun timer(ctx:Context,deadline:Long,episode:Boolean) {
        ctx.prefs().edit().putLong(DEADLINE,deadline).putBoolean(EPISODE,episode).putInt(BOOT,boot(ctx)).apply()
    }
    fun stopped(ctx:Context,wanted:Boolean) {
        ctx.prefs().edit().putBoolean(ACTIVE,false).putBoolean(NOTICE,wanted)
            .apply { if(!wanted)putLong(DEADLINE,0).putBoolean(EPISODE,false) }.commit()
    }
    fun showIfNeeded(activity:Activity):AlertDialog? {
        val p=activity.prefs()
        if(!activity.hasWindowFocus() || activity.isFinishing || OverlayService.instance?.isAnyBlackShowing()==true || !p.getBoolean(NOTICE,false))return null
        // A dialog is an explicit acknowledgement; never replay an old media request.
        p.edit().putBoolean(NOTICE,false).apply()
        val remaining=RecoveryPolicy.remaining(p.getLong(DEADLINE,0),SystemClock.elapsedRealtime(),p.getInt(BOOT,-2),boot(activity))
        val detail=when {
            p.getBoolean(EPISODE,false)->activity.getString(R.string.rel_recovery_episode)
            p.getLong(DEADLINE,0)>0 ->activity.getString(R.string.rel_recovery_timer,(remaining+59999)/60000)
            else ->""
        }
        return AlertDialog.Builder(activity).setTitle(R.string.rel_recovery_title)
            .setMessage(activity.getString(R.string.rel_recovery_message)+"\n\n"+detail)
            .setPositiveButton(R.string.rel_ack,null).show()
    }
}
