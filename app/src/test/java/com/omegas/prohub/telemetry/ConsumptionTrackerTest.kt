package com.omegas.prohub.telemetry

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ConsumptionTrackerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        context = mock(Context::class.java)
        prefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)
        
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putFloat(anyString(), org.mockito.ArgumentMatchers.anyFloat())).thenReturn(editor)
        `when`(editor.putInt(anyString(), anyInt())).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(editor)
        `when`(prefs.getInt("last_stable_raw_level", -1)).thenReturn(-1)
        `when`(prefs.getFloat("remaining_m3", -1f)).thenReturn(-1f)
        `when`(prefs.getFloat("learned_capacity_m3", -1f)).thenReturn(-1f)
    }

    @Test
    fun testRaw0IsFull() {
        val tracker = ConsumptionTracker(context)
        tracker.update(0, 0)
        val json = tracker.buildTelemetryJson(10f)
        assertEquals(0.0, json.getDouble("empty_index"), 0.01)
        assertEquals(1.0, json.getDouble("remaining_index"), 0.01)
    }

    @Test
    fun testRaw255IsEmpty() {
        val tracker = ConsumptionTracker(context)
        tracker.update(0, 255)
        val json = tracker.buildTelemetryJson(10f)
        assertEquals(1.0, json.getDouble("empty_index"), 0.01)
        assertEquals(0.0, json.getDouble("remaining_index"), 0.01)
    }

    @Test
    fun testRawIncreaseReducesRemaining() {
        val tracker = ConsumptionTracker(context)
        tracker.update(0, 100) // Initial
        val json1 = tracker.buildTelemetryJson(10f)
        val remain1 = json1.getDouble("remaining_index")

        tracker.update(1, 100) // Stabilize
        tracker.update(2, 120) // Decrease (raw increase)
        tracker.update(3, 120) // Stabilize
        tracker.update(4, 120) // Stabilize
        tracker.update(5, 120) // Stabilize
        tracker.update(6, 120) // Stabilize
        tracker.update(7, 120) // Stabilize
        tracker.update(8, 120) // Stabilize
        val json2 = tracker.buildTelemetryJson(10f)
        val remain2 = json2.getDouble("remaining_index")

        assertTrue("Increase of raw should reduce remaining", remain2 < remain1)
        assertFalse(tracker.refuelDetected)
    }

    @Test
    fun testPersistentDropSignalsRefuel() {
        val tracker = ConsumptionTracker(context)
        // start somewhat empty
        tracker.update(0, 200)
        for(i in 1..20) tracker.update(i.toLong(), 200)
        assertFalse(tracker.refuelDetected)
        
        // refuel -> raw drops to 50
        for(i in 21..40) tracker.update(i.toLong(), 50)
        assertTrue("Persistent drop of raw should signal refuel", tracker.refuelDetected)
    }

    @Test
    fun testIsolatedSpikeDoesNotSignalRefuel() {
        val tracker = ConsumptionTracker(context)
        tracker.update(0, 200)
        for(i in 1..20) tracker.update(i.toLong(), 200)
        assertFalse(tracker.refuelDetected)

        // Spike down
        tracker.update(21, 50)
        // Back to normal
        for(i in 22..40) tracker.update(i.toLong(), 200)
        
        assertFalse("Isolated spike should not signal refuel permanently", tracker.refuelDetected)
    }

    @Test
    fun testNoCalibrationRemainingM3Unavailable() {
        val tracker = ConsumptionTracker(context)
        tracker.update(0, 100)
        val json = tracker.buildTelemetryJson(10f)
        assertFalse("Without calibration, remaining_m3 should not be available", json.has("remaining_m3"))
    }
}
