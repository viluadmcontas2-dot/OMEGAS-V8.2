package com.omegas.prohub.autocal

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCalReceiptTrimTest {
    private fun receipt(id: Int, automatic: Boolean) = JSONObject().put("id", id).put("automatic", automatic)

    private fun ids(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it).getInt("id") }

    @Test
    fun `apagamentos automaticos nao empurram recibos manuais para fora`() {
        val all = JSONArray()
        all.put(receipt(1, automatic = false))
        all.put(receipt(2, automatic = false))
        repeat(10) { all.put(receipt(100 + it, automatic = true)) }

        val trimmed = AutoCalNativeActionManager.trimReceipts(all, max = 3)

        assertEquals(listOf(1, 2, 107, 108, 109), ids(trimmed))
    }

    @Test
    fun `cada categoria guarda os mais recentes na ordem original`() {
        val all = JSONArray()
        all.put(receipt(1, false)); all.put(receipt(2, true)); all.put(receipt(3, false))
        all.put(receipt(4, true)); all.put(receipt(5, false)); all.put(receipt(6, true))

        val trimmed = AutoCalNativeActionManager.trimReceipts(all, max = 2)

        assertEquals(listOf(3, 4, 5, 6), ids(trimmed))
    }

    @Test
    fun `abaixo do limite nada muda`() {
        val all = JSONArray().put(receipt(1, false)).put(receipt(2, true))
        assertEquals(listOf(1, 2), ids(AutoCalNativeActionManager.trimReceipts(all, max = 200)))
    }
}
