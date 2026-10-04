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
    fun `snapshot callers cannot mutate cached state`() {
        val bus = RuntimeSnapshotBus()
        bus.publishPresent(JSONObject().put("fuel", "GNV"))
        val copy = bus.presentJson()
        copy.getJSONObject("data").put("fuel", "PETROL")
        assertEquals("GNV", bus.presentJson().getJSONObject("data").getString("fuel"))
    }

    @Test
    fun `per-kind revisions move only when the data really changes`() {
        val bus = RuntimeSnapshotBus()
        val tables = RuntimeSnapshotBus.Kind.TABLES
        assertEquals(0L, bus.revision(tables))
        val first = bus.publishIfChanged(tables, "G2:AAAA|G3:BBBB")
        assertEquals(1L, first)
        // Mesma resposta da ECU (hash igual): a revisão NÃO se mexe.
        assertEquals(first, bus.publishIfChanged(tables, "G2:AAAA|G3:BBBB"))
        assertEquals(first, bus.revision(tables))
        // Resposta diferente: sobe uma vez.
        assertEquals(first + 1, bus.publishIfChanged(tables, "G2:CCCC|G3:BBBB"))
        // Os outros tipos são independentes.
        assertEquals(0L, bus.revision(RuntimeSnapshotBus.Kind.EVIDENCE))
        assertEquals(1L, bus.bump(RuntimeSnapshotBus.Kind.EVIDENCE))
        assertEquals(0L, bus.revision(RuntimeSnapshotBus.Kind.SESSION))
    }

    @Test
    fun `revisions json lists the four kinds and the live revision can be supplied`() {
        val bus = RuntimeSnapshotBus()
        bus.bump(RuntimeSnapshotBus.Kind.SESSION)
        val root = bus.revisionsJson(liveRevision = 42L)
        assertTrue(root.getBoolean("ok"))
        val revisions = root.getJSONObject("revisions")
        assertEquals(42L, revisions.getLong("live"))
        assertEquals(0L, revisions.getLong("evidence"))
        assertEquals(0L, revisions.getLong("tables"))
        assertEquals(1L, revisions.getLong("session"))
        assertEquals(RuntimeSnapshotBus.Kind.TABLES, RuntimeSnapshotBus.Kind.fromWire(" Tables "))
        assertEquals(null, RuntimeSnapshotBus.Kind.fromWire("nada"))
        assertFalse(bus.changedSince(RuntimeSnapshotBus.Kind.SESSION, 1L))
        assertTrue(bus.changedSince(RuntimeSnapshotBus.Kind.SESSION, 0L))
        assertTrue(bus.changedSince(RuntimeSnapshotBus.Kind.SESSION, -1L))
    }

    @Test
    fun `push coalescer delivers at most one per kind every 100 ms with the newest revision`() {
        var now = 1_000L
        val scheduled = ArrayList<Pair<Long, () -> Unit>>()
        val delivered = ArrayList<Pair<RuntimeSnapshotBus.Kind, Long>>()
        val coalescer = RevisionPushCoalescer(
            clock = { now },
            schedule = { delay, task -> scheduled += delay to task },
            dispatch = { kind, revision -> delivered += kind to revision },
        )
        val tables = RuntimeSnapshotBus.Kind.TABLES
        coalescer.onRevision(tables, 1L)
        assertEquals("primeira entrega sem espera", 0L, scheduled.single().first)
        scheduled.removeAt(0).second.invoke()
        assertEquals(listOf(tables to 1L), delivered)

        now += 30
        coalescer.onRevision(tables, 2L)
        coalescer.onRevision(tables, 3L) // coalescido na mesma entrega marcada
        assertEquals(1, scheduled.size)
        assertEquals("espera o resto dos 100 ms", 70L, scheduled.single().first)
        now += 70
        scheduled.removeAt(0).second.invoke()
        assertEquals(tables to 3L, delivered.last())
        assertEquals(2, delivered.size)

        // Outro tipo não é atrasado pelo primeiro.
        coalescer.onRevision(RuntimeSnapshotBus.Kind.EVIDENCE, 7L)
        assertEquals(0L, scheduled.single().first)
    }
}
