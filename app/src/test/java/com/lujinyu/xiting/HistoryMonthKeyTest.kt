package com.lujinyu.xiting

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMonthKeyTest {
    @Test fun formatterCacheHonorsTimezoneChange() {
        val original=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT-08:00"))
            assertEquals("1969-12",HistoryStore.monthKey(0))
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"))
            assertEquals("1970-01",HistoryStore.monthKey(0))
        } finally { TimeZone.setDefault(original) }
    }
}
