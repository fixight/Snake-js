package dev.dualsense.fix

import org.junit.Assert.*
import org.junit.Test

class RuntimeStateTest {
    @Test fun virtualDeviceAloneDoesNotMeanWorking() {
        val s=RuntimeState.parse("phase=waiting\nvirtual=1\ngrabbed=0\nphysical=Disconnected")
        assertTrue(s.busy);assertFalse(s.working);assertEquals("Ожидаем DualSense",s.title)
    }
    @Test fun runningRequiresPhysicalAndExclusiveGrab() {
        val s=RuntimeState.parse("phase=running\nvirtual=1\ngrabbed=1\nphysical=/dev/input/event14")
        assertTrue(s.working);assertEquals("Мост работает",s.title)
        assertFalse(s.copy(grabbed=false).working);assertFalse(s.copy(physical="Disconnected").working)
        assertFalse(s.copy(virtual=false).working)
    }
    @Test fun fallbackDiagnosticsAreNotForwarding() {
        val s=RuntimeState.parse("phase=diagnostics\ndiagnostic=1\nerror=EVIOCGRAB denied: errno=13\nphysical=/dev/input/event9")
        assertTrue(s.busy);assertFalse(s.working);assertTrue(s.diagnostic)
        assertEquals("EVIOCGRAB denied: errno=13",s.error);assertEquals("Диагностика · без перехвата",s.title)
    }
    @Test fun failureAndStopAreDifferent() {
        val s=RuntimeState.parse("phase=error\nerror=open /dev/uinput: Permission denied")
        assertFalse(s.busy);assertFalse(s.working);assertTrue(s.error.isNotEmpty())
        assertNotEquals(s.title,RuntimeState().title)
    }
    @Test fun acknowledgmentsAreStructuredAndMissingFieldsAreNotSuccess() {
        val s=RuntimeState.parse("phase=diagnostics\nlearning=1\nlearned=2\ntested=3")
        assertEquals(1,s.learning);assertEquals(2,s.learned);assertEquals(3,s.tested)
        assertFalse(RuntimeState.parse("").working);assertEquals("lost",RuntimeState.parse("").phase)
        assertEquals(0,RuntimeState.parse("tested=invalid").tested)
        assertEquals("lost",RuntimeState.parse("phase=unexpected").phase)
    }
    @Test fun diagnosticWaitingIsDistinctFromBridgeWaiting() {
        assertEquals("Диагностика · ждём DualSense",RuntimeState(phase="waiting",diagnostic=true).title)
        assertFalse(RuntimeState(phase="lost",virtual=true,grabbed=true,physical="x").working)
    }
}
