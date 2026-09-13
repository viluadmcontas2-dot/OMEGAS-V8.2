package com.omegas.prohub.blue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlueCausalAttributionTest {
    private val evaluator = BlueCausalAttribution()

    @Test
    fun `accepts one confirmed participating curve point and calculates measured gain`() {
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
    fun `accepts one confirmed participating map cell and keeps its exact address`() {
        val address = BlueActuatorAddress.mapCell(row = 4, column = 1)
        val result = evaluator.evaluate(
            intervention(address, CalibrationRevision(2, 4), CalibrationRevision(2, 5)),
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
                primary,
                CalibrationRevision(2, 4),
                CalibrationRevision(3, 4),
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
    fun `abstains when after evidence belongs to another physical region`() {
        val before = comparison("before", CalibrationRevision(2, 4), 4.40)
        val after = comparison(
            "after",
            CalibrationRevision(3, 4),
            4.08,
            rpm = 2_200.0,
            map = 0.80,
        )
        val result = evaluator.evaluate(
            intervention(BlueActuatorAddress.curvePoint(7), CalibrationRevision(2, 4), CalibrationRevision(3, 4)),
            before,
            after,
        )
        assertEquals(BlueAttributionState.ABSTAIN, result.state)
        assertEquals("REGION_MISMATCH", result.reason)
    }

    @Test
    fun `abstains when map cell did not participate`() {
        val result = evaluator.evaluate(
            intervention(BlueActuatorAddress.mapCell(3, 8), CalibrationRevision(2, 4), CalibrationRevision(2, 5)),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(2, 5), 4.08),
        )
        assertEquals("MAP_CELL_NOT_PARTICIPATING", result.reason)
    }

    @Test
    fun `abstains when curve point did not participate`() {
        val result = evaluator.evaluate(
            intervention(BlueActuatorAddress.curvePoint(20), CalibrationRevision(2, 4), CalibrationRevision(3, 4)),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 4), 4.08),
        )
        assertEquals("CURVE_POINT_NOT_PARTICIPATING", result.reason)
    }

    @Test
    fun `abstains when comparison revisions do not close intervention lineage`() {
        val result = evaluator.evaluate(
            intervention(BlueActuatorAddress.mapCell(4, 1), CalibrationRevision(2, 4), CalibrationRevision(2, 5)),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 5), 4.08),
        )
        assertEquals("REVISION_MISMATCH", result.reason)
    }

    @Test
    fun `abstains on zero K step or response in the wrong direction`() {
        val zero = evaluator.evaluate(
            intervention(BlueActuatorAddress.curvePoint(7), CalibrationRevision(2, 4), CalibrationRevision(3, 4), beforeK = 1.0, afterK = 1.0),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 4), 4.08),
        )
        val wrongDirection = evaluator.evaluate(
            intervention(BlueActuatorAddress.curvePoint(7), CalibrationRevision(2, 4), CalibrationRevision(3, 4), beforeK = 1.0, afterK = 1.1),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 4), 4.45),
        )
        assertEquals("INVALID_GAIN", zero.reason)
        assertEquals("INVALID_GAIN", wrongDirection.reason)
    }

    @Test
    fun `abstains without both ACK and full readback confirmation`() {
        val result = evaluator.evaluate(
            intervention(
                BlueActuatorAddress.curvePoint(7),
                CalibrationRevision(2, 4),
                CalibrationRevision(3, 4),
                readbackConfirmed = false,
            ),
            comparison("before", CalibrationRevision(2, 4), 4.40),
            comparison("after", CalibrationRevision(3, 4), 4.08),
        )
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
        scientificRegionId = BlueScientificRegion.from(1_500.0, 0.50).id,
    )

    private fun comparison(
        id: String,
        revision: CalibrationRevision,
        petrolOnCngMs: Double,
        rpm: Double = 1_500.0,
        map: Double = 0.50,
    ) = FuelComparison(
        id = id,
        revision = revision,
        petrolVisitId = "petrol",
        cngVisitId = id,
        rpm = rpm,
        mapBar = map,
        petrolTargetMs = 4.00,
        petrolOnCngMs = petrolOnCngMs,
        errorPercent = (petrolOnCngMs / 4.00 - 1.0) * 100.0,
        quality = 0.95,
        createdAtMs = if (id == "before") 9_000L else 11_000L,
        scientificRegionId = BlueScientificRegion.from(rpm, map).id,
    )
}
