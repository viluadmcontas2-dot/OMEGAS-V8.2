package com.omegas.prohub.autocal

/** Detecta somente reset/reacquire da aquisição GNV dentro da mesma sessão. */
class NativeAutoCalGasReacquireTracker {
    enum class EventType {
        GAS_RESET,
        GAS_REACQUIRE_STARTED,
    }

    data class Event(
        val type: EventType,
        val invalidationScope: String = "GNV_ACQUISITION_ONLY",
        val reconnect: Boolean = false,
    )

    private enum class GasState {
        UNKNOWN,
        EMPTY,
        ACTIVE,
    }

    private var previous = GasState.UNKNOWN
    private var resetObserved = false

    fun resetSession() {
        previous = GasState.UNKNOWN
        resetObserved = false
    }

    fun observe(snapshot: NativeAutoCalProgression.Snapshot): List<Event> {
        val current = snapshot.gasState()
        if (current == GasState.UNKNOWN) return emptyList()

        val events = buildList {
            if (previous == GasState.ACTIVE && current == GasState.EMPTY) {
                resetObserved = true
                add(Event(EventType.GAS_RESET))
            } else if (resetObserved && previous == GasState.EMPTY && current == GasState.ACTIVE) {
                resetObserved = false
                add(Event(EventType.GAS_REACQUIRE_STARTED))
            }
        }
        previous = current
        return events
    }

    private fun NativeAutoCalProgression.Snapshot.gasState(): GasState {
        val gas = acquisition18.singleOrNull { it.fuel == NativeAutoCalProgression.Fuel.GAS }
            ?: return GasState.UNKNOWN
        if (gas.shapeState != NativeAutoCalProgression.ShapeState.KNOWN) return GasState.UNKNOWN
        if (gas.bands.any { it.counter == null }) return GasState.UNKNOWN
        if (gas.zones.any { it.state == NativeAutoCalProgression.ZoneState.UNKNOWN || it.state == NativeAutoCalProgression.ZoneState.RAW_OTHER }) {
            return GasState.UNKNOWN
        }

        val counters = gas.bands.mapNotNull { it.counter }
        val allZero = counters.all { it == 0 } && gas.zones.all {
            it.state == NativeAutoCalProgression.ZoneState.NOT_ACQUIRED
        }
        if (allZero) return GasState.EMPTY

        val material = counters.any { it > 0 } || gas.zones.any {
            it.state == NativeAutoCalProgression.ZoneState.ACQUIRED
        }
        return if (material) GasState.ACTIVE else GasState.UNKNOWN
    }
}
