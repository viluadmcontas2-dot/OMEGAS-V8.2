package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.Mp48Protocol
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
    /** MATRIX_ROW: pedido `2A lo hi linha` (uma linha de matriz; PARAM_PROGRESSIONI, OBS-FIO no LOGNOVO). */
    enum class Kind { SCALAR, VECTOR, INDEXED, MATRIX_ROW }

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
    private fun row(name: String, address: Int, index: Int, count: Int, width: Int) = Entry(name, address, Kind.MATRIX_ROW, width, count, false, index)

    /** Pedido de uma linha de matriz: `2A lo hi linha cs` (mesmo formato de `Mp48Protocol.readKRow`, outro endereço). */
    fun matrixRowRequest(address: Int, row: Int): ByteArray {
        require(address in 0..0xFFFF && row in 0..0xFF)
        return Mp48Protocol.frame(byteArrayOf(0x2A, (address and 0xFF).toByte(), ((address ushr 8) and 0xFF).toByte(), row.toByte()))
    }

    /**
     * O catálogo sai em GRUPOS: cada grupo é uma unidade curta e independente na serial (o snapshot inteiro, ~49 leituras,
     * numa só unidade podia estourar a espera e perder tudo). Falha de um grupo não descarta os outros. Sem relógio: a
     * ordem é a dos grupos e cada leitura espera a RESPOSTA da ECU.
     */
    private val NUCLEO: List<Entry> = listOf(
        // Anti-engasgo e troca de combustível
        v("ANTI_STALLO", 0x0133, 5, 2),
        s("GIRI_MIN_CAMBIO", 0x0010, 2),
        s("RITARDO_CAMBIO", 0x0011),
        v("CHANGE_OVER_CYL_DELAY", 0x00E2, 4, 2),
        s("GIRI_SUP_BENZINA", 0x0064, 2),
        s("RPM_FOR_SPLIT_FUEL", 0x00C8, 2),
        v("RITARDO_PASSAGGIO", 0x00DD, 8, 1),
    )

    /** Ferramentas > Avançado (2026-10-18): flags e vizinhos. Formas conferidas no LOGNOVO: `29 03 00`, `29 C0 00`, `29 FA 00`, `29 A1 00`, `2A 34 01 0..3`. */
    private val AVANCADO: List<Entry> = listOf(
        v("FLAG_CONF1", 0x0003, 2, 2),
        v("FLAG_CONF2", 0x00C0, 2, 2),
        v("FLAG_CONF3", 0x00FA, 2, 2),
        v("GIRI_PER_BENZINA", 0x00A1, 2, 2),
        row("PARAM_PROGRESSIONI_L0", 0x0134, 0, 3, 2),
        row("PARAM_PROGRESSIONI_L1", 0x0134, 1, 3, 2),
        row("PARAM_PROGRESSIONI_L2", 0x0134, 2, 3, 2),
        row("PARAM_PROGRESSIONI_L3", 0x0134, 3, 3, 2),
    )

    private val CORTE: List<Entry> = listOf(
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
    )

    private val APRENDIZADO: List<Entry> = listOf(
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

    /**
     * Volta à gasolina por pressão baixa (ProgBase F4; docs/progbase/protocolo/volta-automatica-gasolina.md): tabela de 15
     * pontos RIF_PRESS_DIFF (SC 123, mbar) e COEFF_PRESS_DIFF (SC 124, s16). Forma `29 7B 00` / `29 7C 00` CONFERIDA no LOGNOVO
     * (ACK). O campo da família LR (SC 152) NÃO é lido: nesta central o SC 152 é TINJ_FILTRO (8 × u16, LOGNOVO).
     * Grupo próprio: um NACK aqui não derruba nenhum outro grupo.
     */
    private val VOLTA: List<Entry> = listOf(
        v("RIF_PRESS_DIFF", 0x007B, 15, 2),
        Entry("COEFF_PRESS_DIFF", 0x007C, Kind.VECTOR, 2, 15, signed = true),
    )

    /**
     * Calibração do NÍVEL do cilindro, as mesmas leituras que o original faz ao conectar (Portmon LOGNOVO; pedidos e
     * respostas em `tests/ui/fixtures/ecu-estado-lognovo.json`): tipo do sensor (SC 0x24, `09 24 00`), 4 referências
     * (0x25, `29 25 00`), filtro passa-baixa FAST/SLOW (0x114, `0A 14 01 0i`, u16) e histerese + 4 limiares de LED
     * (0x12C, `0A 2C 01 0i`). Só LEITURA. Grupo próprio e por último: um NACK aqui não derruba nada.
     */
    private val NIVEL: List<Entry> = listOf(
        s("TIPO_SENSORE", 0x0024),
        v("RIF_SENSORE", 0x0025, 4, 1),
        Entry("LO_PASS_FILT_CON_FAST", 0x0114, Kind.INDEXED, 2, 1, false, 0),
        Entry("LO_PASS_FILT_CON_SLOW", 0x0114, Kind.INDEXED, 2, 1, false, 1),
        i("ISTERESI_RIACCENSIONE", 0x012C, 0),
        i("SOGLIA_LED_1", 0x012C, 1),
        i("SOGLIA_LED_2", 0x012C, 2),
        i("SOGLIA_LED_3", 0x012C, 3),
        i("SOGLIA_LED_4", 0x012C, 4),
    )

    /** Núcleo primeiro; o Avançado vai separado e por último (o que importa ao motor não espera pelas flags). */
    val GROUPS: List<List<Entry>> = listOf(NUCLEO, CORTE, APRENDIZADO, AVANCADO, VOLTA, NIVEL)

    /** Pedido (com checksum) de uma leitura do catálogo: só opcodes de LEITURA (`09` escalar, `29` vetor, `0A` indexado, `2A` linha). */
    fun request(entry: Entry): ByteArray = when (entry.kind) {
        Kind.SCALAR -> com.omegas.prohub.ecu.AutoCalProtocol.readScalar(entry.address)
        Kind.VECTOR -> com.omegas.prohub.ecu.AutoCalProtocol.readVector(entry.address)
        Kind.INDEXED -> com.omegas.prohub.ecu.AutoCalProtocol.readIndexed(entry.address, entry.index)
        Kind.MATRIX_ROW -> matrixRowRequest(entry.address, entry.index)
    }

    val CATALOG: List<Entry> = GROUPS.flatten()

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

    /**
     * Lê grupo a grupo (cada `readGroup` = uma unidade da serial). Se um grupo estoura ou a unidade falha, as leituras dele
     * ficam ERRO_TRANSPORTE (desconhecido, nunca 0) e os demais grupos seguem.
     */
    fun runGroups(readGroup: (List<Entry>) -> List<Item>): List<Item> = GROUPS.flatMap { group ->
        try {
            readGroup(group)
        } catch (error: Exception) {
            val detail = error.message ?: error.javaClass.simpleName
            group.map { Item(it, "ERRO_TRANSPORTE", null, detail) }
        }
    }

    fun run(read: (Entry) -> ReadOutcome): List<Item> = run(CATALOG, read)

    fun run(entries: List<Entry>, read: (Entry) -> ReadOutcome): List<Item> = entries.map { entry ->
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
