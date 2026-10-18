package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryExportTest {
    @Test fun emptyExportKeepsStableHeaders() {
        assertEquals("start_epoch_ms,end_epoch_ms,duration_ms,mode\r\n",HistoryExport.csv(emptyList()))
    }
    @Test fun exportUsesRawMillisecondsAndModeWithoutLosingPrecision() {
        assertEquals("start_epoch_ms,end_epoch_ms,duration_ms,mode\r\n123,999,876,1\r\n",
            HistoryExport.csv(listOf(ListenSession(123,999,876,1))))
    }
}
