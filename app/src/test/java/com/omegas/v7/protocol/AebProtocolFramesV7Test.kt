package com.omegas.v7.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AebProtocolFramesV7Test {
    @Test
    fun map_row_read_matches_progbase_frame() {
        assertArrayEquals(
            hex("2A 54 00 00 7E"),
            AebProtocolFramesV7.readMapRow(0),
        )
    }

    @Test
    fun map_cell_write_matches_observed_set_number_frame() {
        assertArrayEquals(
            hex("14 54 00 00 0A AB 1D"),
            AebProtocolFramesV7.writeMapCell(row = 0, column = 10, value = 0xAB),
        )
    }

    @Test
    fun technical_map_row_can_still_be_preserved_as_full_protocol_row() {
        val values = listOf(110, 110, 110, 110, 110, 110, 112, 113, 114, 115, 116, 116)

        val frame = AebProtocolFramesV7.writeMapRow(12, values)

        assertArrayEquals(
            hex("37 54 0F 00 0C 6E 6E 6E 6E 6E 6E 70 71 72 73 74 74 E8"),
            frame,
        )
        assertTrue(AebProtocolFramesV7.checksumIsValid(frame))
    }

    @Test(expected = IllegalArgumentException::class)
    fun technical_map_row_rejects_ordinary_cell_write() {
        AebProtocolFramesV7.writeMapCell(row = 12, column = 0, value = 110)
    }

    @Test(expected = IllegalArgumentException::class)
    fun map_cell_writer_rejects_row_after_physical_storage() {
        AebProtocolFramesV7.writeMapCell(row = 13, column = 0, value = 110)
    }

    @Test
    fun mul_act_read_matches_progbase_frame() {
        assertArrayEquals(
            hex("29 61 01 8B"),
            AebProtocolFramesV7.readMulAct(),
        )
    }

    @Test
    fun mul_act_point_write_uses_index_and_u16_little_endian() {
        assertArrayEquals(
            hex("14 61 01 00 E8 03 61"),
            AebProtocolFramesV7.writeMulActPoint(index = 0, rawU16 = 1000),
        )
    }

    @Test
    fun full_mul_act_write_has_60_data_bytes_and_extended_length_3e() {
        val frame = AebProtocolFramesV7.writeMulAct(List(30) { 1000 })

        assertEquals(65, frame.size)
        assertArrayEquals(hex("37 61 3E 01"), frame.copyOfRange(0, 4))
        assertEquals(0x61, frame.last().toUByte().toInt())
        assertTrue(AebProtocolFramesV7.checksumIsValid(frame))
    }

    private fun hex(value: String): ByteArray = value
        .trim()
        .split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
