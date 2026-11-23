package com.omegas.prohub.telemetry

import kotlin.math.abs

private fun near(a: Double?, b: Double, tol: Double = 1e-6) {
    check(a != null && abs(a - b) <= tol) { "Expected $b but got $a" }
}

fun main() {
    val a = CngVolumeCalibration.assess(emptyList(), 20.0, 160.0)
    check(a.state == "SEM_CALIBRACAO")
    check(a.consumedM3 == null && a.kmPerM3 == null)

    val good = listOf(
        CngFillCycle("A", 20.0, 10.0, comparableFullToFull = true, coverage = 0.99),
        CngFillCycle("B", 24.0, 12.0, comparableFullToFull = true, coverage = 0.99),
        CngFillCycle("C", 18.0, 9.0, comparableFullToFull = true, coverage = 0.99),
    )
    val s = CngVolumeCalibration.assess(good, 10.0, 80.0)
    check(s.state == "VALIDADO_POR_ABASTECIMENTO") { "state=${s.state}: $s" }
    near(s.consumedM3, 5.0)
    near(s.kmPerM3, 16.0)
    near(s.holdoutErrorPct, 0.0)
    check(s.referenceCycles == 3)
    check(s.isDirectMeasurement.not())

    val contaminated = good + CngFillCycle("D", 12.0, 90.0, comparableFullToFull = false, coverage = 0.99)
    check(CngVolumeCalibration.assess(contaminated, 10.0, 80.0).state == "VALIDADO_POR_ABASTECIMENTO")

    val gap = good.map { it.copy(coverage = 0.5) }
    check(CngVolumeCalibration.assess(gap, 10.0, 80.0).consumedM3 == null)

    val inconsistent = good.dropLast(1) + CngFillCycle("C", 18.0, 30.0, comparableFullToFull = true, coverage = 0.99)
    val error = CngVolumeCalibration.assess(inconsistent, 10.0, 80.0)
    check(error.state == "DIVERGENCIA_MEDIDA")
    check(error.consumedM3 == null)

    val duplicate = good + good.last()
    val d = CngVolumeCalibration.assess(duplicate, 10.0, 80.0)
    check(d.referenceCycles == 3)

    val partial = CngVolumeCalibration.assess(good, 10.0, null)
    check(partial.consumedM3 != null && partial.kmPerM3 == null)
    check(CngVolumeCalibration.assess(good, Double.NaN, 12.0).consumedM3 == null)
    println("PASS: 7 essential GNV-volume calibration scenarios")
}
