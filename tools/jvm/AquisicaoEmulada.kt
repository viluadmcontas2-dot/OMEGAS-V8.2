package com.omegas.prohub.autocal

import android.os.SystemClock
import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONObject
import kotlin.system.exitProcess

/**
 * ECU falsa + o NativeAutoCalMonitor REAL (Kotlin de produção, só com SystemClock trocado por um relógio de teste).
 * A ECU adquire só o combustível em uso (como a do carro); o monitor só lê. Prova: gasolina e GNV aparecem no app
 * em qualquer ordem, também depois de "Reaprender", sem o app bloquear nenhum dos dois.
 * Rodar: tools/jvm/rodar_aquisicao_emulada.sh
 */
class FakeEcu : Mp48SerialScheduler {
    private val scalars = HashMap<Int, ByteArray>()
    private val vectors = HashMap<Int, ByteArray>()
    private val indexed = HashMap<Pair<Int, Int>, ByteArray>()
    var autoMatchCount = 1
    var step = 0
    val unknown = LinkedHashSet<String>()
    /** Anomalias injetáveis: a ECU de verdade perde respostas, devolve lixo, reconecta e muda de sessão USB. */
    var connected = true
    var sessionId = 7L
    var failNext = 0
    var corruptNext = 0

    init {
        for (f in AutoCalProtocol.READ_ONLY_FIELDS) {
            val width = if (f.encoding == AutoCalProtocol.Encoding.U8_OR_U16_LE) 1 else f.encoding.bytesPerElement
            val bytes = ByteArray(width * (f.expectedElementsHint ?: 1))
            when (f.shape) {
                AutoCalProtocol.Shape.SCALAR -> scalars[f.address] = bytes
                AutoCalProtocol.Shape.VECTOR -> vectors[f.address] = bytes
                AutoCalProtocol.Shape.INDEXED -> indexed[f.address to f.index!!] = bytes
            }
        }
        setScalar(AutoCalProtocol.AUTO_CAL_ENABLE, 1)
        setScalar(AutoCalProtocol.MODULE_VERSION, 5)
        setScalar(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED, autoMatchCount)
        setIndexed(AutoCalProtocol.MAX_AUTOMATCH, 3)
        setU16Vector(AutoCalProtocol.PETR_INJ_TBP, IntArray(30) { 300 + it * 40 })
        setU16Vector(AutoCalProtocol.MUL_ACT, IntArray(30) { 0x4000 })
        val cal = ByteArray(10); cal[5] = 3; cal[8] = 8
        vectors[AutoCalProtocol.CALIBRATION_VAL_1.address] = cal
    }

    private fun setScalar(f: AutoCalProtocol.Field, v: Int) {
        val b = scalars.getValue(f.address)
        b[0] = v.toByte(); if (b.size > 1) b[1] = (v ushr 8).toByte()
    }
    private fun setIndexed(f: AutoCalProtocol.Field, v: Int) { indexed.getValue(f.address to f.index!!)[0] = v.toByte() }
    private fun setU16Vector(f: AutoCalProtocol.Field, values: IntArray) {
        val b = vectors.getValue(f.address)
        values.forEachIndexed { i, v -> b[i * 2] = v.toByte(); b[i * 2 + 1] = (v ushr 8).toByte() }
    }
    fun counters(f: AutoCalProtocol.Field): IntArray {
        val b = vectors.getValue(f.address)
        return IntArray(18) { (b[i2(it)].toInt() and 0xFF) or ((b[i2(it) + 1].toInt() and 0xFF) shl 8) }
    }
    private fun i2(i: Int) = i * 2

    /** A ECU adquire UMA banda do combustível em uso (o outro fica como está). */
    fun acquire(fuel: String) {
        val band = (step++ * 5) % 18
        val field = if (fuel == "PETROL") AutoCalProtocol.NUM_BUF_UPD_PETR else AutoCalProtocol.NUM_BUF_UPD_GAS
        val c = counters(field); c[band] += 1
        setU16Vector(field, c)
        if (fuel == "PETROL") {
            setU16Vector(AutoCalProtocol.PETR_INJ_TBUF, IntArray(18) { 400 + it * 55 })
        } else {
            setU16Vector(AutoCalProtocol.PETR_INJ_TBUF_GAS, IntArray(18) { 410 + it * 55 })
        }
    }

    /** "Reaprender" (modo 4 = Reset all): a ECU zera os contadores dos dois combustíveis. */
    fun resetAll() {
        setU16Vector(AutoCalProtocol.NUM_BUF_UPD_PETR, IntArray(18))
        setU16Vector(AutoCalProtocol.NUM_BUF_UPD_GAS, IntArray(18))
    }

    fun nativeAutoMatchDone() { autoMatchCount += 1; setScalar(AutoCalProtocol.NUM_AUTOMATCH_EXECUTED, autoMatchCount) }

    override fun isConnected() = connected
    override fun currentSessionId() = sessionId

    fun setK(values: IntArray) = setU16Vector(AutoCalProtocol.MUL_ACT, values)
    fun k(): IntArray = vectors.getValue(AutoCalProtocol.MUL_ACT.address).let { b -> IntArray(30) { i -> (b[i * 2].toInt() and 0xFF) or ((b[i * 2 + 1].toInt() and 0xFF) shl 8) } }
    fun resetPetrol() = setU16Vector(AutoCalProtocol.NUM_BUF_UPD_PETR, IntArray(18))
    fun resetGas() = setU16Vector(AutoCalProtocol.NUM_BUF_UPD_GAS, IntArray(18))

    private fun answer(request: ByteArray): UsbProtocolReply {
        if (failNext > 0) { failNext -= 1; return UsbProtocolReply(ok = false, error = "timeout", request = request) }
        val cmd = request[0].toInt() and 0xFF
        val payload: ByteArray? = when {
            request.size == 3 && cmd == 0x48 && (request[1].toInt() and 0xFF) == 0x0B ->
                ByteArray(14).also { it[12] = 1; it[13] = autoMatchCount.toByte() }
            cmd == AutoCalProtocol.READ_SCALAR -> scalars[addr(request)]
            cmd == AutoCalProtocol.READ_VECTOR -> vectors[addr(request)]
            cmd == AutoCalProtocol.READ_INDEXED -> indexed[addr(request) to (request[3].toInt() and 0xFF)]
            else -> null
        }
        if (payload == null) { unknown += request.joinToString(" ") { "%02X".format(it) }; return UsbProtocolReply(ok = false, error = "desconhecido", request = request) }
        val bytes = if (corruptNext > 0 && payload.size > 1) { corruptNext -= 1; payload.copyOf(payload.size - 1) } else payload.copyOf()
        return UsbProtocolReply(ok = true, status = 0x53, payload = bytes, request = request)
    }
    private fun addr(r: ByteArray) = (r[1].toInt() and 0xFF) or ((r[2].toInt() and 0xFF) shl 8)

    override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean) = answer(request)
    override fun <T> unit(reason: String, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean, waitTimeoutMs: Long, block: (Mp48SerialUnit) -> T): T =
        block(object : Mp48SerialUnit {
            override val sessionId: Long get() = this@FakeEcu.sessionId
            override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean) = answer(request)
        })
}

private var failures = 0
private fun check(ok: Boolean, text: String) { println((if (ok) "OK     " else "FALHOU ") + text); if (!ok) failures++ }

fun main(args: Array<String>) {
    val ecu = FakeEcu()
    val monitor = NativeAutoCalMonitor(serial = ecu, calibrationBusy = { false })
    fun epoch() = monitor.statusJson().getJSONObject("liveAcquisitionEpoch")
    fun run(ticks: Int, fuel: String?, every: Int = 1) {
        repeat(ticks) { i ->
            if (fuel != null && i % every == 0) ecu.acquire(fuel)
            SystemClock.now += 1000
            monitor.tick()
        }
    }
    fun truth(f: AutoCalProtocol.Field) = ecu.counters(f).count { it > 0 }

    run(12, null) // abre a sessão, passa a espera de estabilização e lê o primeiro quadro completo
    println("Fase 0 · parado · epoch=${epoch()}")

    println("\n== A) Gasolina primeiro")
    run(30, "PETROL", every = 3)
    var e = epoch()
    check(e.getInt("petrolSamples") == truth(AutoCalProtocol.NUM_BUF_UPD_PETR) && e.getInt("petrolSamples") >= 3, "gasolina: app mostra ${e.getInt("petrolSamples")} bandas = ECU ${truth(AutoCalProtocol.NUM_BUF_UPD_PETR)}")
    check(e.getInt("gasSamples") == 0, "GNV: 0 bandas (a ECU não adquiriu GNV ainda)")

    println("\n== B) Troca para GNV (sem reset)")
    run(40, "GAS", every = 4)
    e = epoch()
    check(e.getInt("gasSamples") == truth(AutoCalProtocol.NUM_BUF_UPD_GAS) && e.getInt("gasSamples") >= 3, "GNV: app mostra ${e.getInt("gasSamples")} bandas = ECU ${truth(AutoCalProtocol.NUM_BUF_UPD_GAS)}")
    check(e.getInt("petrolSamples") == truth(AutoCalProtocol.NUM_BUF_UPD_PETR), "gasolina continua visível (${e.getInt("petrolSamples")} bandas)")
    check(!e.getBoolean("gasPending") && !e.getBoolean("petrolPending"), "nenhum combustível preso em 'pendente'")

    println("\n== C) Reaprender (Reset all) e GNV ANTES da gasolina")
    ecu.resetAll()
    monitor.onManualActionConfirmed(JSONObject().put("sessionId", 7L).put("action", "RESET_ALL").put("readbackValid", true))
    run(3, null)
    e = epoch()
    check(e.getInt("gasSamples") == 0 && e.getInt("petrolSamples") == 0, "logo após o reset: zero nos dois")
    run(40, "GAS", every = 5)
    e = epoch()
    check(e.getInt("gasSamples") == truth(AutoCalProtocol.NUM_BUF_UPD_GAS) && e.getInt("gasSamples") >= 3, "GNV adquire primeiro: app mostra ${e.getInt("gasSamples")} = ECU ${truth(AutoCalProtocol.NUM_BUF_UPD_GAS)}")
    check(!e.getBoolean("gasPending"), "GNV saiu de 'pendente' sem a gasolina ter adquirido nada")
    run(40, "PETROL", every = 6)
    e = epoch()
    check(e.getInt("petrolSamples") == truth(AutoCalProtocol.NUM_BUF_UPD_PETR) && e.getInt("petrolSamples") >= 3, "gasolina depois: ${e.getInt("petrolSamples")} = ECU ${truth(AutoCalProtocol.NUM_BUF_UPD_PETR)}")
    check(e.getInt("gasSamples") == truth(AutoCalProtocol.NUM_BUF_UPD_GAS), "GNV segue visível (${e.getInt("gasSamples")})")

    println("\n== D) Alterna a cada poucos segundos + AutoMatch nativo no meio")
    repeat(6) { round ->
        run(8, if (round % 2 == 0) "GAS" else "PETROL", every = 2)
        if (round == 2) ecu.nativeAutoMatchDone()
    }
    run(6, null)
    e = epoch()
    check(e.getInt("gasSamples") == truth(AutoCalProtocol.NUM_BUF_UPD_GAS), "GNV: app ${e.getInt("gasSamples")} = ECU ${truth(AutoCalProtocol.NUM_BUF_UPD_GAS)} após alternar")
    check(e.getInt("petrolSamples") == truth(AutoCalProtocol.NUM_BUF_UPD_PETR), "gasolina: app ${e.getInt("petrolSamples")} = ECU ${truth(AutoCalProtocol.NUM_BUF_UPD_PETR)} após alternar")
    check(!e.getBoolean("gasPending") && !e.getBoolean("petrolPending"), "ninguém preso em 'pendente' (reason=${e.getString("reason")})")

    println("\n== E) Reset dos dois com o carro JÁ voltando a adquirir GNV (contadores nem chegam a zero)")
    ecu.resetAll()
    ecu.acquire("GAS"); ecu.acquire("GAS")
    monitor.onManualActionConfirmed(JSONObject().put("sessionId", 7L).put("action", "RESET_ALL").put("readbackValid", true))
    run(30, "GAS", every = 5)
    e = epoch()
    check(!e.getBoolean("gasPending"), "GNV não fica 'pendente' para sempre (reason=${e.getString("reason")})")
    check(e.getInt("gasSamples") == truth(AutoCalProtocol.NUM_BUF_UPD_GAS), "GNV: app ${e.getInt("gasSamples")} = ECU ${truth(AutoCalProtocol.NUM_BUF_UPD_GAS)}")

    if (ecu.unknown.isNotEmpty()) println("\nrequisições que a ECU falsa não conhece: ${ecu.unknown}")
    println("\n== F) Cabo de guerra: a ECU reescreve a Curva K a cada AutoMatch, indo e voltando")
    val tug = FakeEcu(); val mt = NativeAutoCalMonitor(serial = tug, calibrationBusy = { false })
    fun settle(m: NativeAutoCalMonitor, ticks: Int) = repeat(ticks) { SystemClock.now += 1000; m.tick() }
    settle(mt, 14)
    val ups = listOf(1.12, 0.93, 1.09, 0.95)
    for ((i, f) in ups.withIndex()) {
        tug.setK(IntArray(30) { (16384 * f).toInt() })
        tug.nativeAutoMatchDone()
        settle(mt, 8)
    }
    val km = mt.statusJson().getJSONObject("kMovement")
    println("   kMovement=$km")
    check(km.getInt("events") >= 3, "medidor viu ${km.getInt("events")} AutoMatch com mudança da curva")
    check(km.getBoolean("oscillating"), "vai-e-volta detectado (inversões em ${km.getInt("reversalPoints")} de ${km.getInt("pointsWithTwoMoves")} pontos)")
    check(km.getBoolean("measurementOnly"), "medidor só mede (nada gravado)")

    println("\n== G) Convergindo: a ECU mexe sempre para o mesmo lado")
    val conv = FakeEcu(); val mc = NativeAutoCalMonitor(serial = conv, calibrationBusy = { false })
    settle(mc, 14)
    for (f in listOf(1.04, 1.07, 1.09, 1.10)) { conv.setK(IntArray(30) { (16384 * f).toInt() }); conv.nativeAutoMatchDone(); settle(mc, 8) }
    val kc = mc.statusJson().getJSONObject("kMovement")
    check(kc.getInt("events") >= 3 && !kc.getBoolean("oscillating"), "convergindo não é cabo de guerra (eventos=${kc.getInt("events")}, inversões=${kc.getInt("reversalPoints")})")

    if (args.firstOrNull() == "fuzz") fuzz(args.getOrNull(1)?.toInt() ?: 1000)
    exitProcess(if (failures == 0) 0 else 1)
}

/**
 * Milhares de cenários aleatórios (semente fixa) com anomalias: respostas perdidas, payload truncado, desconexão, troca de
 * sessão USB, reset (todos/gasolina/GNV) no meio da aquisição, AutoMatch nativo reescrevendo a curva, troca de combustível.
 * Invariantes depois de um período quieto: o app mostra o que a ECU tem, nenhum combustível preso em "pendente",
 * nenhuma exceção escapa do monitor, o medidor nunca grava.
 */
fun fuzz(scenarios: Int) {
    println("\n== FUZZ: $scenarios cenários com anomalias")
    var exceptions = 0; var badTruth = 0; var stuck = 0
    val firstBad = ArrayList<String>()
    for (seed in 1..scenarios) {
        val rnd = java.util.Random(seed.toLong() * 7919)
        val ecu = FakeEcu(); val mon = NativeAutoCalMonitor(serial = ecu, calibrationBusy = { false })
        var fuel = if (rnd.nextBoolean()) "PETROL" else "GAS"
        var thrown: Throwable? = null
        fun step(acquire: Boolean) {
            if (acquire) ecu.acquire(fuel)
            SystemClock.now += 1000
            try { mon.tick() } catch (t: Throwable) { thrown = thrown ?: t }
        }
        repeat(12) { step(false) }
        val steps = 60 + rnd.nextInt(140)
        repeat(steps) {
            val r = rnd.nextDouble()
            when {
                r < 0.05 -> fuel = if (fuel == "GAS") "PETROL" else "GAS"
                r < 0.065 -> { ecu.resetAll(); mon.onManualActionConfirmed(JSONObject().put("sessionId", ecu.sessionId).put("action", "RESET_ALL").put("readbackValid", true)) }
                r < 0.075 -> { ecu.resetGas(); mon.onManualActionConfirmed(JSONObject().put("sessionId", ecu.sessionId).put("action", "RESET_GAS").put("readbackValid", true)) }
                r < 0.085 -> { ecu.resetPetrol(); mon.onManualActionConfirmed(JSONObject().put("sessionId", ecu.sessionId).put("action", "RESET_PETROL").put("readbackValid", true)) }
                r < 0.10 -> { val f = 0.85 + rnd.nextDouble() * 0.4; ecu.setK(IntArray(30) { (16384 * f * (0.97 + rnd.nextDouble() * 0.06)).toInt() }); ecu.nativeAutoMatchDone() }
                r < 0.13 -> ecu.failNext = 1 + rnd.nextInt(6)
                r < 0.15 -> ecu.corruptNext = 1 + rnd.nextInt(4)
                r < 0.16 -> { ecu.connected = false; repeat(1 + rnd.nextInt(4)) { step(false) }; ecu.connected = true }
                r < 0.17 -> ecu.sessionId += 1
            }
            step(rnd.nextDouble() < 0.7)
        }
        ecu.failNext = 0; ecu.corruptNext = 0; ecu.connected = true
        // Período quieto: a ECU continua adquirindo o combustível em uso e o app tem tempo de ler tudo.
        repeat(40) { step(it % 3 == 0) }
        val e = mon.statusJson().getJSONObject("liveAcquisitionEpoch")
        val petrolTruth = ecu.counters(AutoCalProtocol.NUM_BUF_UPD_PETR).count { it > 0 }
        val gasTruth = ecu.counters(AutoCalProtocol.NUM_BUF_UPD_GAS).count { it > 0 }
        val why = ArrayList<String>()
        if (thrown != null) { exceptions++; why += "exceção ${thrown!!::class.simpleName}: ${thrown!!.message}" }
        if (e.getInt("petrolSamples") != petrolTruth || e.getInt("gasSamples") != gasTruth) { badTruth++; why += "app gasolina=${e.getInt("petrolSamples")} GNV=${e.getInt("gasSamples")} × ECU ${petrolTruth}/${gasTruth}" }
        if (e.getBoolean("petrolPending") || e.getBoolean("gasPending")) { stuck++; why += "preso em pendente (${e.getString("reason")})" }
        if (why.isNotEmpty() && firstBad.size < 8) firstBad += "semente $seed: ${why.joinToString("; ")}"
    }
    check(exceptions == 0, "nenhuma exceção escapou do monitor em $scenarios cenários (violações: $exceptions)")
    check(badTruth == 0, "app = ECU depois do período quieto em $scenarios cenários (violações: $badTruth)")
    check(stuck == 0, "nenhum combustível preso em 'pendente' (violações: $stuck)")
    firstBad.forEach { println("   $it") }
}
