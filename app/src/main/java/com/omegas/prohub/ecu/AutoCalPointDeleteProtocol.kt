package com.omegas.prohub.ecu

/**
 * Exclusão pontual de aquisição AutoCal reconstruída do ProgBase 4.2.0.6 canônico.
 *
 * DFM/RTTI/disassembly remotos provam:
 * GAS_POINT_2DELETE=0x016E U8[18], PETROL_POINT_2DELETE=0x016D U8[18];
 * selecionado=0, preservado=1; os dois masks completos são persistidos;
 * o commit final é 01 24 05 2A.
 */
object AutoCalPointDeleteProtocol {
    const val POINT_COUNT = 18
    const val KEEP = 1
    const val DELETE = 0
    const val PETROL_DELETE_ADDRESS = 0x016D
    const val GAS_DELETE_ADDRESS = 0x016E

    enum class Fuel(val wireName: String, val label: String, val address: Int) {
        PETROL("PETROL", "gasolina", PETROL_DELETE_ADDRESS),
        GAS("GAS", "GNV", GAS_DELETE_ADDRESS);

        companion object {
            fun parse(value: String): Fuel = when (value.trim().uppercase()) {
                "PETROL", "PETROLINA", "GASOLINA" -> PETROL
                "GAS", "GNV", "CNG" -> GAS
                else -> throw IllegalArgumentException("Combustível inválido para readquirir ponto")
            }
        }
    }

    data class Target(val fuel: Fuel, val index: Int) {
        init {
            require(index in 0 until POINT_COUNT) { "Ponto AutoCal inválido: $index" }
        }

        val zone: Int
            get() = when (index) {
                in 0..5 -> 1
                in 6..9 -> 2
                in 10..13 -> 3
                else -> 4
            }

        fun toLabel(): String = "${fuel.label} · ponto ${index + 1} · Z$zone"
    }

    fun maskFor(target: Target, fuel: Fuel): IntArray =
        IntArray(POINT_COUNT) { index ->
            if (fuel == target.fuel && index == target.index) DELETE else KEEP
        }

    /**
     * ProgBase monta os 18 defaults em memória e chama ResetDefault(true),
     * que persiste o TAebVector inteiro via TAebProtocol::SetVector.
     */
    fun writeMaskVector(fuel: Fuel, values: IntArray): ByteArray {
        require(values.size == POINT_COUNT)
        require(values.all { it == KEEP || it == DELETE })
        return AutoCalProtocol.writeVectorU8(fuel.address, values)
    }

    fun singlePointPlan(target: Target): List<ByteArray> = listOf(
        writeMaskVector(Fuel.GAS, maskFor(target, Fuel.GAS)),
        writeMaskVector(Fuel.PETROL, maskFor(target, Fuel.PETROL)),
        commit(),
    )

    fun commit(): ByteArray = Mp48Protocol.frame(byteArrayOf(0x01, 0x24, 0x05))
}
