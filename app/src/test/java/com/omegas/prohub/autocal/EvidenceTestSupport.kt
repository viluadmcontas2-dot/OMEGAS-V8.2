package com.omegas.prohub.autocal

/** Ajudantes dos testes de evidência: pares espalhados por DENTRO da faixa e condução em visitas separadas por ≥ 60 s. */
object EvidenceTestSupport {
    /** Folga entre passagens: > [EvidencePairs.VISIT_GAP_MS], então cada passagem é uma visita distinta. */
    const val VISIT_GAP = 90_000L

    /** n ms espalhados por dentro da faixa [band] do livro (cobertura interna: uma ponta só não vale como a faixa). */
    fun interior(band: Int, n: Int): List<Double> {
        val (lo, hi) = EquivalenceLedger.BANDS[band]
        return List(n) { lo + (it + 0.5) / n * (hi - lo) }
    }

    /** n pares por faixa, espalhados por dentro dela, todos com a mesma razão GNV/gasolina. */
    fun pairsIn(ratio: Double, perBand: Int, bands: List<Int>): List<Pair<Double, Double>> =
        bands.flatMap { b -> interior(b, perBand).map { it to it * ratio } }

    /**
     * Duas células de condução por faixa do livro (rpm, MAP, ms de gasolina): a segunda cai noutro terço da faixa,
     * então a faixa tem cobertura interna. A curva de gasolina da ECU que passa por todas.
     */
    val CELLS_A = listOf(
        Triple(2_000.0, 0.40, 3.6), Triple(2_200.0, 0.50, 5.0), Triple(2_500.0, 0.60, 6.5),
        Triple(2_800.0, 0.70, 8.0), Triple(3_200.0, 0.85, 10.0),
    )
    val CELLS_B = listOf(
        Triple(2_100.0, 0.45, 4.3), Triple(2_300.0, 0.55, 5.7), Triple(2_600.0, 0.65, 7.2),
        Triple(2_900.0, 0.75, 8.7), Triple(3_300.0, 0.95, 11.3),
    )
    val ECU_CURVE: List<Pair<Double, Double>> =
        (listOf(0.30 to 2.5) + (CELLS_A + CELLS_B).map { it.second to it.third } + listOf(1.00 to 12.0)).sortedBy { it.first }
}
