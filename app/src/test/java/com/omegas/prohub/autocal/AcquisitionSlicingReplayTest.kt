package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48Protocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lote D, classe 3 (replay de tráfego real): o tráfego capturado do ProgBase original
 * (`tests/fixtures/portmon-autocal-cycle-v1.json`) fornece (1) as RESPOSTAS reais de cada campo AutoCal e
 * (2) o modelo de custo do barramento (ms por resposta, ajustado nas leituras adjacentes do próprio dump).
 *
 * Um barramento simulado roda duas políticas sobre as MESMAS respostas:
 *  - baseline: a unidade inseparável antiga (10 leituras + probe, ~0,5 s com a porta presa);
 *  - fatiada: o código de produção ([NativeAutoCalRefreshPlanner] + [SlotArbiter] + [SliceStepper]).
 * E prova: bytes idênticos por rodada, maior trecho sem quadro vivo, >= 3 quadros vivos entre grupos e
 * snapshot campo a campo igual ao baseline. O laço de simulação espelha o tick do monitor (confirmar,
 * ler um grupo, probe); as decisões vêm do [SliceStepper], o mesmo objeto usado em produção.
 */
class AcquisitionSlicingReplayTest {
    private data class Tx(
        val sequence: Int,
        val requestText: String,
        val atMs: Double,
        val responseLen: Int,
        val status: Int,
        val payload: ByteArray,
    )

    private data class Reply(val status: Int, val payload: ByteArray, val responseLen: Int)

    private enum class Kind { LIVE, ACQ, PROBE }

    private data class Ev(val start: Double, val end: Double, val kind: Kind, val cmd: String)

    private fun fixture(name: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, "tests/fixtures/$name")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Fixture not found: $name from ${System.getProperty("user.dir")}")
    }

    private fun hexBytes(value: String): ByteArray =
        value.trim().split(Regex("\\s+")).filter(String::isNotBlank).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private val txs: List<Tx> by lazy {
        val array = JSONObject(fixture("portmon-autocal-cycle-v1.json").readText()).getJSONArray("transactions")
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            val request = hexBytes(row.getString("request"))
            val response = hexBytes(row.getString("response"))
            Tx(
                sequence = row.getInt("sequence"),
                requestText = row.getString("request"),
                atMs = row.getDouble("at_ms"),
                responseLen = response.size,
                status = response[request.size].toInt() and 0xFF,
                payload = response.copyOfRange(request.size + 2, response.size - 1),
            )
        }
    }

    /** Resposta real por comando (primeira ocorrência no dump); campos que o ProgBase não lê ficam sintéticos. */
    private val replies: Map<String, Reply> by lazy {
        val real = LinkedHashMap<String, Reply>()
        txs.forEach { tx -> real.getOrPut(tx.requestText) { Reply(tx.status, tx.payload, tx.responseLen) } }
        val all = LinkedHashMap(real)
        (NativeAutoCalRefreshPlanner.Group.values().flatMap { it.fields }).forEach { field ->
            val text = AutoCalProtocol.read(field).hex()
            if (text !in all) {
                val size = field.expectedElementsHint!! * field.encoding.bytesPerElement
                val payload = ByteArray(size) { index -> (index * 7 + field.address).toByte() }
                // Valores pequenos e positivos em todas as codificações: o 2º byte de cada elemento fica 0.
                for (i in payload.indices) if (i % field.encoding.bytesPerElement != 0) payload[i] = 0
                all[text] = Reply(Mp48Protocol.STATUS_ACK, payload, AutoCalProtocol.read(field).size + 2 + size + 1)
            }
        }
        all
    }

    private val probeText = AutoCalProtocol.CMD_NATIVE_STATUS.hex()

    private fun replyFor(field: AutoCalProtocol.Field): Reply = replies.getValue(AutoCalProtocol.read(field).hex())

    private fun probeReply(countDelta: Int = 0): Reply {
        val base = replies.getValue(probeText)
        val payload = base.payload.copyOf()
        payload[13] = ((payload[13].toInt() and 0xFF) + countDelta).toByte()
        return Reply(base.status, payload, base.responseLen)
    }

    // ---- modelo de custo ajustado no dump real -------------------------------------------------

    private class CostModel(val a: Double, val b: Double, val liveMs: Double) {
        fun ms(responseLen: Int): Double = a + b * responseLen
    }

    private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]

    private val model: CostModel by lazy {
        fun adjacentDeltas(len: Int): List<Double> = txs.zipWithNext()
            .filter { (cur, next) -> cur.responseLen == len && cur.requestText != "48 01 49" && next.sequence == cur.sequence + 1 }
            .map { (cur, next) -> next.atMs - cur.atMs }
        val c43 = median(adjacentDeltas(43))
        val c67 = median(adjacentDeltas(67))
        val live = median(
            txs.zipWithNext()
                .filter { (cur, next) -> cur.requestText == "48 01 49" && next.requestText == "48 01 49" && next.sequence == cur.sequence + 1 }
                .map { (cur, next) -> next.atMs - cur.atMs },
        )
        val b = (c67 - c43) / (67 - 43)
        CostModel(a = c43 - b * 43, b = b, liveMs = live)
    }

    @Test
    fun `cost model is fitted on real captured timings`() {
        assertTrue("leitura de 43 bytes ~49 ms: ${model.ms(43)}", model.ms(43) in 40.0..60.0)
        assertTrue("quadro vivo ~46 ms: ${model.liveMs}", model.liveMs in 40.0..52.0)
        assertTrue("grupo de 3 leituras de 18 elementos cabe em ~160 ms", 3 * model.ms(43) <= 160.0)
    }

    // ---- barramento simulado -------------------------------------------------------------------

    private inner class SimBus(startMs: Double) {
        var now = startMs
        var liveCount = 0L
        val events = ArrayList<Ev>()

        fun liveFrame() {
            events += Ev(now, now + model.liveMs, Kind.LIVE, "48 01 49")
            now += model.liveMs
            liveCount += 1
        }

        fun liveUntil(t: Double) {
            while (now < t) liveFrame()
        }

        fun transaction(kind: Kind, cmd: ByteArray, responseLen: Int) {
            val cost = model.ms(responseLen)
            events += Ev(now, now + cost, kind, cmd.hex())
            now += cost
        }
    }

    private fun maxContiguousNonLiveMs(events: List<Ev>): Double {
        var best = 0.0
        var run = 0.0
        var previous: Ev? = null
        for (event in events) {
            if (event.kind == Kind.LIVE) {
                run = 0.0
            } else {
                val contiguous = previous != null && previous.kind != Kind.LIVE && event.start - previous.end < 1e-6
                run = if (contiguous) run + (event.end - event.start) else event.end - event.start
                if (run > best) best = run
            }
            previous = event
        }
        return best
    }

    private fun busShare(events: List<Ev>): Double {
        val busy = events.filter { it.kind != Kind.LIVE }.sumOf { it.end - it.start }
        return busy / (events.last().end - events.first().start)
    }

    /** Quadros vivos entre o fim de uma corrida contígua de leituras (ACQ) e o início da seguinte. */
    private fun liveFramesBetweenGroups(events: List<Ev>): List<Int> {
        val gaps = ArrayList<Int>()
        var inGroup = false
        var lives = 0
        var seenGroup = false
        for (event in events) {
            when (event.kind) {
                Kind.ACQ -> {
                    if (!inGroup) {
                        if (seenGroup) gaps += lives
                        inGroup = true
                    }
                    seenGroup = true
                }
                Kind.LIVE -> {
                    if (inGroup) { inGroup = false; lives = 0 }
                    lives += 1
                }
                Kind.PROBE -> if (inGroup) inGroup = false
            }
        }
        return gaps
    }

    private fun observation(field: AutoCalProtocol.Field, atMs: Double): AutoCalReadObservation {
        val reply = replyFor(field)
        return AutoCalReadObservation(field = field, status = reply.status, payload = reply.payload, capturedAtMs = atMs.toLong())
    }

    // ---- políticas ------------------------------------------------------------------------------

    private val baselineAcquisitionFields: List<AutoCalProtocol.Field> = listOf(
        AutoCalProtocol.PETR_INJ_TBUF, AutoCalProtocol.MNFLD_PRESS_BUF, AutoCalProtocol.NUM_BUF_UPD_PETR,
        AutoCalProtocol.PETR_INJ_TBUF_GAS_PREV, AutoCalProtocol.MNFLD_PRESS_BUF_GAS_PREV,
        AutoCalProtocol.PETR_INJ_TBUF_GAS, AutoCalProtocol.MNFLD_PRESS_BUF_GAS, AutoCalProtocol.NUM_BUF_UPD_GAS,
        AutoCalProtocol.ACQUIRED_ZONES_PETROL, AutoCalProtocol.ACQUIRED_ZONES_GAS,
    )
    private val baselineReferenceFields: List<AutoCalProtocol.Field> = listOf(
        AutoCalProtocol.PETR_INJ_TBP, AutoCalProtocol.MNFLD_PRESS_THD, AutoCalProtocol.MUL_ACT,
        AutoCalProtocol.PETR_MNFLD_PRESS_RV, AutoCalProtocol.GAS_MNFLD_PRESS_RV,
    )

    private class Baseline(val events: List<Ev>, val acquisition: AutoCalSnapshot, val reference: AutoCalSnapshot)

    /** Política antiga: tick de 1 s (probe + unidade de aquisição), referência a cada 4º tick; tudo preso à porta. */
    private fun runBaseline(seconds: Int): Baseline {
        val bus = SimBus(1_000.0)
        var acquisition: AutoCalSnapshot? = null
        var reference: AutoCalSnapshot? = null
        var t = 1_000.0
        var tick = 0
        while (t < 1_000.0 + seconds * 1_000.0) {
            bus.liveUntil(t)
            bus.transaction(Kind.PROBE, AutoCalProtocol.CMD_NATIVE_STATUS, replies.getValue(probeText).responseLen)
            bus.liveFrame()
            val observations = ArrayList<AutoCalReadObservation>()
            baselineAcquisitionFields.forEach { field ->
                bus.transaction(Kind.ACQ, AutoCalProtocol.read(field), replyFor(field).responseLen)
                observations += observation(field, bus.now)
            }
            bus.transaction(Kind.ACQ, AutoCalProtocol.CMD_NATIVE_STATUS, replies.getValue(probeText).responseLen)
            bus.liveFrame()
            if (acquisition == null) {
                acquisition = AutoCalSnapshotBuilder.build(observations, baselineAcquisitionFields, "baseline-acq", startedAtMs = 0L, finishedAtMs = bus.now.toLong())
            }
            if (tick % 4 == 0) {
                val refObservations = ArrayList<AutoCalReadObservation>()
                baselineReferenceFields.forEach { field ->
                    bus.transaction(Kind.ACQ, AutoCalProtocol.read(field), replyFor(field).responseLen)
                    refObservations += observation(field, bus.now)
                }
                bus.transaction(Kind.ACQ, AutoCalProtocol.CMD_NATIVE_STATUS, replies.getValue(probeText).responseLen)
                bus.liveFrame()
                if (reference == null) {
                    reference = AutoCalSnapshotBuilder.build(refObservations, baselineReferenceFields, "baseline-ref", startedAtMs = 0L, finishedAtMs = bus.now.toLong())
                }
            }
            tick += 1
            t = maxOf(t + 1_000.0, bus.now)
        }
        return Baseline(bus.events, acquisition!!, reference!!)
    }

    private class Sliced(
        val events: List<Ev>,
        val committed: List<NativeAutoCalRefreshPlanner.Group>,
        val committedSnapshots: List<Pair<NativeAutoCalRefreshPlanner.Group, AutoCalSnapshot>>,
        val planner: NativeAutoCalRefreshPlanner,
        val failedAtMs: List<Double>,
        val groupStartsMs: List<Pair<NativeAutoCalRefreshPlanner.Group, Double>>,
        /** (início da leitura, fim do probe de confirmação) de cada grupo aceito. */
        val commitWindowsMs: List<Pair<Double, Double>>,
    )

    private class Pending(
        val group: NativeAutoCalRefreshPlanner.Group,
        val snapshot: AutoCalSnapshot?,
        val before: AutoCalProtocol.NativeStatus,
        val readOk: Boolean,
        val startedAtMs: Double,
    )

    /**
     * Política fatiada com o código de produção. [epochChangeAtMs]: a partir daí a ECU responde outro contador
     * AutoMatch ao probe. [failGroupFrom]: a partir daí o grupo G3 não é confirmado pela ECU (leitura falha).
     */
    private fun runSliced(
        seconds: Int,
        epochChangeAtMs: Double = Double.MAX_VALUE,
        failGroupFrom: Double = Double.MAX_VALUE,
        failGroup: NativeAutoCalRefreshPlanner.Group = NativeAutoCalRefreshPlanner.Group.G3_GAS_PREV,
    ): Sliced {
        val bus = SimBus(1_000.0)
        val planner = NativeAutoCalRefreshPlanner()
        planner.markFullSnapshot(1_000L)
        val arbiter = SlotArbiter(clock = { bus.now.toLong() }, liveFrames = { bus.liveCount })
        val duty = AcquisitionDuty { bus.now.toLong() }
        val stepper = SliceStepper(planner, arbiter, duty)
        fun currentProbe(): AutoCalProtocol.NativeStatus {
            val reply = probeReply(if (bus.now >= epochChangeAtMs) 1 else 0)
            return AutoCalProtocol.decodeNativeStatus(reply.status, reply.payload)
        }
        var lastProbe = currentProbe()
        var lastProbeAt = 1_000L
        var pending: Pending? = null
        val committed = ArrayList<NativeAutoCalRefreshPlanner.Group>()
        val committedSnapshots = ArrayList<Pair<NativeAutoCalRefreshPlanner.Group, AutoCalSnapshot>>()
        val failedAt = ArrayList<Double>()
        val groupStarts = ArrayList<Pair<NativeAutoCalRefreshPlanner.Group, Double>>()
        val commitWindows = ArrayList<Pair<Double, Double>>()
        var t = 1_000.0
        val end = 1_000.0 + seconds * 1_000.0
        while (t < end) {
            bus.liveUntil(t)
            val nowMs = bus.now.toLong()
            val decision = stepper.decide(
                nowMs = nowMs,
                hasPending = pending != null,
                snapshotWanted = false,
                acquisitionEnabled = true,
                probeAgeMs = nowMs - lastProbeAt,
            )
            when (decision.step) {
                SliceStepper.Step.CONFIRM_PROBE -> {
                    val waiting = pending!!
                    bus.transaction(Kind.PROBE, AutoCalProtocol.CMD_NATIVE_STATUS, replies.getValue(probeText).responseLen)
                    bus.liveFrame()
                    val after = currentProbe()
                    lastProbe = after
                    lastProbeAt = bus.now.toLong()
                    pending = null
                    if (waiting.readOk && NativeAutoCalEpochGuard.sameEpoch(waiting.before, after)) {
                        committed += waiting.group
                        committedSnapshots += waiting.group to waiting.snapshot!!
                        commitWindows += waiting.startedAtMs to bus.now
                        planner.groupDone(waiting.group)
                    } else {
                        failedAt += bus.now
                        planner.groupFailed(waiting.group, bus.now.toLong())
                    }
                }
                SliceStepper.Step.RUN_GROUP -> {
                    val group = decision.group!!
                    val startedAt = bus.now
                    groupStarts += group to startedAt
                    val observations = ArrayList<AutoCalReadObservation>()
                    var ok = true
                    for (field in group.fields) {
                        bus.transaction(Kind.ACQ, AutoCalProtocol.read(field), replyFor(field).responseLen)
                        if (group == failGroup && bus.now >= failGroupFrom) { ok = false; break }
                        observations += observation(field, bus.now)
                    }
                    arbiter.end()
                    bus.liveFrame() // telemetryAfter = true do scheduler
                    if (ok) {
                        val snapshot = AutoCalSnapshotBuilder.build(
                            observations, group.fields, "slice-${group.label}", startedAtMs = 0L, finishedAtMs = bus.now.toLong(),
                        )
                        assertFalse("grupo ${group.label} parcial", snapshot.partial)
                        pending = Pending(group, snapshot, lastProbe, true, startedAt)
                    } else {
                        failedAt += bus.now
                        planner.groupFailed(group, bus.now.toLong())
                    }
                }
                SliceStepper.Step.PROBE -> {
                    bus.transaction(Kind.PROBE, AutoCalProtocol.CMD_NATIVE_STATUS, replies.getValue(probeText).responseLen)
                    bus.liveFrame()
                    lastProbe = currentProbe()
                    lastProbeAt = bus.now.toLong()
                }
                SliceStepper.Step.IDLE -> Unit
            }
            t = bus.now + 100.0 // scheduleWithFixedDelay(100 ms)
        }
        return Sliced(bus.events, committed, committedSnapshots, planner, failedAt, groupStarts, commitWindows)
    }

    // ---- provas ---------------------------------------------------------------------------------

    private fun readCommands(events: List<Ev>): List<String> =
        events.filter { it.kind == Kind.ACQ && it.cmd != probeText }.map { it.cmd }

    private fun cmds(fields: List<AutoCalProtocol.Field>): List<String> = fields.map { AutoCalProtocol.read(it).hex() }

    @Test
    fun `a bytes per round equal the baseline - same reads, only grouping and order change`() {
        val sliced = runSliced(seconds = 30)
        val acquisitionSet = cmds(baselineAcquisitionFields).sorted()
        val withReferenceSet = (cmds(baselineAcquisitionFields) + cmds(baselineReferenceFields)).sorted()

        // Nenhum comando novo: o conjunto de comandos distintos é o do baseline (+ o probe 48 0B que ele já usava).
        val distinct = sliced.events.filter { it.kind != Kind.LIVE }.map { it.cmd }.toSet()
        assertEquals((withReferenceSet + probeText).toSet(), distinct)

        // Por rodada (de um G2 ao próximo G2): multiconjunto de leituras = baseline (aquisição, com ou sem referência).
        val reads = readCommands(sliced.events)
        val g2First = AutoCalProtocol.read(AutoCalProtocol.PETR_INJ_TBUF).hex()
        val starts = reads.indices.filter { reads[it] == g2First }
        assertTrue("várias rodadas completas", starts.size >= 6)
        var withReference = 0
        var acquisitionOnly = 0
        for (i in 0 until starts.size - 1) {
            val round = reads.subList(starts[i], starts[i + 1]).sorted()
            if (round == acquisitionSet) acquisitionOnly += 1
            else if (round == withReferenceSet) withReference += 1
            else error("rodada $i difere do baseline: $round")
        }
        assertTrue("há rodadas só de aquisição", acquisitionOnly >= 2)
        assertTrue("há rodadas com referência (a cada 2ª)", withReference >= 2)
    }

    @Test
    fun `b max contiguous non-live bus time stays within 160 ms and c at least 3 live frames separate groups`() {
        val baseline = runBaseline(seconds = 30)
        val sliced = runSliced(seconds = 30)
        val before = maxContiguousNonLiveMs(baseline.events)
        val after = maxContiguousNonLiveMs(sliced.events)
        // Cada grupo é uma unidade curta; o probe de época é uma transação própria entre quadros vivos.
        assertTrue("sem grupo > 160 ms: $after (baseline $before)", after <= 160.0)
        assertTrue("baseline segura a porta por ~0,5 s: $before", before > 400.0)
        val gaps = liveFramesBetweenGroups(sliced.events)
        assertTrue("grupos suficientes", gaps.size >= 20)
        assertTrue("mínimo de quadros vivos entre grupos: ${gaps.minOrNull()}", gaps.all { it >= 3 })
        println(
            "SLICING_METRICS maxNonLiveMs before=%.0f after=%.0f busShare before=%.3f after=%.3f minLiveBetweenGroups=%d".format(
                before, after, busShare(baseline.events), busShare(sliced.events), gaps.minOrNull() ?: -1,
            ),
        )
        assertTrue("fatiar não pode aumentar a fatia de barramento", busShare(sliced.events) <= busShare(baseline.events))
    }

    @Test
    fun `d snapshot built from sliced groups equals the baseline snapshot field by field`() {
        val baseline = runBaseline(seconds = 8)
        val sliced = runSliced(seconds = 12)
        fun union(family: NativeAutoCalRefreshPlanner.Family): Map<Int, AutoCalFieldValue> {
            val merged = LinkedHashMap<Int, AutoCalFieldValue>()
            sliced.committedSnapshots.filter { it.first.family == family }.forEach { (_, snapshot) -> merged.putAll(snapshot.fields) }
            return merged
        }
        fun assertSame(expected: AutoCalSnapshot, actual: Map<Int, AutoCalFieldValue>) {
            assertEquals(expected.fields.keys, actual.keys)
            expected.fields.forEach { (key, want) ->
                val got = actual.getValue(key)
                assertEquals(want.key, got.key)
                assertEquals(want.status, got.status)
                assertEquals(want.rawPayloadHex, got.rawPayloadHex)
                assertTrue(want.rawValues.contentEquals(got.rawValues))
                assertTrue(want.physicalValues.contentEquals(got.physicalValues))
                assertEquals(want.elementCount, got.elementCount)
            }
        }
        assertFalse(baseline.acquisition.partial)
        assertFalse(baseline.reference.partial)
        assertSame(baseline.acquisition, union(NativeAutoCalRefreshPlanner.Family.ACQUISITION))
        assertSame(baseline.reference, union(NativeAutoCalRefreshPlanner.Family.REFERENCE))
    }

    @Test
    fun `epoch guard is per group - a group confirmed under another epoch is discarded and the rest of the family is dropped`() {
        val changeAt = 1_000.0 + 3_000.0
        val sliced = runSliced(seconds = 6, epochChangeAtMs = changeAt)
        // Nenhum grupo cujo "antes" e "depois" difiram entra; o que estava em voo na virada foi descartado.
        assertTrue("houve descarte na virada de época", sliced.failedAtMs.any { it >= changeAt })
        val round = sliced.planner.roundRemaining()
        assertTrue("sem grupos vencidos acumulados de aquisição: $round",
            round.none { it.family == NativeAutoCalRefreshPlanner.Family.ACQUISITION })
        // Todo grupo aceito foi lido E confirmado sob a mesma época: nenhuma janela de aceite atravessa a virada.
        assertTrue("houve grupos aceitos", sliced.commitWindowsMs.isNotEmpty())
        sliced.commitWindowsMs.forEach { (startedAt, confirmedAt) ->
            assertFalse("grupo aceito atravessou a virada de época", startedAt < changeAt && confirmedAt >= changeAt)
        }
    }

    @Test
    fun `failed group is skipped and retried only after the backoff - overdue groups never pile up`() {
        val failFrom = 1_000.0 + 2_500.0
        val sliced = runSliced(seconds = 14, failGroupFrom = failFrom)
        val firstFailure = sliced.failedAtMs.first { it >= failFrom }
        // Depois da falha, o próximo G2 (início da rodada seguinte) só sai depois do recuo (1ª falha: 4 s com base de 2 s).
        val nextG2 = sliced.groupStartsMs.firstOrNull {
            it.first == NativeAutoCalRefreshPlanner.Group.G2_PETROL_BUFFERS && it.second > firstFailure
        }
        assertTrue("recuo exponencial respeitado", nextG2 == null || nextG2.second - firstFailure >= NativeAutoCalRefreshPlanner.ACQUISITION_INTERVAL_MS * 2 - 300.0)
        // E o resto da aquisição daquela rodada foi descartado, não empilhado.
        val tail = sliced.groupStartsMs.filter { it.second > firstFailure && it.second < firstFailure + 1_500.0 }
        assertTrue("sem G4/G6 da rodada que falhou: $tail", tail.none {
            it.first == NativeAutoCalRefreshPlanner.Group.G4_GAS || it.first == NativeAutoCalRefreshPlanner.Group.G6_ZONES
        })
    }
}
