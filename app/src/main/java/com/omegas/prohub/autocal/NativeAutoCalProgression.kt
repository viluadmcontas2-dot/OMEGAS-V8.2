package com.omegas.prohub.autocal

/**
 * Modelo read-only da progressão nativa AutoCal.
 *
 * Aquisição física tem exatamente 18 bandas e quatro zonas. A família de 30
 * pontos de Curva K/referências permanece separada e nunca é convertida em
 * bandas. Índice sem Tpet+MAP fisicamente observados permanece UNPOSITIONED.
 */
object NativeAutoCalProgression {
    const val ACQUISITION_BANDS = 18
    const val ZONES = 4
    const val REFERENCE_POINTS = 30

    enum class Fuel {
        PETROL,
        GAS,
        GAS_PREVIOUS,
    }

    enum class CoordinateState {
        POSITIONED,
        UNPOSITIONED,
    }

    enum class ZoneState {
        UNKNOWN,
        NOT_ACQUIRED,
        ACQUIRED,
        RAW_OTHER,
    }

    enum class ShapeState {
        KNOWN,
        UNKNOWN,
    }

    data class BandPoint(
        val fuel: Fuel,
        val index: Int,
        val zone: Int,
        val counter: Int?,
        val petrolTimeRaw: Int?,
        val mapRaw: Int?,
        val coordinateState: CoordinateState,
    )

    data class ZoneProgress(
        val fuel: Fuel,
        val zone: Int,
        val rawFlag: Int?,
        val state: ZoneState,
    )

    data class AcquisitionFamily(
        val fuel: Fuel,
        val shapeState: ShapeState,
        val bands: List<BandPoint>,
        val zones: List<ZoneProgress>,
    ) {
        init {
            require(bands.size == ACQUISITION_BANDS)
            require(zones.size == ZONES)
        }
    }

    data class ReferenceVector(
        val key: String,
        val shapeState: ShapeState,
        val rawValues: IntArray?,
    ) {
        init {
            require(rawValues == null || rawValues.size == REFERENCE_POINTS)
        }
    }

    data class Snapshot(
        val acquisition18: List<AcquisitionFamily>,
        val reference30: List<ReferenceVector>,
    )

    fun evaluate(
        petrolCounters: IntArray?,
        petrolTimes: IntArray?,
        petrolMaps: IntArray?,
        petrolZoneFlags: IntArray?,
        gasCounters: IntArray?,
        gasTimes: IntArray?,
        gasMaps: IntArray?,
        gasZoneFlags: IntArray?,
        previousGasTimes: IntArray?,
        previousGasMaps: IntArray?,
        reference30: Map<String, IntArray?> = emptyMap(),
    ): Snapshot {
        val families = listOf(
            family(Fuel.PETROL, petrolCounters, petrolTimes, petrolMaps, petrolZoneFlags),
            family(Fuel.GAS, gasCounters, gasTimes, gasMaps, gasZoneFlags),
            family(Fuel.GAS_PREVIOUS, gasCounters, previousGasTimes, previousGasMaps, gasZoneFlags),
        )
        val references = reference30.map { (key, values) ->
            val exact = values?.takeIf { it.size == REFERENCE_POINTS }?.copyOf()
            ReferenceVector(
                key = key,
                shapeState = if (exact != null) ShapeState.KNOWN else ShapeState.UNKNOWN,
                rawValues = exact,
            )
        }
        return Snapshot(families, references)
    }

    private fun family(
        fuel: Fuel,
        counters: IntArray?,
        times: IntArray?,
        maps: IntArray?,
        zoneFlags: IntArray?,
    ): AcquisitionFamily {
        val exactCounters = counters?.takeIf { it.size == ACQUISITION_BANDS }
        val exactTimes = times?.takeIf { it.size == ACQUISITION_BANDS }
        val exactMaps = maps?.takeIf { it.size == ACQUISITION_BANDS }
        val exactZones = zoneFlags?.takeIf { it.size == ZONES }
        val shapeKnown = exactCounters != null && exactTimes != null && exactMaps != null

        val bands = List(ACQUISITION_BANDS) { index ->
            val time = exactTimes?.get(index)
            val map = exactMaps?.get(index)
            BandPoint(
                fuel = fuel,
                index = index,
                zone = zone(index),
                counter = exactCounters?.get(index),
                petrolTimeRaw = time,
                mapRaw = map,
                coordinateState = if (time != null && map != null) CoordinateState.POSITIONED else CoordinateState.UNPOSITIONED,
            )
        }
        val zones = List(ZONES) { index ->
            val raw = exactZones?.get(index)
            ZoneProgress(
                fuel = fuel,
                zone = index,
                rawFlag = raw,
                state = when (raw) {
                    null -> ZoneState.UNKNOWN
                    0 -> ZoneState.NOT_ACQUIRED
                    1 -> ZoneState.ACQUIRED
                    else -> ZoneState.RAW_OTHER
                },
            )
        }
        return AcquisitionFamily(
            fuel = fuel,
            shapeState = if (shapeKnown) ShapeState.KNOWN else ShapeState.UNKNOWN,
            bands = bands,
            zones = zones,
        )
    }

    private fun zone(index: Int): Int = when (index) {
        in 0..5 -> 0
        in 6..9 -> 1
        in 10..13 -> 2
        else -> 3
    }
}
