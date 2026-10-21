package com.omegas.prohub.carinfo

/**
 * Decodificadores TOLERANTES (nunca estouram; parcial = `complete=false`) das outras transações do ouvinte ICanBus,
 * na ordem de ESCRITA do contrato (`Taytech-/docs/produto/CONTRATO-ICANBUS.md` §3 e §5.1): o que cruza o binder é o que o
 * `writeToParcel` da multimídia escreve; o `createFromParcel` do cliente OEM tem ordem diferente (assimetria documentada).
 * SOMENTE LEITURA: nada aqui transmite para a CAN/ECU.
 */
data class CarProp(val propId: Int, val areaId: Int, val type: String?, val value: String, val number: Number?) {
    fun label(): String = "0x${Integer.toHexString(propId)}/${Integer.toHexString(areaId)}=$value"
}

data class CarPropBatch(val service: String?, val props: List<CarProp>, val declared: Int, val complete: Boolean)

data class CarPropConfigBatch(val service: String?, val propIds: List<Int>, val declared: Int, val complete: Boolean)

data class CabinFields(val values: Map<String, Int>, val complete: Boolean)

object CarInfoExtraDecoders {
    const val MAX_ITEMS = 64
    const val UNKNOWN_INT = Int.MIN_VALUE

    /** Ordem do `CabinInfo.writeToParcel` (22 int; contrato CabinInfo.java:265-287). Os cintos (9, 10) entram na escrita. */
    val CABIN_FIELDS = listOf(
        "bonnetStatus", "flDoorStatus", "frDoorStatus", "rlDoorStatus", "rrDoorStatus", "trunkStatus", "electricTrunk",
        "electricTrunkDirection", "leftSafetyBelt", "rightSafetyBelt", "sunroofStatus", "sunroofSunshadeStatus",
        "flWindowStatus", "frWindowStatus", "rlWindowStatus", "rrWindowStatus", "lockCarStatus", "seatUpDownMoveStatus",
        "seatFrontRearMoveStatus", "seatHighLowMoveStatus", "seatBackUpDownMoveStatus", "seatBackFrontRearMoveStatus",
    )

    /** tx 2: `P(CabinInfo)` = marcador int + 22 int. Desconhecido (MIN_VALUE) fica de fora. */
    fun decodeCabin(r: ParcelBytes): CabinFields {
        val out = LinkedHashMap<String, Int>()
        try {
            if (r.readInt() == 0) return CabinFields(out, true)
            for (name in CABIN_FIELDS) {
                val v = r.readInt()
                if (v != UNKNOWN_INT) out[name] = v
            }
            return CabinFields(out, true)
        } catch (_: RuntimeException) {
            return CabinFields(out, false)
        }
    }

    /** tx 17: String service + `L(CarPropertyValue)` (int n; por item marcador int + propId, areaId, classe, valor). */
    fun decodeCarPropValues(r: ParcelBytes): CarPropBatch {
        val props = ArrayList<CarProp>()
        var service: String? = null
        var declared = 0
        try {
            service = r.readString()
            declared = r.readInt()
            val n = declared.coerceIn(0, MAX_ITEMS)
            for (i in 0 until n) {
                if (r.readInt() == 0) continue
                val propId = r.readInt()
                val areaId = r.readInt()
                val type = r.readString()
                val (text, number) = when (type) {
                    "java.lang.String" -> (r.readString() ?: "null") to null
                    "[B" -> { r.readInt(); "bytes:" + ParcelBytes.hex(r.readByteArray() ?: ByteArray(0), 32) to null }
                    null -> "null" to null
                    else -> readValue(r)
                }
                props.add(CarProp(propId, areaId, type, text, number))
            }
            return CarPropBatch(service, props, declared, declared in 0..MAX_ITEMS)
        } catch (_: RuntimeException) {
            return CarPropBatch(service, props, declared, false)
        }
    }

    /** tx 14: String service + `L(CarPropertyConfig)`. Só os propIds (o resto é descartado com cuidado). */
    fun decodePropertyList(r: ParcelBytes): CarPropConfigBatch {
        val ids = ArrayList<Int>()
        var service: String? = null
        var declared = 0
        try {
            service = r.readString()
            declared = r.readInt()
            val n = declared.coerceIn(0, MAX_ITEMS)
            for (i in 0 until n) {
                if (r.readInt() == 0) continue
                ids.add(r.readInt())
                r.readString()          // classe do tipo
                r.readInt()             // areaType
                val areas = r.readInt().coerceIn(0, MAX_ITEMS)
                for (a in 0 until areas) {
                    r.readInt()         // chave da área
                    skipAreaConfig(r)
                }
            }
            return CarPropConfigBatch(service, ids, declared, declared in 0..MAX_ITEMS)
        } catch (_: RuntimeException) {
            return CarPropConfigBatch(service, ids, declared, false)
        }
    }

    /** `writeParcelable(AreaConfig)`: nome da classe + 3 valores + vetor + unidade + somente-leitura. */
    private fun skipAreaConfig(r: ParcelBytes) {
        r.readString()
        readValue(r); readValue(r); readValue(r)
        val extras = r.readInt()
        if (extras in 1..MAX_ITEMS) repeat(extras) { readValue(r) }
        r.readInt(); r.readInt()
    }

    /** `Parcel.writeValue`: marcador de tipo + valor. Tipos que não conhecemos interrompem a leitura (exceção). */
    private fun readValue(r: ParcelBytes): Pair<String, Number?> = when (val tag = r.readInt()) {
        -1 -> "null" to null
        0 -> (r.readString() ?: "null") to null
        1, 5, 20 -> r.readInt().let { it.toString() to it }
        6 -> r.readLong().let { it.toString() to it }
        7 -> r.readFloat().let { it.toString() to it }
        8 -> r.readDouble().let { it.toString() to it }
        9 -> r.readInt().let { (it != 0).toString() to (if (it != 0) 1 else 0) }
        else -> throw ParcelUnderflow("tipo de valor não suportado ($tag)")
    }

    /** Campos crus do DashBoardInfo ANTES de qualquer sentinela (ints e floats como chegaram). */
    val DASH_RAW_NAMES = listOf(
        "gearShiftPosition", "odo", "cruisingRange", "rotationRate", "speed", "runningTime", "fuelTankage",
        "fuel", "fuelPercentage", "avgFuelCont", "instFuelCont", "coolantTemp", "engineOilTemp", "inletTemp", "ambientTemp",
        "accPedal", "brakePedal", "throttlePos",
    )

    fun dashboardRaw(r: ParcelBytes): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            if (r.remainingBytes() < 4 || r.readInt() == 0) return out
            DASH_RAW_NAMES.forEachIndexed { i, name -> out[name] = if (i < 7 || i >= 15) r.readInt().toString() else r.readFloat().toString() }
        } catch (_: RuntimeException) { /* parcial */ }
        return out
    }
}
