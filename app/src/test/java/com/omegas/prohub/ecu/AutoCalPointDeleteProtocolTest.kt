package com.omegas.prohub.ecu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCalPointDeleteProtocolTest {
    @Test
    fun `masks completos usam SetVector do ProgBase e commit final`() {
        val target = AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.GAS, 14)
        val gas = AutoCalPointDeleteProtocol.writeMaskVector(
            AutoCalPointDeleteProtocol.Fuel.GAS,
            AutoCalPointDeleteProtocol.maskFor(target, AutoCalPointDeleteProtocol.Fuel.GAS),
        )
        val petrol = AutoCalPointDeleteProtocol.writeMaskVector(
            AutoCalPointDeleteProtocol.Fuel.PETROL,
            AutoCalPointDeleteProtocol.maskFor(target, AutoCalPointDeleteProtocol.Fuel.PETROL),
        )

        assertArrayEquals(hex("37 6E 14 01"), gas.copyOfRange(0, 4))
        assertArrayEquals(hex("37 6D 14 01"), petrol.copyOfRange(0, 4))
        assertEquals(23, gas.size)
        assertEquals(23, petrol.size)
        assertEquals(0, gas[4 + 14].toInt() and 0xFF)
        assertEquals(1, petrol[4 + 14].toInt() and 0xFF)
        assertArrayEquals(hex("01 24 05 2A"), AutoCalPointDeleteProtocol.commit())
    }

    @Test
    fun `plano pontual envia dois vetores completos antes do commit`() {
        val target = AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.GAS, 14)
        val plan = AutoCalPointDeleteProtocol.singlePointPlan(target)
        assertEquals(3, plan.size)
        assertArrayEquals(
            AutoCalPointDeleteProtocol.writeMaskVector(
                AutoCalPointDeleteProtocol.Fuel.GAS,
                AutoCalPointDeleteProtocol.maskFor(target, AutoCalPointDeleteProtocol.Fuel.GAS),
            ),
            plan[0],
        )
        assertArrayEquals(
            AutoCalPointDeleteProtocol.writeMaskVector(
                AutoCalPointDeleteProtocol.Fuel.PETROL,
                AutoCalPointDeleteProtocol.maskFor(target, AutoCalPointDeleteProtocol.Fuel.PETROL),
            ),
            plan[1],
        )
        assertArrayEquals(AutoCalPointDeleteProtocol.commit(), plan.last())
        assertEquals(4, target.zone)
    }

    private fun hex(value: String): ByteArray =
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
