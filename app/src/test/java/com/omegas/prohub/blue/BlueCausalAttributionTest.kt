package com.omegas.prohub.blue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlueCausalAttributionTest {
    private val evaluator = BlueCausalAttribution()

    @Test
    fun `accepts one confirmed curve point and calculates measured gain`() {
        val result = evaluator.evaluate(
            intervention(
                actuator = BlueActuatorAddress.curvePoint(7),
                beforeRevision = CalibrationRevision(2, 4),
                afterRevision = CalibrationRevision(3, 4),
            ),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 4), 4.08),
        )

        assertEquals(BlueAttributionState.ACCEPTED, result.state)
        assertEquals(BlueActuatorKind.CURVE_POINT, result.observation?.actuator?.kind)
        assertNotNull(result.observation?.gain)
    }

    @Test
    fun `accepts one confirmed map cell and keeps its exact address`() {
        val address = BlueActuatorAddress.mapCell(row = 3, column = 8)
        val result = evaluator.evaluate(
            intervention(
                actuator = address,
                beforeRevision = CalibrationRevision(2, 4),
                afterRevision = CalibrationRevision(2, 5),
            ),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(2, 5), 4.08),
        )

        assertEquals(BlueAttributionState.ACCEPTED, result.state)
        assertEquals(address, result.observation?.actuator)
    }

    @Test
    fun `abstains when writer changed more than one actuator`() {
        val primary = BlueActuatorAddress.curvePoint(7)
        val result = evaluator.evaluate(
            intervention(
                actuator = primary,
                beforeRevision = CalibrationRevision(2, 4),
                afterRevision = CalibrationRevision(3, 4),
                changedActuators = listOf(primary, BlueActuatorAddress.curvePoint(8)),
            ),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 4), 4.08),
        )

        assertEquals(BlueAttributionState.ABSTAIN, result.state)
        assertEquals("INTERVENTION_NOT_ISOLATED", result.reason)
        assertNull(result.observation)
    }

    @Test
    fun `abstains when after evidence belongs to another scientific region`() {
        val result = evaluator.evaluate(
            intervention(
                actuator = BlueActuatorAddress.curvePoint(7),
                beforeRevision = CalibrationRevision(2, 4),
                afterRevision = CalibrationRevision(3, 4),
            ),
            comparison("before", CalibrationRevision(2, 4), 4.40, region = "rpm1500-map050"),
            comparison("after", CalibrationRevision(3, 4), 4.08, region = "rpm2200-map080"),
        )

        assertEquals(BlueAttributionState.ABSTAIN, result.state)
        assertEquals("REGION_MISMATCH", result.reason)
    }

    @Test
    fun `abstains when comparison revisions do not close intervention lineage`() {
        val result = evaluator.evaluate(
            intervention(
                actuator = BlueActuatorAddress.mapCell(3, 8),
                beforeRevision = CalibrationRevision(2, 4),
                afterRevision = CalibrationRevision(2, 5),
            ),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 5), 4.08),
        )

        assertEquals(BlueAttributionState.ABSTAIN, result.state)
        assertEquals("REVISION_MISMATCH", result.reason)
    }

    @Test
    fun `abstains on zero K step or response in the wrong direction`() {
        val zero = evaluator.evaluate(
            intervention(
                actuator = BlueActuatorAddress.curvePoint(7),
                beforeRevision = CalibrationRevision(2, 4),
                afterRevision = CalibrationRevision(3, 4),
                beforeK = 1.0,
                afterK = 1.0,
            ),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 4), 4.08),
        )
        val wrongDirection = evaluator.evaluate(
            intervention(
                actuator = BlueActuatorAddress.curvePoint(7),
                beforeRevision = CalibrationRevision(2, 4),
                afterRevision = CalibrationRevision(3, 4),
                beforeK = 1.0,
                afterK = 1.1,
            ),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 4), 4.80),
        )

        assertEquals("INVALID_GAIN", zero.reason)
        assertEquals("INVALID_GAIN", wrongDirection.reason)
        assertNull(zero.observation)
        assertNull(wrongDirection.observation)
    }

    @Test
    fun `abstains without both ACK and full readback confirmation`() {
        val result = evaluator.evaluate(
            intervention(
                actuator = BlueActuatorAddress.curvePoint(7),
                beforeRevision = CalibrationRevision(2, 4),
                afterRevision = CalibrationRevision(3, 4),
                readbackConfirmed = false,
            ),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 4), 4.08),
        )

        assertEquals(BlueAttributionState.ABSTAIN, result.state)
        assertEquals("WRITE_NOT_CONFIRMED", result.reason)
    }

    private fun intervention(
        actuator: BlueActuatorAddress,
        beforeRevision: CalibrationRevision,
        afterRevision: CalibrationRevision,
        beforeK: Double = 1.0,
        afterK: Double = 1.1,
        changedActuators: List<BlueActuatorAddress> = listOf(actuator),
        readbackConfirmed: Boolean = true,
    ) = BlueCausalIntervention(
        id = "write-1",
        actuator = actuator,
        beforeRevision = beforeRevision,
        afterRevision = afterRevision,
        beforeK = beforeK,
        afterK = afterK,
        ackConfirmed = true,
        readbackConfirmed = readbackConfirmed,
        changedActuators = changedActuators,
        confirmedAtMs = 10_000L,
    )

    private fun comparison(
        id: String,
        revision: CalibrationRevision,
        petrolOnCngMs: Double,
        region: String = "rpm1500-map050",
    ) = FuelComparison(
        id = id,
        revision = revision,
        petrolVisitId = "petrol",
        cngVisitId = id,
        rpm = 1500.0,
        mapBar = 0.50,
        petrolTargetMs = 4.00,
        petrolOnCngMs = petrolOnCngMs,
        errorPercent = (petrolOnCngMs / 4.00 - 1.0) * 100.0,
        quality = 0.95,
        createdAtMs = if (id == "before") 9_000L else 11_000L,
        scientificRegionId = region,
    )
}
