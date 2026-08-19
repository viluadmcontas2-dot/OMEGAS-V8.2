package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalScale

/** Projeção humana read-only do AutoCal; não contém matemática de calibração nem writer. */
object NativeAutoCalHumanProjection {
    enum class HumanState {
        ACQUIRING_PETROL,
        ACQUIRING_CNG,
        AUTOMATCH_REVALIDATING,
        NATIVE_STATE_INSUFFICIENT,
        READY,
    }

    data class Point(
        val fuel: NativeAutoCalProgression.Fuel,
        val bandIndex: Int,
        val zone: Int,
        val tPetrolMs: Double?,
        val mapBar: Double?,
        val maturity: NativeAutoCalProgression.ZoneState,
        val positioned: Boolean,
        val correlatedAnchor: Boolean,
    )

    data class Projection(
        val state: HumanState,
        val message: String,
        val xAxis: String = "TPET_MS",
        val yAxis: String = "MAP_BAR",
        val acquisitionPoints: List<Point>,
        val reference30Keys: List<String>,
        val reference30Role: String = "SEPARATE_CURVE_REFERENCE_OVERLAY_ONLY",
        val curveKSeparateSurface: Boolean = true,
    )

    fun project(
        snapshot: NativeAutoCalProgression.Snapshot,
        autoMatchRevalidating: Boolean = false,
        correlatedBandKeys: Set<String> = emptySet(),
    ): Projection {
        val families = snapshot.acquisition18
        val petrol = families.firstOrNull { it.fuel == NativeAutoCalProgression.Fuel.PETROL }
        val gas = families.firstOrNull { it.fuel == NativeAutoCalProgression.Fuel.GAS }
        val insufficient = families.any { it.shapeState != NativeAutoCalProgression.ShapeState.KNOWN }
        val petrolComplete = petrol?.zones?.all { it.state == NativeAutoCalProgression.ZoneState.ACQUIRED } == true
        val gasComplete = gas?.zones?.all { it.state == NativeAutoCalProgression.ZoneState.ACQUIRED } == true

        val state = when {
            autoMatchRevalidating -> HumanState.AUTOMATCH_REVALIDATING
            insufficient -> HumanState.NATIVE_STATE_INSUFFICIENT
            !petrolComplete -> HumanState.ACQUIRING_PETROL
            !gasComplete -> HumanState.ACQUIRING_CNG
            else -> HumanState.READY
        }
        val message = when (state) {
            HumanState.ACQUIRING_PETROL -> "Adquirindo gasolina"
            HumanState.ACQUIRING_CNG -> "Adquirindo GNV"
            HumanState.AUTOMATCH_REVALIDATING -> "AutoMatch alterou calibração — revalidando"
            HumanState.NATIVE_STATE_INSUFFICIENT -> "Estado nativo insuficiente"
            HumanState.READY -> "Aquisição AutoCal pronta"
        }

        val points = families.flatMap { family ->
            family.bands.map { band ->
                val zoneState = family.zones.getOrNull(band.zone)?.state ?: NativeAutoCalProgression.ZoneState.UNKNOWN
                val positioned = band.coordinateState == NativeAutoCalProgression.CoordinateState.POSITIONED
                Point(
                    fuel = family.fuel,
                    bandIndex = band.index,
                    zone = band.zone,
                    tPetrolMs = band.petrolTimeRaw?.takeIf { positioned }?.let(AutoCalScale::injectionMs),
                    mapBar = band.mapRaw?.takeIf { positioned }?.let(AutoCalScale::mapBar),
                    maturity = zoneState,
                    positioned = positioned,
                    correlatedAnchor = "${family.fuel.name}:${band.index}" in correlatedBandKeys,
                )
            }
        }
        return Projection(
            state = state,
            message = message,
            acquisitionPoints = points,
            reference30Keys = snapshot.reference30.map { it.key },
        )
    }
}
