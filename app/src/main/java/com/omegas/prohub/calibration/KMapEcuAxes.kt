package com.omegas.prohub.calibration

import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48TelemetryScale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Eixos do Mapa K lidos da ECU (SOMENTE LEITURA).
 *
 * O ProgBase lê `29 37 00` (TEMPI_PER_K, tempo de injeção em gasolina) e `29 3D 00` (GIRI_PER_K, RPM)
 * na conexão. Cada resposta é `53 18` + 12 inteiros de 16 bits little-endian + checksum.
 * Escala (PortmonLOGNOVO): RPM direto (0x03E8 = 1000); tempo `raw × 0,00256 ms`
 * (781 = 2,0 ms; 3125 = 8,0 ms), a mesma escala da injeção na telemetria.
 *
 * Se a leitura falhar ou vier inválida, o eixo volta ao contrato fixo [KMapPhysicalAxes] e o motivo
 * vai para os Detalhes técnicos (erro de transporte ≠ erro da ECU). Nada aqui escreve na ECU.
 */
object KMapEcuAxes {
    const val TIME_ADDRESS = 0x0037
    const val RPM_ADDRESS = 0x003D
    const val POINTS = KMapPhysicalAxes.COLUMNS

    const val SOURCE_ECU = "ECU"
    const val SOURCE_FIXED = "FIXO"
    const val SOURCE_PARTIAL = "PARCIAL"

    private const val MIN_RPM = 100
    private const val MAX_RPM = 12_000
    private const val MIN_MS = 0.1
    private const val MAX_MS = 100.0

    /** `29 37 00 60`. */
    val READ_TIME: ByteArray = Mp48Protocol.frame(byteArrayOf(0x29, 0x37, 0x00))

    /** `29 3D 00 66`. */
    val READ_RPM: ByteArray = Mp48Protocol.frame(byteArrayOf(0x29, 0x3D, 0x00))

    /** O que a ECU respondeu (ou por que não respondeu) a uma leitura de eixo. */
    class Attempt(val status: Int, val payload: ByteArray, val error: String = "") {
        companion object {
            fun failed(error: String) = Attempt(-1, byteArrayOf(), error)
        }
    }

    class Resolved(
        val rpmBins: IntArray,
        val petrolBins: DoubleArray,
        val rpmSource: String,
        val petrolSource: String,
        val notes: List<String>,
        val rpmRawHex: String,
        val petrolRawHex: String,
    ) {
        val source: String
            get() = when {
                rpmSource == SOURCE_ECU && petrolSource == SOURCE_ECU -> SOURCE_ECU
                rpmSource == SOURCE_FIXED && petrolSource == SOURCE_FIXED -> SOURCE_FIXED
                else -> SOURCE_PARTIAL
            }

        val differsFromLock: Boolean
            get() = !rpmBins.contentEquals(KMapPhysicalAxes.rpmBins()) ||
                !petrolBins.contentEquals(KMapPhysicalAxes.petrolBins())

        /** Frase para os Detalhes técnicos (registro do sistema). */
        fun summary(): String {
            val head = when (source) {
                SOURCE_ECU -> "Eixos do Mapa K lidos da ECU"
                SOURCE_FIXED -> "Eixos do Mapa K FIXOS (leitura da ECU não aproveitada)"
                else -> "Eixos do Mapa K parcialmente da ECU (RPM=$rpmSource, tempo=$petrolSource)"
            }
            val diff = if (differsFromLock) " • DIFERENTES do contrato fixo" else " • iguais ao contrato fixo"
            val tail = if (notes.isEmpty()) "" else " • " + notes.joinToString("; ")
            return head + diff + tail
        }

        fun toJson(): JSONObject = KMapPhysicalAxes.json()
            .put("rpmBins", JSONArray(rpmBins.toList()))
            .put("petrolBins", JSONArray(petrolBins.toList()))
            .put("source", source)
            .put("rpmSource", rpmSource)
            .put("petrolSource", petrolSource)
            .put("differsFromLock", differsFromLock)
            .put("rpmRawHex", rpmRawHex)
            .put("petrolRawHex", petrolRawHex)
            .put("notes", JSONArray(notes))
    }

    /** Eixos fixos do contrato, sem leitura da ECU (também usado quando nada foi lido nesta sessão). */
    fun fixed(reason: String = ""): Resolved = resolve(
        Attempt.failed(reason.ifBlank { "não lido" }),
        Attempt.failed(reason.ifBlank { "não lido" }),
    )

    /** Decodifica RPM; lança [IllegalArgumentException] com a razão quando a resposta não serve. */
    fun decodeRpm(attempt: Attempt): IntArray {
        val raw = rawValues(attempt, "RPM")
        val rpm = IntArray(POINTS) { raw[it] }
        require(rpm.all { it in MIN_RPM..MAX_RPM }) { "RPM fora de $MIN_RPM..$MAX_RPM" }
        requireAscending(rpm.map { it.toDouble() }, "RPM")
        return rpm
    }

    /** Decodifica o tempo de injeção (ms, 2 casas); lança [IllegalArgumentException] se inválido. */
    fun decodePetrolMs(attempt: Attempt): DoubleArray {
        val raw = rawValues(attempt, "tempo")
        val ms = DoubleArray(POINTS) { Math.round(Mp48TelemetryScale.injectionMs(raw[it]) * 100.0) / 100.0 }
        require(ms.all { it in MIN_MS..MAX_MS }) { "tempo fora de $MIN_MS..$MAX_MS ms" }
        requireAscending(ms.toList(), "tempo")
        return ms
    }

    fun resolve(rpm: Attempt, time: Attempt): Resolved {
        val notes = ArrayList<String>()
        val fixedRpm = KMapPhysicalAxes.rpmBins()
        val fixedMs = KMapPhysicalAxes.petrolBins()
        val rpmBins = try {
            decodeRpm(rpm)
        } catch (error: Exception) {
            notes += "RPM (29 3D 00): ${error.message ?: "inválido"}; usando eixo fixo"
            null
        }
        val msBins = try {
            decodePetrolMs(time)
        } catch (error: Exception) {
            notes += "tempo (29 37 00): ${error.message ?: "inválido"}; usando eixo fixo"
            null
        }
        return Resolved(
            rpmBins = rpmBins ?: fixedRpm,
            petrolBins = msBins ?: fixedMs,
            rpmSource = if (rpmBins != null) SOURCE_ECU else SOURCE_FIXED,
            petrolSource = if (msBins != null) SOURCE_ECU else SOURCE_FIXED,
            notes = notes,
            rpmRawHex = rpm.payload.hex(),
            petrolRawHex = time.payload.hex(),
        )
    }

    private fun rawValues(attempt: Attempt, label: String): IntArray {
        require(attempt.error.isBlank()) { attempt.error }
        require(attempt.status == Mp48Protocol.STATUS_ACK) {
            if (attempt.status < 0) "sem resposta" else "ECU recusou (status 0x%02X)".format(attempt.status)
        }
        require(attempt.payload.size == POINTS * 2) {
            "$label: ${attempt.payload.size} bytes; esperado ${POINTS * 2}"
        }
        return IntArray(POINTS) { i ->
            (attempt.payload[i * 2].toInt() and 0xFF) or ((attempt.payload[i * 2 + 1].toInt() and 0xFF) shl 8)
        }
    }

    private fun requireAscending(values: List<Double>, label: String) {
        for (i in 1 until values.size) {
            require(values[i] > values[i - 1]) { "$label não crescente no ponto ${i + 1}" }
        }
    }

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
