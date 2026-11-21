package com.omegas.dyno.lab

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test fun obdAcceptsHeadersEchoAndPromptButRejectsConflictingResponders() {
        assertEquals(1726.0, ObdProtocol.decode(ObdPid.RPM, "010C\rSEARCHING...\r7E8 04 41 0C 1A F8\r>")!!, 0.0)
        assertEquals(1726.0, ObdProtocol.decode(ObdPid.RPM, "48 6B 10 41 0C 1A F8 99\r>")!!, 0.0)
        assertEquals(1726.0, ObdProtocol.decode(ObdPid.RPM, "7E804410C1AF8>")!!, 0.0)
        assertNull(ObdProtocol.decode(ObdPid.RPM, "NO DATA\r>"))
        assertNull(ObdProtocol.decode(ObdPid.RPM, "41 0C 1A F8\r41 0C 00 00\r>"))
        assertNull(ObdProtocol.decode(ObdPid.RPM, "7E8 02 41 0C 1A F8>"))
    }

    @Test fun obdDecodesEveryRequestedSensorAndExtendedBitmap() {
        assertEquals(90.0, ObdProtocol.decode(ObdPid.ECT, "41 05 82>")!!, 0.0)
        assertEquals(100.0, ObdProtocol.decode(ObdPid.TPS, "41 11 FF>")!!, 0.0)
        assertEquals(65.0, ObdProtocol.decode(ObdPid.SPEED, "41 0D 41>")!!, 0.0)
        assertEquals(45.0, ObdProtocol.decode(ObdPid.MAP, "41 0B 2D>")!!, 0.0)
        assertEquals(-25.0, ObdProtocol.decode(ObdPid.STFT, "41 06 60>")!!, 0.0)
        assertEquals(25.0, ObdProtocol.decode(ObdPid.LTFT, "41 07 A0>")!!, 0.0)
        assertEquals(0x8000_0000L, ObdProtocol.bitmap(0x20, "18 DA F1 10 06 41 20 80 00 00 00>"))
    }

    @Test fun bitmapAndServiceOneAreExplicit() {
        val bitmap = ObdProtocol.bitmap(0, "41 00 00 10 00 01>")!!
        assertTrue(ObdProtocol.supports(bitmap, 0x0C))
        assertTrue(ObdProtocol.supports(bitmap, 0x20))
        assertFalse(ObdProtocol.supports(bitmap, 0x0D))
        assertEquals(listOf("ATE0", "ATL0", "ATS1", "ATH0", "ATSP0"), ObdProtocol.adapterSetup)
        assertEquals(setOf("0100", "0120", "010C", "010D", "010B", "0111", "0106", "0107", "0105"),
            ObdPid.entries.map { it.command }.toSet() + ObdProtocol.discoveryCommands.values)
    }

    @Test fun mp48ValidatesEchoLengthAndChecksumAndUsesPhysicalScales() {
        val command = Mp48Command.TELEMETRY.bytes()
        val payload = ByteArray(34)
        payload[0] = 0xD0.toByte()
        payload[1] = 7
        payload[6] = 100
        payload[8] = 125
        payload[11] = 0xB0.toByte()
        payload[14] = 0x40
        payload[15] = 6
        payload[17] = 0xF4.toByte()
        payload[18] = 1
        val body = byteArrayOf(0x53, 34) + payload
        val checksum = body.sumOf { it.toInt() and 255 }.toByte()
        val reply = command + body + checksum
        val sample = Mp48Protocol.decode(Mp48Protocol.payload(Mp48Command.TELEMETRY, reply)!!)!!
        assertEquals(2000, sample.rpm)
        assertEquals(2.0, sample.gasPressureBar, 0.0)
        assertEquals(0.5, sample.mapBar, 0.0)
        assertEquals(0.32, sample.petrolMs, 0.00001)
        assertEquals(0.256, sample.gasMs!!, 0.00001)
        assertEquals(Fuel.GNV, sample.fuel)
        assertNull(Mp48Protocol.payload(Mp48Command.TELEMETRY, reply.copyOf(reply.size - 1)))
        reply[0] = 0
        assertNull(Mp48Protocol.payload(Mp48Command.TELEMETRY, reply))
        reply[0] = command[0]
        reply[reply.lastIndex] = (checksum + 1).toByte()
        assertNull(Mp48Protocol.payload(Mp48Command.TELEMETRY, reply))
    }

    @Test fun mp48SupportsExistingReadSessionAndDistinguishesFuelVariants() {
        val init = Mp48Command.INIT_1
        val existing = init.bytes() + byteArrayOf(0xCA.toByte(), 0x01, 0x08, 0xD3.toByte())
        assertArrayEquals(byteArrayOf(8), Mp48Protocol.payload(init, existing, 0xCA))
        assertNull(Mp48Protocol.payload(init, existing))

        val payload = ByteArray(34)
        payload[0] = 0xE8.toByte()
        payload[1] = 3
        for ((flags, fuel) in mapOf(
            0x80 to Fuel.PETROL, 0xA0 to Fuel.PETROL, 0x90 to Fuel.GNV, 0xB0 to Fuel.GNV,
            0x88 to Fuel.TRANSITION, 0xA8 to Fuel.TRANSITION, 0xFF to Fuel.UNKNOWN,
        )) {
            payload[11] = flags.toByte()
            assertEquals(fuel, Mp48Protocol.decode(payload)?.fuel)
        }
    }

    @Test fun mp48CommandsAreAnExactReadOnlyAllowlist() {
        assertEquals(listOf(listOf(0, 2, 2), listOf(1, 0, 58, 59), listOf(72, 1, 73)),
            Mp48Command.entries.map { command -> command.bytes().map { it.toInt() and 255 } })
    }
}
