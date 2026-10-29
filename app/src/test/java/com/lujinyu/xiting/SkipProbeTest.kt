package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 切集探针的纯判定与判死表：这两块决定「键是亮、是按不动、还是该转灰」。 */
class SkipProbeTest {
    @Before fun clearsProbeState() { SkipProbe.reset() }

    @Test fun signatureNeedsSomethingComparable() {
        assertEquals("第3集|id9", SkipProbePolicy.signature("第3集","id9"))
        assertEquals("第3集", SkipProbePolicy.signature("第3集",null))
        assertEquals("id9", SkipProbePolicy.signature(null,"id9"))
        assertNull("标题与 media id 都没有时不许下结论", SkipProbePolicy.signature(null,null))
        assertNull(SkipProbePolicy.signature("  ",""))
    }

    @Test fun declaredGoesStraightToTransport() {
        assertEquals(SkipRoute.TRANSPORT, SkipProbePolicy.route(declared=true, dead=false))
    }

    @Test fun undeclaredGetsOneInjectionTry() {
        assertEquals(SkipRoute.INJECT, SkipProbePolicy.route(declared=false, dead=false))
    }

    @Test fun judgedDeadNeverFiresAgainEvenIfDeclared() {
        assertEquals(SkipRoute.NOTHING, SkipProbePolicy.route(declared=false, dead=true))
        assertEquals("判死优先于声明：按过没响就不该再让人白亮 5 秒", SkipRoute.NOTHING,
            SkipProbePolicy.route(declared=true, dead=true))
    }

    @Test fun onlyUnchangedContentProvesDeath() {
        assertEquals(SkipVerdict.DEAD, SkipProbePolicy.verdict("第3集","第3集"))
        assertEquals(SkipVerdict.WORKED, SkipProbePolicy.verdict("第3集","第4集"))
        assertEquals("读不到签名不等于不支持", SkipVerdict.INCONCLUSIVE, SkipProbePolicy.verdict(null,"第4集"))
        assertEquals(SkipVerdict.INCONCLUSIVE, SkipProbePolicy.verdict("第3集",null))
    }

    @Test fun deathExpiresInsteadOfLastingForever() {
        SkipProbe.markDead("com.example.player", SkipProbePolicy.DIRECTION_PREVIOUS, 1_000L)
        assertTrue(SkipProbe.isDead("com.example.player", SkipProbePolicy.DIRECTION_PREVIOUS, 2_000L))
        assertFalse("过期后复活，别把播放器改版后的支持永久冤枉掉",
            SkipProbe.isDead("com.example.player", SkipProbePolicy.DIRECTION_PREVIOUS, 700_000L))
    }

    @Test fun deathIsPerDirectionAndPerPackage() {
        SkipProbe.markDead("com.example.player", SkipProbePolicy.DIRECTION_PREVIOUS, 1_000L)
        assertFalse(SkipProbe.isDead("com.example.player", SkipProbePolicy.DIRECTION_NEXT, 2_000L))
        assertFalse(SkipProbe.isDead("com.other.player", SkipProbePolicy.DIRECTION_PREVIOUS, 2_000L))
    }

    @Test fun newContentClearsPreviousVerdicts() {
        SkipProbe.noteContent("第3集")
        SkipProbe.markDead("com.example.player", SkipProbePolicy.DIRECTION_PREVIOUS, 1_000L)
        assertTrue(SkipProbe.isDead("com.example.player", SkipProbePolicy.DIRECTION_PREVIOUS, 2_000L))
        SkipProbe.noteContent("第3集")
        assertTrue("同一条内容不该因为刷新而重新赌一次",
            SkipProbe.isDead("com.example.player", SkipProbePolicy.DIRECTION_PREVIOUS, 3_000L))
        SkipProbe.noteContent("第4集")
        assertFalse("换内容即复活：上一条不响不代表这一条不响",
            SkipProbe.isDead("com.example.player", SkipProbePolicy.DIRECTION_PREVIOUS, 4_000L))
    }
}
