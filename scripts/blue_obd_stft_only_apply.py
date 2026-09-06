#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "app/src/main/java/com/omegas/prohub/obd/ObdAssistManager.kt"
text = TARGET.read_text(encoding="utf-8")
start_marker = "    private fun pollCycle(sock: BluetoothSocket) {\n"
end_marker = "    private fun readContext(sock: BluetoothSocket): ContextReadings {\n"
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("pollCycle/readContext boundary not found")

new_poll = '''    private fun pollCycle(sock: BluetoothSocket) {
        val cycleStartedAt = System.currentTimeMillis()
        val stftRead = readPidTimed(sock, "0106", 0x06)
        val rawStft = stftRead.bytes?.firstOrNull()
        val stft = rawStft?.let { ObdStftCodec.percent(it) }
        val now = System.currentTimeMillis()
        val cycleMs = (now - cycleStartedAt).coerceAtLeast(0L)

        synchronized(stateLock) {
            pollSequence += 1L
            lastCycleMs = cycleMs
            if (pollWindowStartedAt == 0L || now - pollWindowStartedAt > 10_000L) {
                pollWindowStartedAt = now
                pollWindowCycles = 0
            }
            pollWindowCycles += 1
        }

        val payload = JSONObject()
            .put("connected", true)
            .put("mode", "local")
            .put("state", "CONECTADO")
            .put("connectionStage", ElmStage.LIVE.name)
            .put("stft", stft ?: JSONObject.NULL)
            // Compatibilidade de leitura da UI: estes sinais não são mais
            // consultados pelo loop científico OBD e portanto ficam ausentes.
            .put("ltft", JSONObject.NULL)
            .put("rpm", JSONObject.NULL)
            .put("speed", JSONObject.NULL)
            .put("coolant", JSONObject.NULL)
            .put("load", JSONObject.NULL)
            .put("throttle", JSONObject.NULL)
            .put("mapKpa", JSONObject.NULL)
            .put("intakeAirC", JSONObject.NULL)
            .put("mafGps", JSONObject.NULL)
            .put("fuelLevelPct", JSONObject.NULL)
            .put("moduleVoltageV", JSONObject.NULL)
            .put("closedLoop", JSONObject.NULL)
            .put("fuel", JSONObject.NULL)
            .put("fuelSource", "PENDING_MP48_PAIR")
            .put("manualFuel", settings.obdManualFuel.ifBlank { JSONObject.NULL })
            .put("quality", if (stft != null) "STFT_ONLY" else "SEM DADOS")
            .put("reason", if (stft != null) "STFT adquirido; aguardando pareamento MP48" else "PID 0106/STFT sem resposta")
            .put("learningState", if (stft != null) "STFT_OBSERVED" else "PAUSADO")
            .put("physicalCellAvailable", false)
            .put("conditionState", "AGUARDANDO_PAREAMENTO_MP48")
            .put("pollCycleMs", cycleMs)
            .put("requestedAtMs", stftRead.startedAtMs)
            .put("observedAtMs", stftRead.observedAtMs)
            .put("pidObservedAt", JSONObject().put("stft", stftRead.observedAtMs))
            .put("pidReadStartedAt", JSONObject().put("stft", stftRead.startedAtMs))
            .put("pidAgeMs", JSONObject().put("stft", pidAgeMs(stftRead, now)))
            .put("sessionLive", true)
            .put("sessionStartedAt", currentSessionStartedAt)
            .put("updatedAt", now)

        synchronized(stateLock) { live = payload }
        if (stft != null) {
            try {
                onLiveSample(
                    JSONObject()
                        .put("kind", "STFT_OBSERVATION")
                        .put("stft", stft)
                        .put("rawByte", rawStft)
                        .put("requestedAtMs", stftRead.startedAtMs)
                        .put("observedAtMs", stftRead.observedAtMs)
                        .put("pollCycleMs", cycleMs)
                        .put("mode", "local"),
                )
            } catch (_: Exception) {}
        }
        onStateChanged()
        try {
            Thread.sleep(settings.obdPollIntervalMs.coerceIn(150L, 3000L))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

'''
TARGET.write_text(text[:start] + new_poll + text[end:], encoding="utf-8")
print("BLUE_OBD_STFT_ONLY_PATCH=APPLIED")
