package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class SessionDiagnosticsTest {
    @Test fun structuredTextOnlyContainsApprovedFields() {
        val snapshot = SessionSnapshot(id="SECRET_ID", scene="SECRET_TITLE", timer=TimerPhase.FIXED,
            assistant=AssistantPhase.RUNNING, curtain=CurtainPhase.LOCKED, playback=PlaybackReport.UNKNOWN,
            pause=PauseReport.UNCONFIRMED, remainingMs=1200)
        val text = diagnosticText("5.0", 35, "device\nSECRET_FIELD=secret", SupportSnapshot(true,false,true,false,true,false,true,false,true,true),
            snapshot, WakeLockSnapshot(true, 50, 75))
        assertFalse(text.contains("SECRET_ID")); assertFalse(text.contains("SECRET_TITLE"))
        assertFalse(text.lines().any { it.startsWith("SECRET_FIELD=") })
        assertEquals(setOf("app.version","android.api","device.model","permission.overlay","permission.media_access",
            "permission.notifications","permission.battery_exempt","session.assistant","session.curtain","session.playback",
            "session.timer","session.pause","session.remaining_ms","wakelock.held","wakelock.held_ms","wakelock.total_ms"),
            text.lines().map { it.substringBefore('=') }.toSet())
        assertTrue(text.contains("session.playback=unknown")); assertTrue(text.contains("session.pause=unconfirmed"))
    }
    @Test fun longModelIsBoundedAndControlCharactersAreRemoved() {
        val text = diagnosticText("5.0", 35, "x".repeat(400)+"\r\n", SupportSnapshot(false,false,false,false,false,false,false,false,false,false),
            SessionSnapshot(), WakeLockSnapshot(false,0,0))
        assertEquals(160, text.lines().single { it.startsWith("device.model=") }.substringAfter('=').length)
        assertFalse(text.contains('\r'))
    }
}
