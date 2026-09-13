package com.omegas.prohub.blue

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlueCausalLedgerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `restart restores a pending isolated intervention`() {
        val file = temporary.newFile("blue-causal-ledger.json")
        val first = BlueCausalLedger(file)
        first.prepare(pendingIntent())

        val restored = BlueCausalLedger(file).pending("write-1")

        assertNotNull(restored)
        assertEquals(BlueActuatorAddress.curvePoint(7), restored?.actuator)
        assertEquals("comparison-before", restored?.beforeComparisonId)
    }

    @Test
    fun `legacy v1 ledger fails closed instead of restoring ambiguous identity`() {
        val file = temporary.newFile("legacy-blue-causal-ledger.json")
        file.writeText(
            JSONObject()
                .put("schema", "omegas-blue-causal-ledger-v1")
                .put("pending", JSONArray().put(JSONObject()
                    .put("id", "legacy")
                    .put("scientificRegionId", "visit-random-uuid")))
                .put("confirmed", JSONArray())
                .toString(),
        )

        val restored = BlueCausalLedger(file)

        assertNull(restored.pending("legacy"))
        assertNull(restored.latestConfirmed())
    }

    @Test
    fun `failed partial and multi writes can never become confirmed interventions`() {
        val file = temporary.newFile("blue-causal-ledger.json")
        val failed = BlueCausalLedger(file).apply { prepare(pendingIntent(id = "failed")) }
            .confirm(confirmation(id = "failed", ack = false))
        val partialLedger = BlueCausalLedger(file).apply { prepare(pendingIntent(id = "partial")) }
        val partial = partialLedger.confirm(confirmation(id = "partial", readback = false))
        val multiLedger = BlueCausalLedger(file).apply { prepare(pendingIntent(id = "multi")) }
        val multi = multiLedger.confirm(confirmation(
            id = "multi",
            changes = listOf(
                BlueConfirmedActuatorChange(BlueActuatorAddress.curvePoint(7), 1.0, 1.1),
                BlueConfirmedActuatorChange(BlueActuatorAddress.curvePoint(8), 1.0, 1.1),
            ),
        ))

        assertEquals(BlueLedgerState.ABSTAIN, failed.state)
        assertEquals("WRITE_NOT_CONFIRMED", failed.reason)
        assertEquals(BlueLedgerState.ABSTAIN, partial.state)
        assertEquals("WRITE_NOT_CONFIRMED", partial.reason)
        assertEquals(BlueLedgerState.ABSTAIN, multi.state)
        assertEquals("INTERVENTION_NOT_ISOLATED", multi.reason)
        assertNull(failed.intervention)
        assertNull(partial.intervention)
        assertNull(multi.intervention)
    }

    @Test
    fun `exact ACK and full readback close and persist the intervention`() {
        val file = temporary.newFile("blue-causal-ledger.json")
        val ledger = BlueCausalLedger(file)
        ledger.prepare(pendingIntent())

        val decision = ledger.confirm(confirmation())
        val restored = BlueCausalLedger(file)

        assertEquals(BlueLedgerState.CONFIRMED, decision.state)
        assertEquals(1.0, decision.intervention?.beforeK ?: 0.0, 1e-9)
        assertEquals(1.1, decision.intervention?.afterK ?: 0.0, 1e-9)
        assertNull(restored.pending("write-1"))
        assertNotNull(restored.confirmed("write-1"))
    }

    private fun pendingIntent(id: String = "write-1") = BluePendingIntervention(
        id = id,
        actuator = BlueActuatorAddress.curvePoint(7),
        beforeRevision = CalibrationRevision(2, 4),
        beforeK = 1.0,
        targetK = 1.1,
        beforeComparisonId = "comparison-before",
        scientificRegionId = BlueScientificRegion.from(1_500.0, 0.50).id,
        preparedAtMs = 9_500L,
    )

    private fun confirmation(
        id: String = "write-1",
        ack: Boolean = true,
        readback: Boolean = true,
        changes: List<BlueConfirmedActuatorChange> = listOf(
            BlueConfirmedActuatorChange(BlueActuatorAddress.curvePoint(7), 1.0, 1.1),
        ),
    ) = BlueInterventionConfirmation(
        id = id,
        afterRevision = CalibrationRevision(3, 4),
        ackConfirmed = ack,
        readbackConfirmed = readback,
        changes = changes,
        confirmedAtMs = 10_000L,
    )
}
