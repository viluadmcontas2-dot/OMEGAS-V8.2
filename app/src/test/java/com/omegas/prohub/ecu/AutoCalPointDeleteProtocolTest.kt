package com.omegas.prohub.ecu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCalPointDeleteProtocolTest {
    @Test
    fun `quadros pontuais reproduzem writer U8 e commit do ProgBase`() {
        assertArrayEquals(hex("13 6D 01 0E 00 8F"), AutoCalPointDeleteProtocol.writeMaskElement(
            AutoCalPointDeleteProtocol.Fuel.PETROL, 14, AutoCalPointDeleteProtocol.DELETE,
        ))
        assertArrayEquals(hex("13 6E 01 0E 00 90"), AutoCalPointDeleteProtocol.writeMaskElement(
            AutoCalPointDeleteProtocol.Fuel.GAS, 14, AutoCalPointDeleteProtocol.DELETE,
        ))
        assertArrayEquals(hex("01 24 05 2A"), AutoCalPointDeleteProtocol.commit())
    }

    @Test
    fun `plano pontual reescreve masks completos antes do commit`() {
        val target = AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.GAS, 14)
        val plan = AutoCalPointDeleteProtocol.singlePointPlan(target)
        assertEquals(37, plan.size)
        for (index in 0 until 18) {
            assertArrayEquals(
                AutoCalPointDeleteProtocol.writeMaskElement(
                    AutoCalPointDeleteProtocol.Fuel.GAS,
                    index,
                    if (index == 14) AutoCalPointDeleteProtocol.DELETE else AutoCalPointDeleteProtocol.KEEP,
                ),
                plan[index],
            )
        }
        for (index in 0 until 18) {
            assertArrayEquals(
                AutoCalPointDeleteProtocol.writeMaskElement(
                    AutoCalPointDeleteProtocol.Fuel.PETROL,
                    index,
                    AutoCalPointDeleteProtocol.KEEP,
                ),
                plan[18 + index],
            )
        }
        assertArrayEquals(AutoCalPointDeleteProtocol.commit(), plan.last())
        assertEquals(4, target.zone)
    }

    private fun hex(value: String): ByteArray = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
