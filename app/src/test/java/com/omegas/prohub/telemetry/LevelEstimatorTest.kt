package com.omegas.prohub.telemetry
import org.junit.Assert.*
import org.junit.Test
class LevelEstimatorTest {
    private fun known()=LevelEstimator.Config(listOf(10,60,110,160),210,1,LevelEstimator.Direction.INCREASING,1.0,0.1,listOf(3,12,37,62,87),true,true)
    @Test fun `unknown profile and missing full reference stay proxy without guessing endpoint`() {
        assertTrue(LevelEstimator().accept(100)!!.proxy)
        assertEquals(60,LevelEstimator().accept(100)!!.percent)
        assertTrue(LevelEstimator(known().copy(fullRaw=null)).accept(110,false)!!.proxy)
        assertTrue(LevelEstimator(known().copy(filterPolicyVerified=false)).accept(110,false)!!.proxy)
        assertTrue(LevelEstimator(known()).accept(110)!!.proxy)
        assertNull(LevelEstimator().accept(-1));assertNull(LevelEstimator().accept(256))
    }
    @Test fun `explicit verified five anchors map both directions without byte-extreme assumption`() {
        assertEquals(50,LevelEstimator(known()).accept(110,false)!!.percent)
        assertEquals(100,LevelEstimator(known()).accept(210,false)!!.percent)
        val inverse=known().copy(references=listOf(240,190,140,90),fullRaw=40,direction=LevelEstimator.Direction.DECREASING)
        assertEquals(50,LevelEstimator(inverse).accept(140,false)!!.percent)
        assertEquals(100,LevelEstimator(inverse).accept(40,false)!!.percent)
    }
    @Test fun `declared verified filter mode selects coefficient and leds require independent proof`() {
        val e=LevelEstimator(known());e.accept(10,true)
        val next=e.accept(110,false)!!;assertEquals(20.0,next.filteredRaw,1e-9);assertEquals(5,next.percent)
        assertEquals(2,LevelEstimator(known()).accept(110,false)!!.leds)
        assertNull(LevelEstimator(known().copy(ledPolicyVerified=false)).accept(110,false)!!.leds)
    }
}
