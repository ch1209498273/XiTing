package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class CurrentPlaybackPolicyTest {
    @Test fun systemRoutedStoppedSessionIsRecognizedWhileAudioIsActive() {
        assertEquals("douyin",chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("douyin",1)),systemId="douyin",audioActive=true))
    }
    @Test fun systemAudioTargetBeatsStalePlayingStateFromAnotherApp() {
        assertEquals("douyin",chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("old",3),CurrentPlaybackCandidate("douyin",1)),systemId="douyin",audioActive=true))
    }
    @Test fun silentSystemTargetDoesNotResurrectStoppedOrPausedHistory() {
        for(state in listOf(0,1,2))assertNull(chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("old",state)),systemId="old",audioActive=false))
    }
    @Test fun missingOrErrorSystemTargetDoesNotInventAPlayer() {
        assertNull(chooseCurrentPlayback(emptyList(),systemId="missing",audioActive=true))
        assertNull(chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("error",7)),systemId="error",audioActive=true))
    }
    @Test fun unrelatedSoundCannotPromotePausedHistoryEvenIfSystemRetainsIt() {
        assertNull(chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("old",2)),systemId="old",audioActive=true))
    }
    @Test fun unreliableStateCannotOfferSeekOrArmPauseTimer() {
        assertFalse(playbackControlsReliable(0));assertFalse(playbackControlsReliable(1));assertFalse(playbackControlsReliable(7))
        assertTrue(playbackControlsReliable(3));assertTrue(playbackControlsReliable(2))
    }
    @Test fun pausedOldPlayerIsNotCurrent() {
        assertNull(chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("bili",2))))
    }
    @Test fun followsOnlyPlayingSourceInsteadOfPausedOldSource() {
        assertEquals("new",chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("bili",2),CurrentPlaybackCandidate("new",3))))
    }
    @Test fun conflictingPlaybackDisablesControl() {
        assertNull(chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("a",3),CurrentPlaybackCandidate("b",3))))
    }
    @Test fun curtainCanResumeItsPausedSourceButNotNoneOrDestroyedSource() {
        assertEquals("a",chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("a",2)),"a"))
        assertNull(chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("a",0)),"a"))
        assertNull(chooseCurrentPlayback(emptyList(),"a"))
    }
    @Test fun newlyPlayingSourceTakesPriorityOverPausedCurtainSource() {
        assertEquals("b",chooseCurrentPlayback(listOf(CurrentPlaybackCandidate("a",2),CurrentPlaybackCandidate("b",3)),"a"))
    }
}
