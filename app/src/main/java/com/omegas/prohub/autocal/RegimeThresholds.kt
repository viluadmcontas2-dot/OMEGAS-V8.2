package com.omegas.prohub.autocal

/** Fronteiras de regime do motor, numa só fonte para evidência, AutoIdle e StallWatch. */
object RegimeThresholds {
    /**
     * Abaixo de 1200 rpm a ECU está na estratégia de lenta: a gasolina injeta +20–30% de ms no MESMO MAP
     * (85 sessões reais). Por isso o pareamento GNV × gasolina nunca cruza esta fronteira, a lenta fica fora do
     * índice de condução, o AutoIdle chama de "lenta" o que está abaixo dela e o StallWatch só considera
     * "condução" o que está acima.
     */
    const val DRIVING_RPM = 1_200.0
}
