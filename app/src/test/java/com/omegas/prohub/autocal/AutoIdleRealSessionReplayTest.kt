package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Fuel
import com.omegas.prohub.ecu.AutoCalPointDeleteProtocol.Target
import com.omegas.prohub.ecu.Mp48TelemetryWindowSource
import com.omegas.prohub.ecu.NativeAnchorTelemetryWindow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Classe 3: entradas reais, relógio determinístico e APIs públicas do coordenador, que integra
 * AutoIdlePointCleaner, OutlierCurveTracker e IdleAcquisitionTracker (este só como evidência).
 * Os trackers privados não são inspecionados; não se prova sua classificação interna isoladamente.
 * Cada disparo recebe sucesso SIMULADO imediato, fora do callback de execução. Não cobre transporte,
 * bytes, readback físico, latência/falhas da ECU nem a evolução contrafactual após um apagamento:
 * snapshots posteriores continuam sendo os gravados, inclusive efeitos das ações manuais.
 * Avalia a cada evento disponível, sem inventar telemetria ou ticks entre linhas do recorte.
 * Usa t (disponibilidade do evento), não capturedAtMs, para não antecipar informação do snapshot.
 * Comparação manual = multiconjunto de combustível/banda na sessão, sem correspondência temporal
 * ou limiar de qualidade. Ausência de disparos é relatada, não é falha destas duas regras de segurança.
 */
class AutoIdleRealSessionReplayTest {
    private data class Delete(
        val at: Long,
        val targets: List<Target>,
        val telemetryFuel: String?,
        val evidence: JSONObject,
    )

    @Test
    fun `pista respeita intervalo global e combustivel da telemetria`() {
        replay("pista_2026-10-06_2030", compareManual = false)
    }

    @Test
    fun `util respeita intervalo e combustivel e relata comparacao com o dono`() {
        replay("util_2026-10-06_1921", compareManual = true)
    }

    private fun replay(name: String, compareManual: Boolean) {
        // sortedBy é estável: empates preservam a ordem original das linhas.
        val events = fixture(name).sortedBy { it.getLong("t") }
        val origin = events.first().getLong("t")
        var now = 0L
        var sessionStart = 0L
        var sessionId = 1L
        var enabled: Int? = null
        val frames = mutableListOf<NativeAnchorTelemetryWindow.Frame>()
        val deletes = mutableListOf<Delete>()
        val pending = mutableListOf<Delete>()
        val manual = mutableListOf<Target>()
        var manualActions = 0
        var manualWithoutTargets = 0
        var buffersRead = 0
        val coordinator = AutoIdleCleanupCoordinator(
            telemetry = object : Mp48TelemetryWindowSource {
                override fun recentTelemetryFrames(fromElapsedMs: Long, toElapsedMs: Long) =
                    frames.filter { it.elapsedMs in fromElapsedMs..toElapsedMs }
            },
            executeDelete = { targets, evidence ->
                val deletion = Delete(now, targets.toList(), frames.lastOrNull()?.fuel, evidence)
                deletes += deletion
                pending += deletion
                JSONObject().put("ok", true).put("started", true)
            },
            autoCalEnabled = { enabled },
            sessionAgeMs = { now - sessionStart },
            clock = { now },
            executor = { it.run() },
        )

        fun confirmPending() {
            pending.toList().forEach { deletion ->
                coordinator.onActionConfirmed(
                    JSONObject()
                        .put("action", "DELETE_POINT")
                        .put("outcome", "CONFIRMED")
                        .put("automatic", true)
                        .put("humanConfirmed", false)
                        .put("finishedAtMs", origin + now)
                        .put("details", JSONObject()
                            .put("fuel", deletion.targets.first().fuel.wireName)
                            .put("targets", JSONArray().also { array ->
                                deletion.targets.forEach { target ->
                                    array.put(JSONObject().put("fuel", target.fuel.wireName)
                                        .put("index", target.index))
                                }
                            })
                            .put("effective", true)
                            .put("otherFuelGuard", JSONObject().put("abnormal", false).put("changed", false))),
                )
            }
            pending.clear()
        }

        events.forEachIndexed { sequence, event ->
            now = event.getLong("t") - origin
            val data = event.getJSONObject("data")
            when (event.getString("type")) {
                "session_started" -> {
                    sessionStart = now
                    enabled = null
                    frames.clear()
                    coordinator.onSessionChanged(++sessionId)
                }
                "telemetry" -> frames += NativeAnchorTelemetryWindow.Frame(
                    sequence = sequence.toLong(),
                    elapsedMs = now,
                    rpm = finite(data, "rpm").toInt(),
                    mapBar = finite(data, "load_bar"),
                    petrolMs = finite(data, "petrol_ms"),
                    fuel = data.optString("fuel", "DESCONHECIDO"),
                )
                "autocal_native_snapshot" -> {
                    enabled = rawValues(data, "AUTO_CAL_ENABLE")?.firstOrNull()
                    for (fuel in listOf(Fuel.GAS, Fuel.PETROL)) {
                        val suffix = if (fuel == Fuel.GAS) "_GAS" else ""
                        val counters = rawValues(data, if (fuel == Fuel.GAS) "NUM_BUF_UPD_GAS" else "NUM_BUF_UPD_PETR")
                            ?: continue
                        val time = rawValues(data, "PETR_INJ_TBUF$suffix") ?: continue
                        val map = rawValues(data, "MNFLD_PRESS_BUF$suffix") ?: continue
                        coordinator.onBuffers(AutoIdleCleanupCoordinator.Buffers(
                            sessionId, fuel, counters, time, map, now,
                        ))
                        buffersRead++
                        confirmPending()
                    }
                }
                "autocal_native_action" -> {
                    if (!data.optBoolean("automatic", false)) {
                        if (data.optString("action") == "DELETE_POINT") {
                            manualActions++
                            val targets = data.optJSONObject("details")?.optJSONArray("targets")
                            if (targets == null || targets.length() == 0) {
                                manualWithoutTargets++
                            } else {
                                for (i in 0 until targets.length()) {
                                    val target = targets.getJSONObject(i)
                                    manual += Target(Fuel.parse(target.getString("fuel")), target.getInt("index"))
                                }
                            }
                        }
                        if (data.optString("outcome") == "CONFIRMED") coordinator.onRoundInvalidated()
                    }
                }
                "session_stopped" -> {
                    enabled = null
                    coordinator.onRoundInvalidated()
                }
            }
            coordinator.evaluate()
            confirmPending()
        }

        println("$name: ${deletes.size} apagamentos automáticos simulados, " +
            "${deletes.sumOf { it.targets.size }} pontos; ${frames.size} quadros, $buffersRead buffers")
        deletes.forEach { deletion ->
            println("$name: t=+${deletion.at} ms, telemetria=${deletion.telemetryFuel}, " +
                "alvos=${deletion.targets.map { "${it.fuel.wireName}/banda=${it.index}" }}, " +
                "evidência=${deletion.evidence}")
        }
        if (compareManual) reportComparison(name, deletes, manual, manualActions, manualWithoutTargets)

        // Asserções fora do executor: nenhuma exceção do teste pode ser engolida pelo coordenador.
        deletes.zipWithNext().forEach { (previous, current) ->
            assertTrue("$name: apagamentos em ${previous.at} e ${current.at} ms (< 5 s)",
                current.at - previous.at >= 5_000L)
        }
        deletes.forEach { deletion ->
            val drivingFuel = when (deletion.telemetryFuel?.uppercase()) {
                "GNV", "CNG" -> Fuel.GAS
                "GASOLINA", "PETROL" -> Fuel.PETROL
                else -> null
            }
            deletion.targets.forEach { target ->
                assertTrue("$name: ${target.fuel}/banda=${target.index} com carro em " +
                    "${deletion.telemetryFuel}, t=${deletion.at}", target.fuel == drivingFuel)
            }
        }
    }

    private fun reportComparison(
        name: String,
        deletes: List<Delete>,
        manual: List<Target>,
        actions: Int,
        withoutTargets: Int,
    ) {
        val automaticCounts = deletes.flatMap { it.targets }.groupingBy { it }.eachCount()
        val manualCounts = manual.groupingBy { it }.eachCount()
        println("$name: comparação por ocorrências/banda (índice base 0), sem pareamento temporal; " +
            "$actions DELETE_POINT manuais, $withoutTargets sem alvos registrados (não comparáveis)")
        var hits = 0
        var extras = 0
        var missed = 0
        (automaticCounts.keys + manualCounts.keys)
            .sortedWith(compareBy<Target> { it.fuel.wireName }.thenBy { it.index })
            .forEach { target ->
                val auto = automaticCounts[target] ?: 0
                val owner = manualCounts[target] ?: 0
                val hit = minOf(auto, owner)
                hits += hit
                extras += auto - hit
                missed += owner - hit
                println("${target.fuel.wireName}/banda=${target.index}: automático=$auto, dono=$owner, " +
                    "acertos=$hit, extras=${auto - hit}, não pegos=${owner - hit}")
            }
        println("$name: total acertos=$hits / extras=$extras / não pegos=$missed (somente relatório)")
    }

    private fun fixture(name: String): List<JSONObject> {
        val file = listOf("../fixtures/autocal/real/$name.jsonl.gz", "fixtures/autocal/real/$name.jsonl.gz")
            .map(::File).first { it.exists() }
        return GZIPInputStream(file.inputStream()).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { JSONObject(it) }.toList()
        }
    }

    private fun finite(data: JSONObject, key: String): Double =
        data.optDouble(key, 0.0).takeIf { it.isFinite() } ?: 0.0

    private fun rawValues(snapshot: JSONObject, key: String): IntArray? {
        val fields = snapshot.getJSONArray("fields")
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            if (field.optString("key") != key || field.optString("status") != "VALID") continue
            val hex = field.optString("rawPayloadHex").filterNot { it.isWhitespace() }
            if (hex.isNotEmpty()) {
                // AUTO_CAL_ENABLE é u8; contadores, tempos e pressões são u16 little-endian.
                val bytes = hex.chunked(2).map { it.toInt(16) }
                if (key == "AUTO_CAL_ENABLE") return intArrayOf(bytes.first())
                return IntArray(bytes.size / 2) { index -> bytes[index * 2] or (bytes[index * 2 + 1] shl 8) }
            }
            return RealSessionReplaySupport.rawValues(snapshot, key)
        }
        return null
    }
}
