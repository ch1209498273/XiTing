package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class PlayerCatalogPolicyTest {
    @Test fun unsupportedInstalledPlayerRemainsVisibleBesidePausedSession() {
        val apps=listOf(PlayerCatalogEntry("qiyi","爱奇艺"),PlayerCatalogEntry("bili","哔哩哔哩"))
        val sessions=listOf(PlayerCatalogEntry("bili","哔哩哔哩",true,false,true))
        val result=mergePlayerCatalog(apps,sessions,"qiyi")
        assertEquals(listOf("qiyi","bili"),result.map { it.packageName })
        assertFalse(result.first().hasSession)
        assertEquals("爱奇艺",result.first().label)
        assertFalse(result.last().playing)
    }
    @Test fun sessionAddsCapabilityWithoutReplacingInstalledAppName() {
        val result=mergePlayerCatalog(listOf(PlayerCatalogEntry("video","视频应用")),
            listOf(PlayerCatalogEntry("video","stale label",true,true,true)),null)
        assertEquals(1,result.size)
        assertEquals("视频应用",result.single().label)
        assertTrue(result.single().hasSession && result.single().playing && result.single().canPause)
    }
    @Test fun selectedAppStaysExplicitEvenWhenAnotherAppIsPlaying() {
        val result=mergePlayerCatalog(listOf(PlayerCatalogEntry("selected","Selected")),
            listOf(PlayerCatalogEntry("other","Other",true,true,true)),"selected")
        assertEquals("selected",result.first().packageName)
        assertFalse(result.first().canPause)
        assertEquals("other",result.last().packageName)
    }
}
