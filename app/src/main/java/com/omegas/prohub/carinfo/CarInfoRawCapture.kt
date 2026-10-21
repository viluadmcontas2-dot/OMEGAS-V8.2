package com.omegas.prohub.carinfo

import org.json.JSONArray
import org.json.JSONObject

/**
 * Captura crua do que a multimídia manda, para decidir OFFLINE com os logs de sessão (sem novo ciclo de teste no carro).
 * Por transação do ouvinte (1..17): quantas chamadas chegaram, último tamanho e início do payload em hex. Mais: campos crus
 * do DashBoardInfo ANTES da sentinela, propriedades da tx 17 (propId/areaId=valor), campos da CabinInfo (tx 2) e propIds
 * da lista de configuração (tx 14). Sem Android, sem relógio próprio (testável). SOMENTE LEITURA.
 *
 * Limites de log: o evento `car_info_raw` sai no máximo 1 a cada [RAW_EVENT_EVERY_MS] (5 s), só quando o conteúdo mudou
 * (contadores não contam), com teto de [RAW_EVENT_MAX] por ligação; cada hex tem no máximo [HEX_BYTES] bytes.
 */
class CarInfoRawCapture(private val clock: () -> Long) {
    companion object {
        const val TX_COUNT = 17
        const val HEX_BYTES = 48
        const val RAW_EVENT_EVERY_MS = 5_000L
        const val RAW_EVENT_MAX = 720
        const val TX_CABIN = 2
        const val TX_DASHBOARD = 6
        const val TX_PROPERTY_LIST = 14
        const val TX_CAR_PROPS = 17
    }

    private val calls = IntArray(TX_COUNT + 1)
    private val lastBytes = IntArray(TX_COUNT + 1)
    private val lastHex = arrayOfNulls<String>(TX_COUNT + 1)
    private var dashRaw: Map<String, String> = emptyMap()
    private var cabin: CabinFields? = null
    private var props: CarPropBatch? = null
    private var configs: CarPropConfigBatch? = null
    private var initial: String? = null
    private var lastRawAt = 0L
    private var lastSignature = ""
    private var rawEvents = 0

    @Synchronized fun reset() {
        calls.fill(0); lastBytes.fill(0); lastHex.fill(null)
        dashRaw = emptyMap(); cabin = null; props = null; configs = null; initial = null
        lastRawAt = 0L; lastSignature = ""; rawEvents = 0
    }

    /** Registra uma chamada do ouvinte e decodifica de forma tolerante as que sabemos. Nunca lança. */
    @Synchronized fun onTransaction(code: Int, payload: ByteArray) {
        if (code !in 1..TX_COUNT) return
        calls[code] += 1
        lastBytes[code] = payload.size
        lastHex[code] = ParcelBytes.hex(payload, HEX_BYTES)
        try {
            when (code) {
                TX_DASHBOARD -> dashRaw = CarInfoExtraDecoders.dashboardRaw(ParcelBytes(payload))
                TX_CABIN -> cabin = CarInfoExtraDecoders.decodeCabin(ParcelBytes(payload))
                TX_PROPERTY_LIST -> configs = CarInfoExtraDecoders.decodePropertyList(ParcelBytes(payload))
                TX_CAR_PROPS -> props = CarInfoExtraDecoders.decodeCarPropValues(ParcelBytes(payload))
            }
        } catch (_: RuntimeException) { /* captura nunca derruba o binder */ }
    }

    /** Foto inicial (tx 18 do ICanBus), fora da conta dos 17 canais do ouvinte. */
    @Synchronized fun onInitialSnapshot(payload: ByteArray) {
        initial = ParcelBytes.hex(payload, HEX_BYTES * 2)
        try { if (dashRaw.isEmpty()) dashRaw = CarInfoExtraDecoders.dashboardRaw(ParcelBytes(payload)) } catch (_: RuntimeException) { /* ignora */ }
    }

    @Synchronized fun activeChannels(): Int = (1..TX_COUNT).count { calls[it] > 0 }

    @Synchronized fun lastProps(): CarPropBatch? = props

    private fun content(): JSONObject {
        val json = JSONObject()
        if (dashRaw.isNotEmpty()) json.put("dashboard", JSONObject(dashRaw as Map<*, *>))
        props?.let { p ->
            json.put("props", JSONObject().put("service", p.service ?: JSONObject.NULL).put("n", p.declared).put("complete", p.complete)
                .put("values", JSONArray(p.props.map { it.label() })))
        }
        cabin?.let { c -> json.put("cabin", JSONObject(c.values as Map<*, *>).put("complete", c.complete)) }
        configs?.let { c ->
            json.put("propertyList", JSONObject().put("service", c.service ?: JSONObject.NULL).put("n", c.declared).put("complete", c.complete)
                .put("propIds", JSONArray(c.propIds.map { "0x" + Integer.toHexString(it) })))
        }
        val hex = JSONObject()
        for (code in 1..TX_COUNT) if (calls[code] > 0 && code != TX_DASHBOARD) hex.put(code.toString(), lastHex[code] ?: "")
        if (hex.length() > 0) json.put("hex", hex)
        return json
    }

    private fun txJson(): JSONObject {
        val tx = JSONObject()
        for (code in 1..TX_COUNT) if (calls[code] > 0) tx.put(code.toString(), JSONObject().put("n", calls[code]).put("bytes", lastBytes[code]))
        return tx
    }

    /** Para o snapshot `carinfo`: contadores, conteúdo cru e hex do tx 6/foto inicial (infrequente, sem limite de taxa). */
    @Synchronized fun json(): JSONObject {
        val json = content()
        json.put("channelsActive", activeChannels()).put("channelsTotal", TX_COUNT).put("tx", txJson())
        if (calls[TX_DASHBOARD] > 0) json.put("dashboardHex", lastHex[TX_DASHBOARD] ?: "")
        initial?.let { json.put("initialHex", it) }
        return json
    }

    /** Evento `car_info_raw` quando devido (≥ 5 s do anterior, conteúdo mudou, abaixo do teto); senão null. */
    @Synchronized fun rawEventIfDue(): JSONObject? {
        val now = clock()
        if (rawEvents >= RAW_EVENT_MAX) return null
        if (rawEvents > 0 && now - lastRawAt < RAW_EVENT_EVERY_MS) return null
        val content = content()
        if (content.length() == 0) return null
        val signature = content.toString()
        if (signature == lastSignature) return null
        lastSignature = signature
        lastRawAt = now
        rawEvents += 1
        return content.put("channelsActive", activeChannels()).put("tx", txJson())
    }
}
