package com.lujinyu.xiting

/** A preset deliberately contains no player identity, session token or deadline. */
data class ListeningPreset(val minutes: Int, val pauseKeepBlack: Boolean)

object ListeningPresetRules {
    fun decode(raw: String): List<ListeningPreset> = raw.split('|').mapNotNull { row ->
        val parts = row.split(':')
        if (parts.size != 2) return@mapNotNull null
        val minutes = parts[0].toIntOrNull() ?: return@mapNotNull null
        if (minutes !in 1..600 || parts[1] !in listOf("0", "1")) return@mapNotNull null
        ListeningPreset(minutes, parts[1] == "1")
    }.distinct()

    fun encode(items: List<ListeningPreset>): String = items.filter { it.minutes in 1..600 }.distinct()
        .joinToString("|") { "${it.minutes}:${if (it.pauseKeepBlack) 1 else 0}" }
}

object HomeEntryOrderRules {
    val defaults = listOf("timer", "player", "demo", "permissions")
    fun normalize(raw: String): List<String> =
        (raw.split(',').filter { it in defaults } + defaults).distinct()
}
