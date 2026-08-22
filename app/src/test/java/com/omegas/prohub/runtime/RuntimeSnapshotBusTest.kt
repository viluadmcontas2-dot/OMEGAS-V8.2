package com.omegas.prohub.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSnapshotBusTest {
    @Test
    fun `present snapshot is latest only and revisioned`() {
        val bus = RuntimeSnapshotBus()
        bus.publishPresent(JSONObject().put("rpm", 1_200))
        val first = bus.presentJson()
        bus.publishPresent(JSONObject().put("rpm", 2_400))
        val second = bus.presentJson()

        assertEquals(1_200, first.getJSONObject("data").getInt("rpm"))
        assertEquals(2_400, second.getJSONObject("data").getInt("rpm"))
        assertTrue(second.getLong("revision") > first.getLong("revision"))
    }

    @Test
    fun `science revision changes only when token changes`() {
        val bus = RuntimeSnapshotBus()
        bus.publishScience(JSONObject().put("cells", 10), "rev-a")
        val first = bus.scienceJsonSince(0L)
        val revision = first.getLong("revision")

        bus.publishScience(JSONObject().put("cells", 99), "rev-a")
        val unchanged = bus.scienceJsonSince(revision)
        assertFalse(unchanged.getBoolean("changed"))
        assertEquals(revision, unchanged.getLong("revision"))

        bus.publishScience(JSONObject().put("cells", 11), "rev-b")
        val changed = bus.scienceJsonSince(revision)
        assertTrue(changed.getBoolean("changed"))
        assertEquals(11, changed.getJSONObject("data").getInt("cells"))
        assertTrue(changed.getLong("revision") > revision)
    }

    @Test
    fun `snapshot callers cannot mutate cached state`() {
        val bus = RuntimeSnapshotBus()
        bus.publishPresent(JSONObject().put("fuel", "GNV"))
        val copy = bus.presentJson()
        copy.getJSONObject("data").put("fuel", "PETROL")
        assertEquals("GNV", bus.presentJson().getJSONObject("data").getString("fuel"))
    }
}
