package com.omegas.prohub.autocal

/**
 * Preserva a última referência gasolina AutoCal fisicamente completa.
 *
 * Mudanças da aquisição GNV nunca apagam esta memória. Uma referência gasolina
 * só é substituída por outra fotografia gasolina novamente completa.
 */
class NativeAutoCalPetrolReferenceMemory {
    data class Reference(
        val bands: List<NativeAutoCalProgression.BandPoint>,
        val zones: List<NativeAutoCalProgression.ZoneProgress>,
        val revision: Long,
    ) {
        init {
            require(bands.size == NativeAutoCalProgression.ACQUISITION_BANDS)
            require(zones.size == NativeAutoCalProgression.ZONES)
            require(bands.all { it.fuel == NativeAutoCalProgression.Fuel.PETROL })
            require(zones.all { it.fuel == NativeAutoCalProgression.Fuel.PETROL })
        }
    }

    private var retained: Reference? = null
    private var nextRevision = 1L

    fun resetAll() {
        retained = null
        nextRevision = 1L
    }

    fun current(): Reference? = retained?.deepCopy()

    /**
     * Observa um snapshot sem assumir que ausência/incompletude significa reset
     * da gasolina. GNV e GAS_PREVIOUS são deliberadamente ignorados aqui.
     */
    fun observe(snapshot: NativeAutoCalProgression.Snapshot): Reference? {
        val petrol = snapshot.acquisition18.singleOrNull {
            it.fuel == NativeAutoCalProgression.Fuel.PETROL
        } ?: return current()

        if (!petrol.isCompletePetrolReference()) return current()

        val candidate = Reference(
            bands = petrol.bands.map { it.copy() },
            zones = petrol.zones.map { it.copy() },
            revision = nextRevision,
        )
        val previous = retained
        if (previous == null || !previous.samePhysicalContent(candidate)) {
            retained = candidate
            nextRevision += 1L
        }
        return current()
    }

    private fun NativeAutoCalProgression.AcquisitionFamily.isCompletePetrolReference(): Boolean =
        fuel == NativeAutoCalProgression.Fuel.PETROL &&
            shapeState == NativeAutoCalProgression.ShapeState.KNOWN &&
            bands.all {
                it.counter != null &&
                    it.coordinateState == NativeAutoCalProgression.CoordinateState.POSITIONED &&
                    it.petrolTimeRaw != null &&
                    it.mapRaw != null
            } &&
            zones.all { it.state == NativeAutoCalProgression.ZoneState.ACQUIRED }

    private fun Reference.samePhysicalContent(other: Reference): Boolean =
        bands == other.bands && zones == other.zones

    private fun Reference.deepCopy(): Reference = copy(
        bands = bands.map { it.copy() },
        zones = zones.map { it.copy() },
    )
}
