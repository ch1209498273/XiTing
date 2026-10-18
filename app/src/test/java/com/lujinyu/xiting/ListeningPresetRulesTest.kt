package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class ListeningPresetRulesTest {
    private fun rules(): Class<*> {
        val type = runCatching { Class.forName("com.lujinyu.xiting.ListeningPresetRules") }.getOrNull()
        assertNotNull("Preset validation and decoding are not implemented", type)
        return type!!
    }
    private fun decode(value: String): List<*> = rules().getMethod("decode", String::class.java)
        .invoke(rules().getField("INSTANCE").get(null), value) as List<*>

    @Test fun corruptAndOutOfRangeRowsNeverBecomeRunnablePresets() {
        assertEquals(0, decode("0:1|601:0|garbage|30:2|:1").size)
        assertEquals(2, decode("30:1|60:0").size)
    }
    @Test fun duplicateRowsCollapseWithoutDroppingDifferentEndActions() {
        assertEquals(2, decode("30:1|30:1|30:0").size)
    }
    @Test fun entryOrderRejectsUnknownAndDuplicateKeysThenRestoresMissingEntries() {
        val type = runCatching { Class.forName("com.lujinyu.xiting.HomeEntryOrderRules") }.getOrNull()
        assertNotNull("Home entry ordering is not implemented", type)
        val result = type!!.getMethod("normalize", String::class.java)
            .invoke(type.getField("INSTANCE").get(null), "demo,timer,demo,unknown")
        assertEquals(listOf("demo", "timer", "player", "permissions"), result)
    }
}
