package com.omegas.dyno.lab

import org.junit.Assert.*
import org.junit.Test

class TelemetryHubTest {
    @Test fun pairsOnlyFreshRpmWithinVerifiedFuelEpoch() {
        val hub = TelemetryHub()
        hub.link(Source.MP48, TelemetryHub.ONLINE)
        hub.link(Source.OBD, TelemetryHub.ONLINE)

        fun sample(source: Source, ms: Long, fuel: Fuel = Fuel.UNKNOWN) {
            hub.accept(Observation(source, Stamp(ms * 1_000_000, ms),
                mapOf("rpm" to 1200, "fuel" to fuel.label), fuel))
        }
        fun snapshot(ms: Long) = hub.snapshot(ms * 1_000_000)
        fun comparison(ms: Long) = snapshot(ms).getJSONObject("fusion").get("comparison")

        sample(Source.MP48, 1000, Fuel.PETROL)
        sample(Source.OBD, 1050)
        assertEquals("gasolina", snapshot(1100).getJSONObject("fusion").getJSONObject("comparison").getString("fuel"))
        // A transport timestamp difference is not acceleration response latency.
        // No paired acceleration events were recorded, so lag remains unknown.
        assertEquals(UNKNOWN, snapshot(1100).getJSONObject("fusion").get("lagMs"))
        sample(Source.MP48, 1100, Fuel.TRANSITION)
        assertEquals(UNKNOWN, comparison(1100))
        sample(Source.MP48, 1150, Fuel.GNV)
        assertEquals(UNKNOWN, comparison(1150))
        sample(Source.OBD, 1200)
        assertEquals("GNV", snapshot(1200).getJSONObject("fusion").getJSONObject("comparison").getString("fuel"))
        assertNotEquals(UNKNOWN, comparison(2650))
        assertEquals(UNKNOWN, comparison(2651))
        assertEquals(UNKNOWN, snapshot(2651).getJSONObject("mp48").get("rpm"))
    }

    @Test fun obdFreshnessIsPerPidAndAnActualZeroIsPreserved() {
        val hub = TelemetryHub()
        hub.link(Source.OBD, TelemetryHub.ONLINE)
        hub.accept(Observation(Source.OBD, Stamp(0, 10), mapOf("rpm" to 0)))
        hub.accept(Observation(Source.OBD, Stamp(1_600_000_000, 20), mapOf("speed" to 0)))
        val snapshot = hub.snapshot(1_600_000_000)
        val obd = snapshot.getJSONObject("obd")
        assertEquals(UNKNOWN, obd.get("rpm"))
        assertEquals(0, obd.get("speed"))
        assertEquals(10, snapshot.getJSONArray("history").getJSONObject(0).getInt("t"))
    }
}
