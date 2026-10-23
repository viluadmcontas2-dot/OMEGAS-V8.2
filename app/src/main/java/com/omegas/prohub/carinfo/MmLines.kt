package com.omegas.prohub.carinfo

import org.json.JSONArray
import org.json.JSONObject

/** Dedupe por conteúdo: só uma MUDANÇA de payload de cada transação (1..17) vira linha crua. Sem relógio. */
class MmChangeFilter {
    private val last = HashMap<Int, String>()

    @Synchronized
    fun changed(tx: Int, payload: ByteArray): Boolean {
        val key = payload.contentToString()
        if (last[tx] == key) return false
        last[tx] = key
        return true
    }

    @Synchronized fun reset() = last.clear()
}

/** Linhas do fluxo cru `mm` do export v2: payload INTEIRO em hex (nenhum id da tx 17 se perde) e as propriedades já legíveis. */
object MmLines {
    fun tx(tx: Int, payload: ByteArray): JSONObject =
        JSONObject().put("tx", tx).put("bytes", payload.size).put("hex", ParcelBytes.hex(payload, payload.size))

    /** Todas as propriedades decodificadas (`propId/areaId=valor`), sem o teto de itens do cartão. */
    fun props(batch: CarPropBatch): JSONObject =
        JSONObject().put("service", batch.service ?: JSONObject.NULL).put("declared", batch.declared).put("complete", batch.complete)
            .put("values", JSONArray(batch.props.map { it.label() }))
}
