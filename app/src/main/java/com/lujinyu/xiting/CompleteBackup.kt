package com.lujinyu.xiting

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** v3 additions: explicit whitelist, never import permissions, running state or media tokens. */
internal object CompleteBackup {
    private fun integer(v:Any?):Long?=when(v) { is Int->v.toLong();is Long->v;else->null }
    private val booleans=setOf(com.lujinyu.xiting.listening.ListeningStore.AUTO,com.lujinyu.xiting.listening.ListeningStore.MARK_SHOW,
        com.lujinyu.xiting.listening.ListeningStore.INTRO,"listening_clean_confirmed_v1",Prefs.DIRECT_UNLOCK,Prefs.BLACK_INFO_SHOW,Prefs.BLACK_TIMER_SHOW,Prefs.BLACK_MEDIA_CONTROLS,
        Prefs.SWITCH_HEADSET,Prefs.TIMER_PAUSE_KEEP_BLACK,Prefs.SUGGEST_NOTIFY,"auto_backup_enabled","session_recap_enabled",FirstSuccessFlow.COMPLETE)
    private val strings=setOf(Prefs.LANG,"appearance_mode_v1","home_entry_order_v1","listening_presets_v1")
    private val longs=setOf(Prefs.STATS_TOTAL_MS,Prefs.STATS_TOTAL_COUNT,Prefs.STATS_MAX_SINGLE,Prefs.LAST_RARE_DAY)
    private val sets=setOf(Prefs.LISTEN_DAYS,Prefs.MONTH_STATS)
    fun encode(p:SharedPreferences):JSONObject=JSONObject().also { out ->
        for((key,value) in p.all)if(key in booleans || key in strings || key in longs || key in sets)
            out.put(key,if(value is Set<*>)JSONArray(value.toList()) else value)
        (p.all["listening_scene_v1"] as? String)?.takeIf { it in setOf("sleep","episode","continuous") }?.let { out.put("listening_scene_v1",it) }
        (p.all[PlayerTimerDefaults.KEY] as? String)?.let(PlayerTimerDefaults::sanitize)?.let { out.put(PlayerTimerDefaults.KEY,it) }
        out.put(com.lujinyu.xiting.listening.ListeningStore.DAYS,(p.all[com.lujinyu.xiting.listening.ListeningStore.DAYS] as? Int)?.takeIf { it in setOf(0,30,90,180) } ?: 90)
        out.put(Prefs.ENERGY_CARRY_MS,p.getLong(Prefs.ENERGY_CARRY_MS,0L))
    }
    fun apply(raw:JSONObject?,ed:SharedPreferences.Editor) {
        if(raw==null)return
        (raw.opt(com.lujinyu.xiting.listening.ListeningStore.DAYS) as? Int)?.takeIf { it in setOf(0,30,90,180) }?.let {
            ed.putInt(com.lujinyu.xiting.listening.ListeningStore.DAYS,it)
        }
        for(key in booleans) (raw.opt(key) as? Boolean)?.let { ed.putBoolean(key,it) }
        for(key in strings) (raw.opt(key) as? String)?.takeIf { it.length<=8192 }?.let { ed.putString(key,it) }
        (raw.opt(PlayerTimerDefaults.KEY) as? String)?.let(PlayerTimerDefaults::sanitize)?.let { ed.putString(PlayerTimerDefaults.KEY,it) }
        (raw.opt("listening_scene_v1") as? String)?.takeIf { it in setOf("sleep","episode","continuous") }?.let { ed.putString("listening_scene_v1",it) }
        val aggregate=hasAggregate(raw)
        for(key in longs) if(key==Prefs.LAST_RARE_DAY || aggregate)
            integer(raw.opt(key))?.takeIf { it in 0..1_000_000_000_000_000L }?.let { ed.putLong(key,it) }
        integer(raw.opt(Prefs.ENERGY_CARRY_MS))?.takeIf { it in 0..59_999L }?.let { ed.putLong(Prefs.ENERGY_CARRY_MS,it) }
        for(key in sets)if(aggregate)raw.optJSONArray(key)?.let { array ->
            val values=LinkedHashSet<String>()
            for(i in 0 until minOf(array.length(),10000)) {
                val value=array.opt(i) as? String ?: continue
                val valid=if(key==Prefs.LISTEN_DAYS) value.toLongOrNull()?.let { it in -719162L..2932896L }==true
                    else validMonth(value)
                if(valid)values.add(value)
            }
            ed.putStringSet(key,values)
        }
    }
    private fun validMonth(value:String):Boolean {
        val fields=value.split(':');if(fields.size!=4 || !fields[0].matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")))return false
        return fields[1].toLongOrNull()?.let { it in 0..1_000_000_000_000_000L }==true &&
            fields[2].toIntOrNull()?.let { it in 0..1_000_000_000 }==true && fields[3].toLongOrNull()?.let { it in 0..86_400_000L }==true
    }
    fun hasAggregate(raw:JSONObject?):Boolean {
        if(raw==null)return false
        val total=integer(raw.opt(Prefs.STATS_TOTAL_MS)) ?: return false
        val count=integer(raw.opt(Prefs.STATS_TOTAL_COUNT)) ?: return false
        val longest=integer(raw.opt(Prefs.STATS_MAX_SINGLE)) ?: return false
        if(total !in 0..1_000_000_000_000_000L || count !in 0..1_000_000_000L || longest !in 0..86_400_000L)return false
        val days=raw.optJSONArray(Prefs.LISTEN_DAYS) ?: return false
        val months=raw.optJSONArray(Prefs.MONTH_STATS) ?: return false
        if(days.length()>10000 || days.length()>count || months.length()>1200)return false
        for(i in 0 until days.length()) {
            val day=days.opt(i) as? String ?: return false
            if(day.toLongOrNull()?.let { it in -719162L..2932896L }!=true)return false
        }
        var sum=0L;var num=0L;var max=0L;val seen=HashSet<String>()
        for(i in 0 until months.length()) {
            val item=months.opt(i) as? String ?: return false
            if(!validMonth(item))return false
            val f=item.split(':');if(!seen.add(f[0]))return false
            val ms=f[1].toLong();val n=f[2].toLong();val single=f[3].toLong()
            if(ms<single || (n==0L)!=(ms==0L) || (n>0 && single<60_000))return false
            sum+=ms;num+=n;max=maxOf(max,single)
        }
        return sum==total && num==count && max==longest
    }
    fun pending(raw:JSONArray,now:Long):JSONArray {
        val out=JSONArray();val seen=HashSet<Long>();var room=EnergyStore.MAX_PENDING
        for(i in 0 until minOf(raw.length(),200)) {
            val row=raw.optJSONObject(i) ?: continue
            val id=integer(row.opt("id")) ?: continue
            val value=integer(row.opt("v")) ?: continue
            val expires=integer(row.opt("e")) ?: continue
            if(id<=0 || id>253402300799999L || value<=0 || value>Int.MAX_VALUE || expires<=now ||
                expires>253402300799999L || !seen.add(id))continue
            if(room<=0)break
            val amount=minOf(value,room.toLong()).toInt();room-=amount
            out.put(JSONObject().put("id",id).put("v",amount).put("e",expires))
        }
        return out
    }
}

