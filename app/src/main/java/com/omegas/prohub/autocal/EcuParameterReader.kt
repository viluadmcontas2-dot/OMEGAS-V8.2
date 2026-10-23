package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.EcuParameterSnapshot.Entry
import com.omegas.prohub.autocal.EcuParameterSnapshot.Kind
import com.omegas.prohub.autocal.EcuParameterSnapshot.ReadOutcome
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import org.json.JSONObject

/** Liga o leitor de parâmetros à serial. SÓ LEITURA (classe READ_ONLY, comandos `09`/`29`/`0A`); nenhuma escrita existe aqui. */
class EcuParameterReader(private val serial: Mp48SerialScheduler) {
    fun readOnce(trigger: String): JSONObject {
        val sessionId = serial.currentSessionId().takeIf { serial.isConnected() && it > 0L }
            ?: throw IllegalStateException("USB desconectado")
        // Uma unidade curta por grupo (Avançado separado do núcleo): estourar um grupo não perde o param_snapshot inteiro.
        val items = EcuParameterSnapshot.runGroups { group ->
            serial.unit(
                reason = "leitura de parâmetros da ECU (só leitura)",
                expectedSessionId = sessionId,
                workClass = Mp48WorkClass.READ_ONLY,
                telemetryAfter = true,
                waitTimeoutMs = 1_500L,
            ) { unit -> EcuParameterSnapshot.run(group) { entry -> read(unit, entry) } }
        }
        return EcuParameterSnapshot.toJson(items, trigger)
    }

    private fun read(unit: Mp48SerialUnit, entry: Entry): ReadOutcome {
        val request = EcuParameterSnapshot.request(entry)
        val reply = unit.transaction(request, "parâmetro ${entry.name}", 900, purgeBefore = false)
        return when {
            reply.ok && reply.status == Mp48Protocol.STATUS_ACK -> ReadOutcome.Ok(IntArray(reply.payload.size) { reply.payload[it].toInt() and 0xFF })
            reply.status >= 0 -> ReadOutcome.Nack(reply.status)
            else -> ReadOutcome.Failed(reply.error.ifBlank { "sem resposta" })
        }
    }
}
