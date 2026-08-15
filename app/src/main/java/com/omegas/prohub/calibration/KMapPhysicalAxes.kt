package com.omegas.prohub.calibration

import org.json.JSONArray
import org.json.JSONObject

/**
 * Fixture histórica de uma geometria MP48 observada.
 *
 * Estes valores continuam úteis para regressão, documentação e dados legados,
 * mas NÃO são a autoridade física do mapa K em runtime. A ECU expõe os vetores
 * configuráveis TEMPI_PER_K (0x0037) e GIRI_PER_K (0x003D); qualquer consumidor
 * material deve usar uma leitura nativa confirmada da sessão atual.
 *
 * Manter esta fixture estável evita reescrever histórico. Alterá-la significa
 * registrar outra observação histórica, não redefinir a geometria da ECU.
 */
object KMapPhysicalAxes {
    const val SCHEMA = "mp48-k-map-physical-axes-v1"
    const val LOCK_SHA256 = "0cc7273171fbe47a8d28235be00f1af49889d0934f6fb3c73fca35ccd2fee7c7"
    const val WRITABLE_ROWS = 12
    const val PROTOCOL_ROWS = 13
    const val COLUMNS = 12
    const val SPECIAL_ROW = "0C"
    const val RUNTIME_AUTHORITY = false

    private val RPM = intArrayOf(
        850, 1350, 1850, 2500, 3000, 3500,
        4000, 4500, 5000, 5500, 6000, 6500,
    )
    private val PETROL_MS = doubleArrayOf(
        2.0, 2.5, 3.0, 3.5, 4.5, 6.0,
        8.0, 10.0, 12.0, 14.0, 16.0, 18.0,
    )

    fun rpmBins(): IntArray = RPM.copyOf()
    fun petrolBins(): DoubleArray = PETROL_MS.copyOf()

    fun json(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("lockSha256", LOCK_SHA256)
        .put("immutablePhysicalContract", false)
        .put("runtimeAuthority", false)
        .put("historicalFixture", true)
        .put("writableRows", WRITABLE_ROWS)
        .put("protocolRows", PROTOCOL_ROWS)
        .put("columns", COLUMNS)
        .put("specialRow", SPECIAL_ROW)
        .put("rpmBins", JSONArray(RPM.toList()))
        .put("petrolBins", JSONArray(PETROL_MS.toList()))
}
