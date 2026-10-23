package com.omegas.prohub.carinfo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ordem do Parcel do DashBoardInfo (contrato ICanBus, DashBoardInfo.java:231-249):
 * 7 int, 7 float, 3 int (inclui throttlePos), 18 campos de 4 bytes = 72 bytes.
 */
class DashBoardInfoDecoderTest {

    /** Leitor falso no formato do Parcel: inteiros e floats de 4 bytes, little-endian, sem passar do fim. */
    private class FakeReader(private val buf: ByteBuffer) : DashBoardFieldReader {
        override fun remainingBytes(): Int = buf.remaining()
        override fun readInt(): Int = buf.int
        override fun readFloat(): Float = buf.float
    }

    private fun parcel(vararg values: Number): FakeReader {
        val buf = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { if (it is Float) buf.putFloat(it) else buf.putInt(it.toInt()) }
        buf.flip()
        return FakeReader(buf)
    }

    private val unknownI = Int.MIN_VALUE
    private val unknownF = -2.14748365E9f

    private fun realisticFields(): Array<Number> = arrayOf(
        3, 12345, 420, 1850, 47, 3600, 47, // gear, odo, range, rpm, speed, runningTime, fuelTankage
        31.5f, 67.0f, 7.8f, 9.1f, 88.0f, 95.0f, 30.0f, 24.0f, // fuel, fuel%, avg, inst, coolant, oil, inlet, ambient
        12, 0, 15, // accPedal, brakePedal, throttlePos
    )

    @Test
    fun `campos reais saem na ordem do contrato`() {
        val sample = DashBoardInfoDecoder.decodeFields(parcel(*realisticFields()))
        assertTrue(sample.complete)
        assertEquals(3, sample.gearShiftPosition)
        assertEquals(12345, sample.odo)
        assertEquals(420, sample.cruisingRange)
        assertEquals(1850, sample.rotationRate)
        assertEquals(47, sample.speed)
        assertEquals(3600, sample.runningTime)
        assertEquals(47, sample.fuelTankage)
        assertEquals(31.5f, sample.fuel!!, 0f)
        assertEquals(67.0f, sample.fuelPercentage!!, 0f)
        assertEquals(7.8f, sample.avgFuelCont!!, 0f)
        assertEquals(9.1f, sample.instFuelCont!!, 0f)
        assertEquals(88.0f, sample.coolantTemp!!, 0f)
        assertEquals(95.0f, sample.engineOilTemp!!, 0f)
        assertEquals(30.0f, sample.inletTemp!!, 0f)
        assertEquals(24.0f, sample.ambientTemp!!, 0f)
        assertEquals(12, sample.accPedal)
        assertEquals(0, sample.brakePedal) // 0 real (pedal solto) continua 0, não vira desconhecido
        assertEquals(15, sample.throttlePos)
        assertEquals(18, sample.knownCount())
    }

    @Test
    fun `sentinelas viram nulo e nunca zero`() {
        val fields = arrayOf<Number>(
            unknownI, unknownI, unknownI, unknownI, unknownI, unknownI, unknownI,
            unknownF, unknownF, unknownF, unknownF, unknownF, unknownF, unknownF, unknownF,
            unknownI, unknownI, unknownI,
        )
        val sample = DashBoardInfoDecoder.decodeFields(parcel(*fields))
        assertTrue(sample.complete)
        assertEquals(0, sample.knownCount())
        assertNull(sample.speed)
        assertNull(sample.odo)
        assertNull(sample.rotationRate)
        assertNull(sample.fuel)
        assertNull(sample.throttlePos)
        val json = sample.toJson()
        assertEquals(JSONObject.NULL, json.get("speed"))
        assertEquals(JSONObject.NULL, json.get("odo"))
        assertEquals(JSONObject.NULL, json.get("fuel"))
    }

    @Test
    fun `float sentinela tambem e reconhecida pelo valor inteiro minimo`() {
        assertTrue(DashBoardInfoDecoder.isUnknown(Int.MIN_VALUE.toFloat()))
        assertTrue(DashBoardInfoDecoder.isUnknown(Float.NaN))
        assertFalse(DashBoardInfoDecoder.isUnknown(0f))
    }

    @Test
    fun `mistura de conhecidos e desconhecidos`() {
        val fields = realisticFields()
        fields[1] = unknownI // odo desconhecido
        fields[7] = unknownF // fuel desconhecido
        val sample = DashBoardInfoDecoder.decodeFields(parcel(*fields))
        assertNull(sample.odo)
        assertNull(sample.fuel)
        assertEquals(47, sample.speed)
        assertEquals(16, sample.knownCount())
    }

    @Test
    fun `sequencia truncada nao estoura e marca incompleto`() {
        val sample = DashBoardInfoDecoder.decodeFields(parcel(3, 12345, 420, 1850, 47))
        assertFalse(sample.complete)
        assertEquals(1850, sample.rotationRate)
        assertEquals(47, sample.speed)
        assertNull(sample.runningTime)
        assertNull(sample.fuel)
        assertNull(sample.throttlePos)
        assertFalse(sample.toJson().getBoolean("complete"))
    }

    @Test
    fun `parcel vazio nao estoura`() {
        val sample = DashBoardInfoDecoder.decodeFields(parcel())
        assertFalse(sample.complete)
        assertEquals(0, sample.knownCount())
    }

    @Test
    fun `objeto anulavel respeita o marcador de presenca`() {
        assertNull(DashBoardInfoDecoder.decodeNullable(parcel(0)))
        assertNull(DashBoardInfoDecoder.decodeNullable(parcel()))
        val sample = DashBoardInfoDecoder.decodeNullable(parcel(1, *realisticFields()))!!
        assertEquals(47, sample.speed)
        assertTrue(sample.complete)
    }

    @Test
    fun `valor fisicamente impossivel vira nulo e valor valido continua`() {
        val fields = realisticFields()
        fields[3] = -1; fields[4] = -1; fields[1] = -1; fields[2] = -1 // rpm, speed, odo, autonomia
        fields[8] = 150.0f // combustivel acima de 100 %
        fields[9] = -1.0f; fields[10] = -1.0f // consumo medio e instantaneo (campo, 2026-10-21: "Consumo agora -1,0")
        fields[11] = 400.0f // temperatura da agua impossivel
        fields[15] = -1 // pedal do acelerador
        val sample = DashBoardInfoDecoder.decodeFields(parcel(*fields))
        assertNull(sample.rotationRate)
        assertNull(sample.speed)
        assertNull(sample.odo)
        assertNull(sample.cruisingRange)
        assertNull(sample.fuelPercentage)
        assertNull(sample.avgFuelCont)
        assertNull(sample.instFuelCont)
        assertNull(sample.coolantTemp)
        assertNull(sample.accPedal)
        assertEquals(JSONObject.NULL, sample.toJson().get("instFuelCont"))
        assertEquals(12345.0, DashBoardInfoDecoder.decodeFields(parcel(*realisticFields())).odo!!.toDouble(), 0.0)
    }

    @Test
    fun `zero real e valores de campo continuam validos`() {
        val fields = realisticFields()
        fields[4] = 0; fields[3] = 0; fields[9] = 11.7f; fields[10] = 0.0f; fields[14] = -4.0f // parado, consumo 0, ambiente negativo
        val sample = DashBoardInfoDecoder.decodeFields(parcel(*fields))
        assertEquals(0, sample.speed)
        assertEquals(0, sample.rotationRate)
        assertEquals(11.7f, sample.avgFuelCont!!, 0f)
        assertEquals(0.0f, sample.instFuelCont!!, 0f)
        assertEquals(-4.0f, sample.ambientTemp!!, 0f)
    }
}
