package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class SupportPolicyTest {
    private val ready = SupportSnapshot(true,true,true,true,true,true,true,true,true,false)
    @Test fun missingOverlayWinsOverOtherIssues() {
        assertEquals(SupportRepair.OVERLAY, ready.copy(overlay=false,mediaAccess=false,session=false).suggestedRepair())
    }
    @Test fun mediaAccessDenialIsNotMistakenForNoPlayer() {
        assertEquals(SupportRepair.MEDIA_ACCESS, ready.copy(mediaAccess=false,session=false).suggestedRepair())
    }
    @Test fun stoppedPlaybackGoesToPlayerBeforeBatterySettings() {
        assertEquals(SupportRepair.PLAYER, ready.copy(playing=false,batteryExempt=false).suggestedRepair())
    }
    @Test fun missingServiceWithActivePlaybackOffersStart() {
        assertEquals(SupportRepair.START_ASSISTANT, ready.copy(assistant=false).suggestedRepair())
    }
    @Test fun incompleteOrNegativeFeedbackNeverClaimsSuccess() {
        for (sound in listOf(null,false,true)) for (returned in listOf(null,false,true)) {
            if(sound != true || returned != true) assertFalse(firstSuccessConfirmed(sound,returned))
        }
    }
    @Test fun bothUserConfirmationsAreRequired() { assertTrue(firstSuccessConfirmed(true,true)) }
}
