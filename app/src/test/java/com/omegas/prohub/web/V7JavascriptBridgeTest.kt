package com.omegas.prohub.web

import com.omegas.prohub.calibration.FakeKEcuScheduler
import com.omegas.prohub.calibration.KFactorManager
import com.omegas.prohub.calibration.KWriteManager
import com.omegas.prohub.calibration.OwnedBusyLock
import com.omegas.prohub.util.RingLog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class V7JavascriptBridgeTest {
    private val dir = Files.createTempDirectory("v7-bridge").toFile()
    private val ecu = FakeKEcuScheduler()
    private val lock = OwnedBusyLock()
    private val curve = KFactorManager(dir, ecu, RingLog(), {}, busy = lock)
    private val map = KWriteManager(dir, ecu, RingLog(), { false }, { true }, { true }, {}, busy = lock)
    private val refreshes = LinkedBlockingQueue<JSONObject>()
    private val reconciled = mutableListOf<String>()
    private val bridge: V7JavascriptBridge = V7JavascriptBridge(object : V7JavascriptBridge.WriteHost {
        override fun unsafeReason(): String? = null
        override fun startCurveReset(onVerified: (JSONObject) -> Unit) =
            curve.startResetToNeutral(onVerified = onVerified).toString()
        override fun startCurveBatchWrite(points: String, reason: String, onVerified: (JSONObject) -> Unit) =
            curve.startBatchWrite(JSONArray(points), reason, onVerified).toString()
        override fun startMapBatchWrite(cells: String, reason: String, onVerified: (JSONObject) -> Unit) =
            map.startBatchWrite(JSONArray(cells), reason, onVerified).toString()
        override fun curveStatus() = curve.statusJson()
        override fun mapStatus() = map.statusJson()
        override fun reconcile(target: String): String {
            assertFalse("Reconciliation must wait for the calibration lock", lock.isBusy())
            reconciled.add(target)
            return JSONObject().put("ok", true).toString()
        }
        override fun refresh() {
            refreshes.add(operation())
        }
    })

    @Before fun readCalibration() {
        ecu.factors[3] = 18_000
        assertTrue(curve.readCurve().getBoolean("ok"))
        assertTrue(map.readFullMap().getBoolean("ok"))
    }

    @After fun tearDown() {
        bridge.destroy()
        curve.close()
        map.close()
        dir.deleteRecursively()
    }

    @Test fun curveBatchAcknowledgesBeforeReadbackAndReconcilesAfterward() {
        checkWrite("conferência em segundo plano Curva K", "CURVE_K") {
            bridge.startCurveBatchWrite("""[{"index":3,"currentRaw":18000,"targetRaw":16384}]""", "Ajuste manual")
        }
    }

    @Test fun curveResetPublishesAckAndRefreshesWithoutWaitingForReadback() {
        val verified = checkWrite("conferência em segundo plano Curva K", null, bridge::startCurveReset)
        assertTrue(verified.getBoolean("readbackValid"))
        assertEquals(16_384, ecu.factors[3])
    }

    @Test fun mapBatchAcknowledgesAllCellsAndWaitsForReadbackBeforeReconciliation() {
        val verified = checkWrite("conferência em segundo plano Mapa K", "MAP_K") {
            bridge.startMapBatchWrite("""[{"row":0,"column":0,"current":120,"target":130}]""", "Ajuste manual")
        }
        assertEquals(1, verified.getInt("confirmedCells"))
        assertEquals(130, ecu.map[0][0])
    }

    private fun checkWrite(reason: String, target: String?, start: () -> String): JSONObject {
        ecu.hold(reason)
        try {
            assertTrue(JSONObject(start()).getBoolean("started"))
            ecu.awaitEntered(reason)
            val ack = nextRefresh()
            assertEquals("BATCH_CONFIRMED", ack.getString("state"))
            assertTrue(ack.getBoolean("ok"))
            assertTrue(ack.getBoolean("acked"))
            assertFalse(ack.getBoolean("busy"))
            assertFalse(ack.getBoolean("readbackValid"))
            assertTrue(ack.getBoolean("verificationPending"))
            assertTrue(reconciled.isEmpty())
            assertFalse(map.readFullMap().getBoolean("ok"))
            assertFalse(curve.readCurve().getBoolean("ok"))
        } finally {
            ecu.release(reason)
        }
        val verified = nextRefresh()
        assertTrue(verified.getBoolean("readbackValid"))
        assertFalse(verified.getBoolean("verificationPending"))
        assertEquals(listOfNotNull(target), reconciled)
        return verified
    }

    private fun operation() = JSONObject(bridge.getLastOperation())
    private fun nextRefresh() = checkNotNull(refreshes.poll(5, TimeUnit.SECONDS)) { "Bridge did not refresh" }
}
