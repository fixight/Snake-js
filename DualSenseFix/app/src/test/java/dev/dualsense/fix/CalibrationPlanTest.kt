package dev.dualsense.fix

import org.junit.Assert.*
import org.junit.Test

class CalibrationPlanTest {
    @Test fun quickModeCoversEveryDirectionWithoutTouchingSticks() {
        val steps=CalibrationPlan.steps("dpad")
        assertEquals(listOf(13,14,15,16),steps.map { it.target })
        assertTrue(steps.all { it.type==4 });assertEquals(4,steps.size)
    }
    @Test fun fullModeIncludesEveryButtonTriggerAndStick() {
        val all=CalibrationPlan.steps("full")
        assertEquals(21,all.size);assertEquals(all.size,all.map { it.id }.distinct().size)
        assertEquals(setOf(0,1,2,3,4,5,8,9,10,11,12),all.filter { it.type==1 }.map { it.target }.toSet())
        assertEquals(setOf(0,1,2,3,4,5),all.filter { it.type==3 }.map { it.target }.toSet())
    }
    @Test fun standardModeKeepsStickAxesUnchanged() {
        assertEquals(setOf(2,5),CalibrationPlan.steps("buttons").filter { it.type==3 }.map { it.target }.toSet())
    }
    @Test fun dpadAcceptsButtonsAndDirectionalHatBindings() {
        val right=CalibrationPlan.dpad.last()
        assertEquals("k:547",CalibrationPlan.binding("k 547 16",right))
        assertEquals("h:16:1",CalibrationPlan.binding("h 16 1 0 16",right))
        assertNotEquals(CalibrationPlan.binding("h 16 -1 0 15",CalibrationPlan.dpad[2]),CalibrationPlan.binding("h 16 1 0 16",right))
    }
    @Test fun unconfirmedAndMalformedMappingsAreNotTreatedAsLearned() {
        val right=CalibrationPlan.dpad.last()
        assertNull(CalibrationPlan.binding("a 16 16",right))
        assertNull(CalibrationPlan.binding("h 16 1 0 15",right))
        assertNull(CalibrationPlan.binding("h broken",right))
    }
}
