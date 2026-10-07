package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classe 1: snapshot completo parcial não apaga o que a sessão já sabia (ECU#5) e evidência de AutoMatch não se perde (ECU#6). */
class SnapshotCarryOverTest {
    @Test
    fun `campo que falhou no snapshot novo mantem o VALID anterior da mesma sessao`() {
        val previous = snapshot(7L, field("AUTO_CAL_ENABLE", "VALID", "01"), field("MUL_ACT", "VALID", "AA"))
        val fresh = snapshot(7L, field("AUTO_CAL_ENABLE", "TIMEOUT", ""), field("MUL_ACT", "VALID", "BB"))
        val merged = SnapshotCarryOver.mergeFields(previous, fresh, 7L)
        val fields = merged.getJSONArray("fields")
        val enable = (0 until fields.length()).map { fields.getJSONObject(it) }.first { it.getString("key") == "AUTO_CAL_ENABLE" }
        val mul = (0 until fields.length()).map { fields.getJSONObject(it) }.first { it.getString("key") == "MUL_ACT" }
        assertEquals("VALID", enable.getString("status"))
        assertTrue(enable.getBoolean("carriedFromPreviousSnapshot"))
        assertEquals("BB", mul.getString("rawPayloadHex"))
        assertFalse(mul.has("carriedFromPreviousSnapshot"))
        assertEquals(1, merged.getInt("carriedFieldCount"))
    }

    @Test
    fun `sessao diferente nao herda nada`() {
        val previous = snapshot(6L, field("AUTO_CAL_ENABLE", "VALID", "01"))
        val fresh = snapshot(7L, field("AUTO_CAL_ENABLE", "TIMEOUT", ""))
        val merged = SnapshotCarryOver.mergeFields(previous, fresh, 7L)
        assertEquals("TIMEOUT", merged.getJSONArray("fields").getJSONObject(0).getString("status"))
    }

    @Test
    fun `escalar nao lido mantem o valor anterior e lido substitui`() {
        assertEquals(1, SnapshotCarryOver.keepIfUnread(null, 1))
        assertEquals(0, SnapshotCarryOver.keepIfUnread(0, 1))
        assertNull(SnapshotCarryOver.keepIfUnread(null, null))
    }

    @Test
    fun `evidencia de AutoMatch guardada quando o snapshot nao roda entra na proxima tentativa`() {
        val pending = PendingAutoMatchEvidence()
        val first = NativeAutoMatchCounterTracker.Event(
            eventType = "NATIVE_AUTOMATCH_COUNTER_INCREMENT", sessionId = 1L, observedAtElapsedMs = 10L,
            beforeCount = 2, afterCount = 3, delta = 1, mulActChangeConfirmed = false,
        )
        pending.carry(first, countIncreased = true)
        val (merged, increased) = pending.merge(null, countIncreased = false)
        assertEquals(2, merged!!.beforeCount)
        assertTrue(increased)
        val later = first.copy(beforeCount = 3, afterCount = 4, delta = 1, observedAtElapsedMs = 20L)
        val (both, _) = pending.merge(later, countIncreased = true)
        assertEquals(2, both!!.beforeCount)
        assertEquals(4, both.afterCount)
        assertEquals(2, both.delta)
        pending.clear()
        assertNull(pending.merge(null, false).first)
    }

    private fun snapshot(session: Long, vararg fields: JSONObject) = JSONObject()
        .put("available", true)
        .put("usbSessionId", session)
        .put("fields", JSONArray().also { array -> fields.forEach { array.put(it) } })

    private fun field(key: String, status: String, hex: String) = JSONObject()
        .put("key", key).put("status", status).put("rawPayloadHex", hex)
}
