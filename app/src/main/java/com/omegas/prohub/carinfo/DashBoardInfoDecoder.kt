package com.omegas.prohub.carinfo

import org.json.JSONObject

/**
 * Leitura de campos de 4 bytes na ordem do Parcel. Abstração para o decodificador não depender de
 * `android.os.Parcel` (teste JVM puro). [remainingBytes] existe porque o Parcel real devolve 0 ao ler
 * além do fim, e 0 aqui seria um valor falso ("desconhecido é —, nunca 0").
 */
interface DashBoardFieldReader {
    fun remainingBytes(): Int
    fun readInt(): Int
    fun readFloat(): Float
}

/**
 * Painel do carro como a multimídia (Car Info OEM) publica. Nomes iguais aos do contrato
 * (`com.autoai.canbus.DashBoardInfo`). Unidades INCERTAS até captura com o carro andando:
 * speed provável km/h, odo/cruisingRange provável km, rotationRate provável rpm.
 * `null` = a multimídia não informou (sentinela) ou o Parcel acabou antes do campo.
 */
data class CarInfoSample(
    val gearShiftPosition: Int? = null,
    val odo: Int? = null,
    val cruisingRange: Int? = null,
    val rotationRate: Int? = null,
    val speed: Int? = null,
    val runningTime: Int? = null,
    val fuelTankage: Int? = null,
    val fuel: Float? = null,
    val fuelPercentage: Float? = null,
    val avgFuelCont: Float? = null,
    val instFuelCont: Float? = null,
    val coolantTemp: Float? = null,
    val engineOilTemp: Float? = null,
    val inletTemp: Float? = null,
    val ambientTemp: Float? = null,
    val accPedal: Int? = null,
    val brakePedal: Int? = null,
    val throttlePos: Int? = null,
    /** false quando o Parcel terminou antes dos 18 campos. */
    val complete: Boolean = false,
) {
    private fun values(): List<Pair<String, Number?>> = listOf(
        "gearShiftPosition" to gearShiftPosition,
        "odo" to odo,
        "cruisingRange" to cruisingRange,
        "rotationRate" to rotationRate,
        "speed" to speed,
        "runningTime" to runningTime,
        "fuelTankage" to fuelTankage,
        "fuel" to fuel,
        "fuelPercentage" to fuelPercentage,
        "avgFuelCont" to avgFuelCont,
        "instFuelCont" to instFuelCont,
        "coolantTemp" to coolantTemp,
        "engineOilTemp" to engineOilTemp,
        "inletTemp" to inletTemp,
        "ambientTemp" to ambientTemp,
        "accPedal" to accPedal,
        "brakePedal" to brakePedal,
        "throttlePos" to throttlePos,
    )

    fun knownCount(): Int = values().count { it.second != null }

    /** Todos os campos, desconhecido = JSON null. */
    fun toJson(): JSONObject {
        val json = JSONObject()
        values().forEach { (name, value) -> json.put(name, value?.let { jsonNumber(it) } ?: JSONObject.NULL) }
        return json.put("complete", complete)
    }

    /** Só os campos conhecidos (evento compacto da sessão). */
    fun knownJson(): JSONObject {
        val json = JSONObject()
        values().forEach { (name, value) -> if (value != null) json.put(name, jsonNumber(value)) }
        return json.put("complete", complete)
    }

    private fun jsonNumber(value: Number): Any = if (value is Float) value.toDouble() else value
}

/** Decodifica o Parcel de `DashBoardInfo` (7 int, 8 float, 3 int; contrato DashBoardInfo.java:231-249). */
object DashBoardInfoDecoder {
    const val FIELD_COUNT = 18
    const val BYTES = FIELD_COUNT * 4
    const val UNKNOWN_INT = Int.MIN_VALUE
    const val UNKNOWN_FLOAT = -2.14748365E9f

    fun isUnknown(value: Int): Boolean = value == UNKNOWN_INT
    fun isUnknown(value: Float): Boolean = value == UNKNOWN_FLOAT || value.isNaN() || value.isInfinite()

    /** Formato `P(T)`: marcador int (0 = nulo) seguido do objeto. */
    fun decodeNullable(reader: DashBoardFieldReader): CarInfoSample? {
        if (reader.remainingBytes() < 4) return null
        if (reader.readInt() == 0) return null
        return decodeFields(reader)
    }

    fun decodeFields(reader: DashBoardFieldReader): CarInfoSample {
        var truncated = false
        fun int(): Int? {
            if (truncated || reader.remainingBytes() < 4) { truncated = true; return null }
            return reader.readInt().takeUnless(::isUnknown)
        }
        fun float(): Float? {
            if (truncated || reader.remainingBytes() < 4) { truncated = true; return null }
            return reader.readFloat().takeUnless(::isUnknown)
        }
        // A ordem das chamadas abaixo É o contrato: não reordenar.
        val gear = int(); val odo = int(); val range = int(); val rpm = int(); val speed = int()
        val running = int(); val tankage = int()
        val fuel = float(); val fuelPct = float(); val avg = float(); val inst = float()
        val coolant = float(); val oil = float(); val inlet = float(); val ambient = float()
        val acc = int(); val brake = int(); val throttle = int()
        return CarInfoSample(
            gearShiftPosition = gear, odo = odo, cruisingRange = range, rotationRate = rpm, speed = speed,
            runningTime = running, fuelTankage = tankage,
            fuel = fuel, fuelPercentage = fuelPct, avgFuelCont = avg, instFuelCont = inst,
            coolantTemp = coolant, engineOilTemp = oil, inletTemp = inlet, ambientTemp = ambient,
            accPedal = acc, brakePedal = brake, throttlePos = throttle,
            complete = !truncated,
        )
    }
}
