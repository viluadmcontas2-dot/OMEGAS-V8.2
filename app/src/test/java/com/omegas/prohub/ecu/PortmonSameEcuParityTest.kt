package com.omegas.prohub.ecu

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class PortmonSameEcuParityTest {
    private data class Tx(
        val requestText: String,
        val request: ByteArray,
        val response: ByteArray,
    )

    private fun fixture(name: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, "tests/fixtures/$name")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Fixture not found: $name from ${System.getProperty("user.dir")}")
    }

    private fun transactions(): List<Tx> {
        val root = JSONObject(fixture("portmon-autocal-cycle-v1.json").readText())
        val array = root.getJSONArray("transactions")
        return (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            Tx(
                requestText = row.getString("request"),
                request = hex(row.getString("request")),
                response = hex(row.getString("response")),
            )
        }
    }

    private fun hex(value: String): ByteArray =
        value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            .map { it.toInt(16).toByte() }.toByteArray()

    private fun parts(tx: Tx): Triple<Int, Int, ByteArray> {
        assertArrayEquals(tx.request, tx.response.copyOfRange(0, tx.request.size))
        val status = tx.response[tx.request.size].toInt() and 0xFF
        val declared = tx.response[tx.request.size + 1].toInt() and 0xFF
        val payload = tx.response.copyOfRange(tx.request.size + 2, tx.response.size - 1)
        assertEquals(declared, payload.size)
        return Triple(status, declared, payload)
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF
    private fun u16(bytes: ByteArray, offset: Int): Int = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    @Test
    fun `all captured live frames feed the production MP48 decoder at the recovered offsets`() {
        val live = transactions().filter { it.requestText == "48 01 49" }
        assertEquals(138, live.size)
        live.forEachIndexed { index, tx ->
            val (status, _, payload) = parts(tx)
            assertEquals(Mp48Protocol.STATUS_ACK, status)
            assertEquals(Mp48Protocol.TELEMETRY_PAYLOAD_SIZE, payload.size)
            val decoded = Mp48Protocol.decodeTelemetry(payload, index.toLong() + 1L)
            assertEquals(u16(payload, 0), decoded.rpm)
            assertEquals(u16(payload, 6), decoded.gasRaw)
            assertEquals(u16(payload, 8), decoded.petrolRaw)
            assertEquals(u8(payload, 11), decoded.fuelByte)
            assertEquals(u8(payload, 13), decoded.levelRaw)
            assertEquals(u16(payload, 14), decoded.gasPressureRaw)
            assertEquals(u8(payload, 16), decoded.gasTemperatureRaw)
            assertEquals(u16(payload, 17), decoded.mapRaw)
            assertEquals(u16(payload, 24), decoded.gas2Raw)
            assertEquals(u16(payload, 28), decoded.petrol2Raw)
            assertEquals(decoded.levelRaw, decoded.toJson().getInt("level_raw"))
        }
    }

    @Test
    fun `all captured oracle mapped AutoCal vectors feed the production decoder`() {
        val requestToField = mapOf(
            "29 5B 01 85" to AutoCalProtocol.NUM_BUF_UPD_PETR,
            "29 5C 01 86" to AutoCalProtocol.NUM_BUF_UPD_GAS,
            "29 5D 01 87" to AutoCalProtocol.PETR_INJ_TBUF_GAS_PREV,
            "29 5E 01 88" to AutoCalProtocol.MNFLD_PRESS_BUF_GAS_PREV,
            "29 5F 01 89" to AutoCalProtocol.PETR_INJ_TBUF_GAS,
            "29 60 01 8A" to AutoCalProtocol.MNFLD_PRESS_BUF_GAS,
            "29 61 01 8B" to AutoCalProtocol.MUL_ACT,
            "29 62 01 8C" to AutoCalProtocol.PETR_INJ_TBUF,
            "29 63 01 8D" to AutoCalProtocol.MNFLD_PRESS_BUF,
            "29 6F 01 99" to AutoCalProtocol.ACQUIRED_ZONES_PETROL,
            "29 70 01 9A" to AutoCalProtocol.ACQUIRED_ZONES_GAS,
            "09 74 01 7E" to AutoCalProtocol.NUM_AUTOMATCH_EXECUTED,
            "29 8D 01 B7" to AutoCalProtocol.PETR_MNFLD_PRESS_RV,
            "29 8E 01 B8" to AutoCalProtocol.GAS_MNFLD_PRESS_RV,
        )
        var decodedCount = 0
        transactions().forEach { tx ->
            val field = requestToField[tx.requestText] ?: return@forEach
            val (status, _, payload) = parts(tx)
            val decoded = AutoCalProtocol.decode(field, status, payload)
            val moduleVersion = if (field in setOf(
                    AutoCalProtocol.MUL_ACT,
                    AutoCalProtocol.PETR_MNFLD_PRESS_RV,
                    AutoCalProtocol.GAS_MNFLD_PRESS_RV,
                )
            ) 4 else null
            AutoCalProtocol.requireExpectedShape(decoded, moduleVersion)
            assertEquals(payload.size, decoded.rawPayload.size)
            assertTrue(decoded.elementCount > 0)
            decodedCount += 1
        }
        assertEquals(55, decodedCount)
    }

    @Test
    fun `all compact native status frames feed the production decoder`() {
        val rows = transactions().filter { it.requestText == "48 0B 53" }
        assertEquals(4, rows.size)
        rows.forEach { tx ->
            val (status, _, payload) = parts(tx)
            val decoded = AutoCalProtocol.decodeNativeStatus(status, payload)
            assertEquals(payload[12].toInt() and 0xFF, decoded.nativeFlag13)
            assertEquals(payload[13].toInt() and 0xFF, decoded.autoMatchCount)
        }
    }

    @Test
    fun `production decoders fail closed on representative truncation mutations`() {
        val live = transactions().first { it.requestText == "48 01 49" }
        val livePayload = parts(live).third
        assertThrows(IllegalArgumentException::class.java) {
            Mp48Protocol.decodeTelemetry(livePayload.copyOf(livePayload.size - 1), 1L)
        }

        val vector = transactions().first { it.requestText == "29 5B 01 85" }
        val (_, statusPayloadSize, payload) = parts(vector)
        assertTrue(statusPayloadSize > 1)
        assertThrows(IllegalArgumentException::class.java) {
            AutoCalProtocol.decode(AutoCalProtocol.NUM_BUF_UPD_PETR, Mp48Protocol.STATUS_ACK, payload.copyOf(payload.size - 1))
        }
    }
}
