package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject

/**
 * Leitor SÓ-LEITURA dos parâmetros da ECU que importam para engasgo, troca de combustível, corte e qualidade do aprendizado.
 *
 * Endereços = SerialCode do DFM do ProgBase (docs/reference/progbase/parametros.json); a forma de leitura (`09` escalar,
 * `29` vetor, `0A` indexado) e a largura foram CONFERIDAS uma a uma contra o download de conexão do original
 * (PortmonLOGNOVO, 12/08/2026). Ficaram de fora RITARDO_CAMBIO_HIGH, CHANGE_OVER e SMAGRIMENTO_MIN (NACK `CA` nessa ECU)
 * e TEMPO_FUORI_STABILIZZATO, K_FILTRO e GIRI_INTERVENTO_K_FILTRO (o original não os leu: sem prova da forma).
 * `run` recebe só uma função de LEITURA: não há como escrever por aqui. Desconhecido nunca vira 0.
 */
object EcuParameterSnapshot {
    enum class Kind { SCALAR, VECTOR, INDEXED }

    class Entry(
        val name: String,
        val address: Int,
        val kind: Kind,
        /** Bytes por elemento (1 ou 2). */
        val width: Int,
        val count: Int = 1,
        val signed: Boolean = false,
        val index: Int = 0,
    )

    sealed class ReadOutcome {
        class Ok(val payload: IntArray) : ReadOutcome()
        class Nack(val status: Int) : ReadOutcome()
        class Failed(val message: String) : ReadOutcome()
    }

    class Item(val entry: Entry, val state: String, val values: List<Int>?, val detail: String)

    private fun s(name: String, address: Int, width: Int = 1, signed: Boolean = false) =
        Entry(name, address, Kind.SCALAR, width, 1, signed)

    private fun v(name: String, address: Int, count: Int, width: Int) = Entry(name, address, Kind.VECTOR, width, count)
    private fun i(name: String, address: Int, index: Int) = Entry(name, address, Kind.INDEXED, 1, 1, false, index)

    val CATALOG: List<Entry> = listOf(
        // Anti-engasgo e troca de combustível
        v("ANTI_STALLO", 0x0133, 5, 2),
        s("GIRI_MIN_CAMBIO", 0x0010, 2),
        s("RITARDO_CAMBIO", 0x0011),
        v("CHANGE_OVER_CYL_DELAY", 0x00E2, 4, 2),
        s("GIRI_SUP_BENZINA", 0x0064, 2),
        s("RPM_FOR_SPLIT_FUEL", 0x00C8, 2),
        v("RITARDO_PASSAGGIO", 0x00DD, 8, 1),
        // Corte e retorno do corte
        v("GIRI_TEMPO_CUTOFF", 0x0032, 2, 2),
        s("TEMPO_CORRENTE_CUTOFF", 0x0049, 2),
        s("NUMERO_INJ_BENZINA_CUTOFF", 0x006A),
        s("TEMPO_RITORNO_BENZINA", 0x003E),
        s("SMAGRIMENTO_RIENTRO_CUTOFF", 0x0088),
        s("NUMERO_INIETTATE_SMAGRIMENTO", 0x0089),
        s("SMAGRIMENTO_EXTRA", 0x0016),
        s("CORR_ARRICCHIMENTO", 0x0074, 1, true),
        s("RITARDO_GIRI_EMULAZIONE", 0x0087),
        s("RITARDO_GIRI_EMULAZIONE_HIGH", 0x006B),
        v("RIT_EMUL_RICCA", 0x0083, 12, 2),
        v("TEMPI_EXTRAINIETTATE", 0x009B, 3, 2),
        s("TEMPO_MAX_EXTRAINJ_BENZ", 0x009C, 2),
        v("PARAM_VARI", 0x0138, 10, 2),
        // Qualidade do aprendizado (AutoCal)
        v("CALIBRATION_VAL_1", 0x0172, 10, 1),
        s("MAX_RPM_FOR_AUTOCAL", 0x017A, 2),
        s("DIFF_ENG_SPD_THD", 0x0183, 2),
        s("DELTA_ENG_SPD_THD", 0x0184, 2),
        s("DIFF_MNFLD_PRESS_THD", 0x0185, 2),
        s("DELTA_MNFLD_PRESS_THD", 0x0186, 2),
        s("DIFF_PETR_TINJ_T_THD", 0x0187, 2),
        s("DELTA_PETR_INJ_T_THD", 0x0188, 2),
        s("DISABLE_ACQ_BAND", 0x018B),
        i("AUTOCAL_IDLE_MIN_BUF_PETR_THD", 0x0165, 0),
        i("AUTOCAL_IDLE_MIN_BUF_UPD_PETR_THD", 0x0165, 1),
        i("MAX_AUTOMATCH", 0x0165, 2),
    )

    /** Converte os bytes da resposta em valores; tamanho que não bate com o catálogo = null (desconhecido). */
    fun decode(entry: Entry, payload: IntArray): List<Int>? {
        if (payload.size != entry.width * entry.count) return null
        return List(entry.count) { element(payload, it * entry.width, entry.width, entry.signed) }
    }

    private fun element(bytes: IntArray, offset: Int, width: Int, signed: Boolean): Int {
        var value = 0
        for (k in 0 until width) value = value or ((bytes[offset + k] and 0xFF) shl (8 * k))
        if (signed && width in 1..3) {
            val sign = 1 shl (8 * width - 1)
            if (value and sign != 0) value -= sign shl 1
        }
        return value
    }

    fun run(read: (Entry) -> ReadOutcome): List<Item> = CATALOG.map { entry ->
        try {
            when (val outcome = read(entry)) {
                is ReadOutcome.Ok -> {
                    val values = decode(entry, outcome.payload)
                    if (values == null) Item(entry, "TAMANHO_INESPERADO", null, "${outcome.payload.size} bytes")
                    else Item(entry, "OK", values, "")
                }
                is ReadOutcome.Nack -> Item(entry, "NACK", null, "status 0x%02X".format(outcome.status))
                is ReadOutcome.Failed -> Item(entry, "ERRO_TRANSPORTE", null, outcome.message)
            }
        } catch (error: Exception) {
            Item(entry, "ERRO_TRANSPORTE", null, error.message ?: error.javaClass.simpleName)
        }
    }

    /** Evento `param_snapshot` da sessão: valor desconhecido vai como null, com o estado da leitura ao lado. */
    fun toJson(items: List<Item>, trigger: String): JSONObject {
        val params = JSONObject()
        for (item in items) {
            params.put(
                item.entry.name,
                JSONObject()
                    .put("address", item.entry.address)
                    .put("state", item.state)
                    .put("values", item.values?.let { JSONArray(it) } ?: JSONObject.NULL)
                    .put("detail", item.detail),
            )
        }
        return JSONObject()
            .put("trigger", trigger)
            .put("okCount", items.count { it.state == "OK" })
            .put("unknownCount", items.count { it.state != "OK" })
            .put("params", params)
    }
}
