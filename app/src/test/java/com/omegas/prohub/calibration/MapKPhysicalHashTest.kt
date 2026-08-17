package com.omegas.prohub.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MapKPhysicalHashTest {
    private fun map13x12(): List<List<Int>> = List(13) { row ->
        List(12) { column -> (row * 12 + column) and 0xFF }
    }

    @Test
    fun `hash fisico inclui schema e todas as 13 por 12 raws`() {
        val rows = map13x12()
        val hash1 = MapKPhysicalHash.hash(rows)
        val hash2 = MapKPhysicalHash.hash(rows.map { it.toList() })

        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `alterar somente a row especial 0C muda o hash`() {
        val rows = map13x12()
        val changed = rows.mapIndexed { row, values ->
            if (row == 12) values.mapIndexed { column, value -> if (column == 11) value xor 0x01 else value }
            else values.toList()
        }

        assertNotEquals(MapKPhysicalHash.hash(rows), MapKPhysicalHash.hash(changed))
    }

    @Test
    fun `shape diferente de 13 por 12 falha fechado`() {
        assertThrows(IllegalArgumentException::class.java) { MapKPhysicalHash.hash(List(12) { List(12) { 0 } }) }
        assertThrows(IllegalArgumentException::class.java) { MapKPhysicalHash.hash(List(13) { List(11) { 0 } }) }
    }
}
