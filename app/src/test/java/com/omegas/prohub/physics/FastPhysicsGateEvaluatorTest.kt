package com.omegas.prohub.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastPhysicsGateEvaluatorTest {
    @Test fun `fast physics gate measures target signal coverage gain allocation and time to zero`() {
        val scenarios = listOf(
            FastPhysicsScenario(1.00, 1.12, 1.00, CorrectionMechanism.MAP_LOCAL),
            FastPhysicsScenario(1.15, 0.96, 0.85, CorrectionMechanism.CURVE_MUL_ACT),
            FastPhysicsScenario(0.92, 1.04, 1.20, CorrectionMechanism.MAP_LOCAL),
        )
        val report = FastPhysicsGateEvaluator.evaluate(scenarios)
        assertEquals(scenarios.size, report.totalScenarios)
        assertTrue(report.meanTargetRelativeError < 1e-9)
        assertEquals(1.0, report.signalAccuracy, 1e-12)
        assertEquals(1.0, report.intervalCoverage, 1e-12)
        assertTrue(report.gainMeanAbsoluteError < 1e-9)
        assertTrue(report.allocationExclusive)
        assertTrue(report.maxExpectedStepsToTolerance in 1..20)
        assertEquals(0, report.falsePrecisionCount)
        assertTrue(report.pass)
    }

    @Test fun `unknown gain is counted as safe abstention not fabricated precision`() {
        val report = FastPhysicsGateEvaluator.evaluate(
            listOf(FastPhysicsScenario(1.0, 1.1, 1.0, CorrectionMechanism.MAP_LOCAL)),
        )
        assertTrue(report.unknownGainAbstains)
        assertEquals(0, report.falsePrecisionCount)
    }
}
