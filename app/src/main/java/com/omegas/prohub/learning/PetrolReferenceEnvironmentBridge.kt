package com.omegas.prohub.learning

/**
 * Único adaptador entre o contexto físico armazenado no Learning e o selector
 * de referência de gasolina.
 *
 * Água e temperatura do gás atravessam como contexto OMEGAS. Pressão
 * diferencial/MAP carregam provenance `NATIVE_ANCHORED` porque a aquisição
 * nativa MP48 possui evidência E4 de zoneamento/limites de pressão. Isso não
 * transforma pressão em writer nem afirma fórmula física além do observado.
 */
internal object PetrolReferenceEnvironmentBridge {
    private val pressureRegionAuthority = ScientificAuthorityRegistry
        .nativeAnchored("MP48_PRESSURE_DIFF_REGION", "E4")
    private val pressureCurrentAuthority = ScientificAuthorityRegistry
        .nativeAnchored("MP48_PRESSURE_DIFF_CURRENT", "E4")
    private val mapAuthority = ScientificAuthorityRegistry
        .nativeAnchored("MP48_RUNTIME_MAP", "E4")

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
        pressureFreshness = freshness(pressureDiffBar, PetrolReferenceSelector.ContextFreshness.OBSERVED),
        pressureSource = source(pressureDiffBar, pressureRegionAuthority.token()),
        mapSource = mapAuthority.token(),
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
        pressureFreshness = freshness(pressureDiffBar, PetrolReferenceSelector.ContextFreshness.CURRENT),
        pressureSource = source(pressureDiffBar, pressureCurrentAuthority.token()),
        mapSource = mapAuthority.token(),
    )

    private fun freshness(
        value: Double,
        whenKnown: PetrolReferenceSelector.ContextFreshness,
    ): PetrolReferenceSelector.ContextFreshness =
        if (value.isFinite()) whenKnown else PetrolReferenceSelector.ContextFreshness.UNKNOWN

    private fun source(value: Double, whenKnown: String): String =
        if (value.isFinite()) whenKnown else "UNKNOWN"
}
