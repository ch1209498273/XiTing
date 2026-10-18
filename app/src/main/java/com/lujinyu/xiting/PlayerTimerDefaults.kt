package com.lujinyu.xiting

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener

/** Only package identity and timer choices are persisted; never media tokens or deadlines. */
internal object PlayerTimerDefaults {
    const val KEY = "player_timer_defaults_v1"
    private const val MAX_ENTRIES = 32
    private const val MAX_LENGTH = 8192
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    @Synchronized fun load(ctx: Context, pkg: String): ListeningPreset? {
        if (!validPackage(pkg)) return null
        return try {
            ctx.prefs().getString(KEY, null)?.let(::decode)?.get(pkg)
        } catch (_: Exception) { null }
    }

    /** Call only after the service accepts the timer for this player. */
    @Synchronized fun save(ctx: Context, pkg: String, preset: ListeningPreset): Boolean {
        if (!validPackage(pkg) || preset.minutes !in 1..600) return false
        return try {
            val prefs = ctx.prefs()
            val stored = prefs.getString(KEY, null)
            val current = if (stored == null) emptyMap() else decode(stored) ?: return false
            val next = updated(current, pkg, preset) ?: return false
            val raw = encode(next) ?: return false
            prefs.edit().putString(KEY, raw).commit()
        } catch (_: Exception) { false }
    }

    private fun validPackage(pkg: String): Boolean = pkg.length in 3..255 && packagePattern.matches(pkg)

    /** Null means invalid, distinct from a valid empty object so import can preserve local data. */
    fun decode(raw: String): Map<String, ListeningPreset>? {
        if (raw.length > MAX_LENGTH) return null
        return try {
            val tokener = JSONTokener(raw)
            val root = tokener.nextValue() as? JSONObject ?: return null
            if (tokener.nextClean() != '\u0000' || root.length() > MAX_ENTRIES) return null
            val result = linkedMapOf<String, ListeningPreset>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                if (!validPackage(pkg)) return null
                val row = root.opt(pkg) as? JSONObject ?: return null
                if (row.length() != 2 || !row.has("minutes") || !row.has("pauseKeepBlack")) return null
                val minutes = when (val value = row.opt("minutes")) {
                    is Int -> value.toLong()
                    is Long -> value
                    else -> return null
                }
                val keepBlack = row.opt("pauseKeepBlack") as? Boolean ?: return null
                if (minutes !in 1..600) return null
                result[pkg] = ListeningPreset(minutes.toInt(), keepBlack)
            }
            result
        } catch (_: Exception) { null }
    }

    fun encode(items: Map<String, ListeningPreset>): String? {
        if (items.size > MAX_ENTRIES) return null
        val root = JSONObject()
        for ((pkg, preset) in items) {
            if (!validPackage(pkg) || preset.minutes !in 1..600) return null
            root.put(pkg, JSONObject().put("minutes", preset.minutes).put("pauseKeepBlack", preset.pauseKeepBlack))
        }
        return root.toString().takeIf { it.length <= MAX_LENGTH }
    }

    fun sanitize(raw: String): String? = decode(raw)?.let(::encode)

    fun updated(items: Map<String, ListeningPreset>, pkg: String, preset: ListeningPreset): Map<String, ListeningPreset>? {
        if (!validPackage(pkg) || preset.minutes !in 1..600 || encode(items) == null) return null
        val next = LinkedHashMap(items)
        next.remove(pkg)
        next[pkg] = preset
        while (next.size > MAX_ENTRIES) next.remove(next.keys.first())
        // Long package names may reach the string limit before the entry limit.
        while (encode(next) == null && next.size > 1) next.remove(next.keys.first())
        return next.takeIf { encode(it) != null }
    }
}
