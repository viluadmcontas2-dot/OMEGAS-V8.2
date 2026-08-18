package com.omegas.prohub.learning

/**
 * Único adaptador entre o contexto físico armazenado no Learning e o selector
 * de referência de gasolina.
 *
 * Owner 075: água + temperatura do gás atravessam o pipeline com knownness e
 * provenance explícitos. Pressão já chega a esta fronteira para evitar outro
 * rewiring do MotorLearningMemory, mas permanece UNKNOWN/inativa até o owner 076.
 */
internal object PetrolReferenceEnvironmentBridge {
    fun region(
        waterC: Double,
        gasTemperatureC: Double,
        pressureDiffBar: Double,
    ): PetrolReferenceSelector.EnvironmentalContext = PetrolReferenceSelector.EnvironmentalContext(
        waterC = waterC.takeIf(Double::isFinite),
        waterFreshness = freshness(waterC, PetrolReferenceSelector.ContextFreshness.OBSERVED),
        waterSource = source(waterC, "LANDI_ECU_REGION"),
        gasTemperatureC = gasTemperatureC.takeIf(Double::isFinite),
        gasTemperatureFreshness = freshness(gasTemperatureC, PetrolReferenceSelector.ContextFreshness.OBSERVED),
        gasTemperatureSource = source(gasTemperatureC, "LANDI_ECU_REGION"),
        pressureDiffBar = pressureDiffBar.takeIf(Double::isFinite),
        pressureFreshness = PetrolReferenceSelector.ContextFreshness.UNKNOWN,
        pressureSource = "OWNER_076_PENDING",
        mapSource = "MP48_RUNTIME_REGION",
    )

    fun request(
        waterC: Double,
        gasTemperatureC: Double,
        pressureDiffBar: Double,
    ): PetrolReferenceSelector.EnvironmentalContext = PetrolReferenceSelector.EnvironmentalContext(
        waterC = waterC.takeIf(Double::isFinite),
        waterFreshness = freshness(waterC, PetrolReferenceSelector.ContextFreshness.CURRENT),
        waterSource = source(waterC, "LANDI_ECU_CURRENT"),
        gasTemperatureC = gasTemperatureC.takeIf(Double::isFinite),
        gasTemperatureFreshness = freshness(gasTemperatureC, PetrolReferenceSelector.ContextFreshness.CURRENT),
        gasTemperatureSource = source(gasTemperatureC, "LANDI_ECU_CURRENT"),
        pressureDiffBar = pressureDiffBar.takeIf(Double::isFinite),
        pressureFreshness = PetrolReferenceSelector.ContextFreshness.UNKNOWN,
        pressureSource = "OWNER_076_PENDING",
        mapSource = "MP48_RUNTIME_CURRENT",
    )

    private fun freshness(
        value: Double,
        whenKnown: PetrolReferenceSelector.ContextFreshness,
    ): PetrolReferenceSelector.ContextFreshness =
        if (value.isFinite()) whenKnown else PetrolReferenceSelector.ContextFreshness.UNKNOWN

    private fun source(value: Double, whenKnown: String): String =
        if (value.isFinite()) whenKnown else "UNKNOWN"
}
