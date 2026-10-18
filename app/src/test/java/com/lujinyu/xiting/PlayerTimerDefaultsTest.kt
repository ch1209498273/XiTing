package com.lujinyu.xiting

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayerTimerDefaultsTest {
    @Test fun `player defaults round trip independently`() {
        val raw = """{"tv.danmaku.bili":{"minutes":30,"pauseKeepBlack":true},"com.example.player":{"minutes":600,"pauseKeepBlack":false}}"""
        assertNotNull(PlayerTimerDefaults.decode(raw))
        val decoded = PlayerTimerDefaults.decode(raw)!!
        assertEquals(ListeningPreset(30, true), decoded["tv.danmaku.bili"])
        assertEquals(ListeningPreset(600, false), decoded["com.example.player"])
        assertEquals(decoded, PlayerTimerDefaults.decode(PlayerTimerDefaults.encode(decoded)!!))
    }

    @Test fun `malformed and wrongly typed fields cannot become defaults`() {
        val invalid = listOf("", "[]", "{", "{} garbage",
            """{"com.player":{"minutes":"30","pauseKeepBlack":true}}""",
            """{"com.player":{"minutes":30.0,"pauseKeepBlack":true}}""",
            """{"com.player":{"minutes":30,"pauseKeepBlack":"false"}}""",
            """{"com.player":{"minutes":0,"pauseKeepBlack":true}}""",
            """{"com.player":{"minutes":601,"pauseKeepBlack":true}}""",
            """{"bad package":{"minutes":30,"pauseKeepBlack":true}}""",
            """{"com.player":{"minutes":30,"pauseKeepBlack":true,"token":"private"}}""")
        for (raw in invalid) assertNull(raw, PlayerTimerDefaults.decode(raw))
    }

    @Test fun `oversized payloads and excess entries are rejected`() {
        assertNull(PlayerTimerDefaults.decode(" ".repeat(8193)))
        val rows = (0..32).associate { "com.player.p$it" to ListeningPreset(30, true) }
        assertNull(PlayerTimerDefaults.encode(rows))
        val raw = JSONObject()
        rows.forEach { (key, _) -> raw.put(key, JSONObject().put("minutes", 30).put("pauseKeepBlack", true)) }
        assertNull(PlayerTimerDefaults.decode(raw.toString()))
        val longRows = (0..31).associate { "com." + "a".repeat(240) + it to ListeningPreset(30, true) }
        assertNull(PlayerTimerDefaults.encode(longRows))
    }

    @Test fun `updating a player retains others and evicts oldest only when full`() {
        val original = (0..31).associateTo(linkedMapOf()) { "com.player.p$it" to ListeningPreset(30, true) }
        assertNotNull(PlayerTimerDefaults.updated(original, "com.player.p0", ListeningPreset(1, false)))
        val updated = PlayerTimerDefaults.updated(original, "com.player.p0", ListeningPreset(1, false))!!
        assertEquals(32, updated.size)
        assertEquals(ListeningPreset(1, false), updated["com.player.p0"])
        val added = PlayerTimerDefaults.updated(updated, "com.player.new", ListeningPreset(60, true))!!
        assertEquals(32, added.size)
        assertNull(added["com.player.p1"])
        assertEquals(ListeningPreset(1, false), added["com.player.p0"])
        assertNull(PlayerTimerDefaults.updated(updated, "", ListeningPreset(30, true)))
        assertNull(PlayerTimerDefaults.updated(updated, "com.player", ListeningPreset(0, true)))
    }

    @Test fun `backup import normalizes valid optional JSON and preserves local on damaged or absent field`() {
        val local = mutableMapOf<String, Any>(PlayerTimerDefaults.KEY to "existing")
        val editor = editor(local)
        CompleteBackup.apply(JSONObject(), editor)
        assertEquals("existing", local[PlayerTimerDefaults.KEY])
        for (bad in listOf<Any>("broken", "[]", 30, " ".repeat(8193),
            """{"com.player":{"minutes":0,"pauseKeepBlack":true}}""")) {
            CompleteBackup.apply(JSONObject().put(PlayerTimerDefaults.KEY, bad), editor)
            assertEquals("existing", local[PlayerTimerDefaults.KEY])
        }
        CompleteBackup.apply(JSONObject().put(PlayerTimerDefaults.KEY,
            """ { "com.player" : { "minutes" : 60, "pauseKeepBlack" : false } } """), editor)
        val stored = local[PlayerTimerDefaults.KEY] as String
        assertFalse(stored.startsWith(" "))
        assertEquals(mapOf("com.player" to ListeningPreset(60, false)), PlayerTimerDefaults.decode(stored))
    }

    @Test fun `backup export includes validated defaults and omits damaged defaults`() {
        val raw = """{"com.player":{"minutes":60,"pauseKeepBlack":true}}"""
        assertTrue(CompleteBackup.encode(preferences(mapOf(PlayerTimerDefaults.KEY to raw))).has(PlayerTimerDefaults.KEY))
        assertEquals(mapOf("com.player" to ListeningPreset(60, true)),
            PlayerTimerDefaults.decode(CompleteBackup.encode(preferences(mapOf(PlayerTimerDefaults.KEY to raw)))
                .getString(PlayerTimerDefaults.KEY)))
        assertFalse(CompleteBackup.encode(preferences(mapOf(PlayerTimerDefaults.KEY to "broken")))
            .has(PlayerTimerDefaults.KEY))
    }

    private fun editor(values: MutableMap<String, Any>): SharedPreferences.Editor =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString", "putBoolean", "putLong", "putStringSet" -> { values[args!![0] as String] = args[1]; proxy }
                "commit" -> true
                "apply" -> null
                else -> proxy
            }
        } as SharedPreferences.Editor

    private fun preferences(values: Map<String, Any>): SharedPreferences =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getAll" -> values
                "getLong" -> values[args!![0]] ?: args[1]
                else -> null
            }
        } as SharedPreferences
}
