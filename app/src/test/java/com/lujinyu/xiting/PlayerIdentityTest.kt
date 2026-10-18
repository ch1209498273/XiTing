package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerIdentityTest {
    @Test fun missingLabelUsesLocalizedPlayerName() {
        assertEquals("播放器", playerDisplayName("com.example.audio", null, "播放器"))
    }

    @Test fun blankLabelUsesLocalizedPlayerName() {
        assertEquals("Player", playerDisplayName("org.example.radio", "  ", "Player"))
    }

    @Test fun packageReturnedAsLabelUsesLocalizedPlayerName() {
        assertEquals("プレーヤー", playerDisplayName("com.example.music", "com.example.music", "プレーヤー"))
    }

    @Test fun genuineApplicationNameIsPreserved() {
        assertEquals("示例音乐", playerDisplayName("com.example.music", "示例音乐", "播放器"))
    }
}
