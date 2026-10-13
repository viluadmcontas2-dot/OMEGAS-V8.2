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

    override fun isConnected() = true
    override fun currentSessionId() = 7L

    private fun answer(request: ByteArray): UsbProtocolReply {
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
        return UsbProtocolReply(ok = true, status = 0x53, payload = payload.copyOf(), request = request)
    }
    private fun addr(r: ByteArray) = (r[1].toInt() and 0xFF) or ((r[2].toInt() and 0xFF) shl 8)

    override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean) = answer(request)
    override fun <T> unit(reason: String, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean, waitTimeoutMs: Long, block: (Mp48SerialUnit) -> T): T =
        block(object : Mp48SerialUnit {
            override val sessionId = 7L
            override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean) = answer(request)
        })
}

private var failures = 0
private fun check(ok: Boolean, text: String) { println((if (ok) "OK     " else "FALHOU ") + text); if (!ok) failures++ }

fun main() {
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
    exitProcess(if (failures == 0) 0 else 1)
}
