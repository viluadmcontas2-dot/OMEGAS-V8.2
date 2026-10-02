package com.omegas.prohub.ecu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `plano multiponto combina gasolina e GNV em um único commit`() {
        val targets = listOf(
            AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.PETROL, 2),
            AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.PETROL, 5),
            AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.GAS, 7),
        )
        val petrol = AutoCalPointDeleteProtocol.maskForTargets(targets, AutoCalPointDeleteProtocol.Fuel.PETROL)
        val gas = AutoCalPointDeleteProtocol.maskForTargets(targets, AutoCalPointDeleteProtocol.Fuel.GAS)
        val plan = AutoCalPointDeleteProtocol.multiPointPlan(targets)

        assertEquals(18, petrol.size)
        assertEquals(18, gas.size)
        assertEquals(0, petrol[2])
        assertEquals(0, petrol[5])
        assertEquals(0, gas[7])
        assertTrue(petrol.withIndex().all { (index, value) -> index == 2 || index == 5 || value == 1 })
        assertTrue(gas.withIndex().all { (index, value) -> index == 7 || value == 1 })
        assertEquals(3, plan.size)
        assertArrayEquals(AutoCalPointDeleteProtocol.commit(), plan.last())
    }

    @Test
    fun `selecao duplicada nao cria segunda exclusao`() {
        val target = AutoCalPointDeleteProtocol.Target(AutoCalPointDeleteProtocol.Fuel.GAS, 4)
        val gas = AutoCalPointDeleteProtocol.maskForTargets(listOf(target, target), AutoCalPointDeleteProtocol.Fuel.GAS)
        assertEquals(1, gas.count { it == AutoCalPointDeleteProtocol.DELETE })
        assertEquals(3, AutoCalPointDeleteProtocol.multiPointPlan(listOf(target, target)).size)
    }

    private fun hex(value: String): ByteArray =
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
