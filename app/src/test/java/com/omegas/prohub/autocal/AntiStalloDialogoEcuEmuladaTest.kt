package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.Mp48BackpressureScheduler
import com.omegas.prohub.ecu.Mp48SerialScheduler
import com.omegas.prohub.ecu.Mp48SerialUnit
import com.omegas.prohub.ecu.Mp48WorkClass
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Teste FUNCIONAL da escrita do anti-apagão: o escritor REAL do app (AntiStalloFlagWriter / AntiStalloParamsWriter, com AutoCalProtocol real)
 * conversa com o diálogo gerado pela ECU EMULADA (tests/fixtures/anti-apagao-dialogo-ecu.json, congelado por tests/test_dialogo_antiapagao_ecu.py).
 * Ordem ESTRITA: um quadro diferente, fora de ordem ou a mais derruba o teste. As respostas voltam com o eco do pedido + ACK, como no fio.
 */
class AntiStalloDialogoEcuEmuladaTest {
    private class Passo(val pedido: String, val resposta: String, val status: Int, val payload: String)

    private fun fixture(): JSONObject {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, "tests/fixtures/anti-apagao-dialogo-ecu.json")
            if (candidate.isFile) return JSONObject(candidate.readText())
            dir = dir.parentFile ?: return@repeat
        }
        error("fixture anti-apagao-dialogo-ecu.json não encontrada")
    }

    private fun passos(nome: String): List<Passo> {
        val arr = fixture().getJSONObject(nome).getJSONArray("passos")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Passo(o.getString("pedido"), o.getString("resposta"), o.getInt("status"), o.getString("payload"))
        }
    }

    private fun hex(s: String): ByteArray = if (s.isBlank()) ByteArray(0) else s.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    /** Central de mentira que só sabe o diálogo congelado, em ordem estrita. */
    private class Replay(private val passos: List<Passo>) : Mp48SerialScheduler {
        var proximo = 0
        val enviados = ArrayList<String>()
        val classes = ArrayList<Mp48WorkClass>()
        /** Divergências do app em relação ao diálogo (quadro diferente, fora de ordem ou a mais). Os testes de caminho feliz exigem vazio. */
        val falhas = java.util.concurrent.CopyOnWriteArrayList<String>()
        private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        override fun isConnected() = true
        override fun currentSessionId() = 7L
        private fun responder(request: ByteArray): UsbProtocolReply {
            val h = hex(request)
            enviados += h
            val esperado = passos.getOrNull(proximo) ?: run { falhas += "quadro a mais: $h (já passaram os ${passos.size} passos)"; throw IllegalStateException(falhas.last()) }
            if (esperado.pedido != h) { falhas += "passo ${proximo + 1}: esperado ${esperado.pedido}, o app mandou $h"; throw IllegalStateException(falhas.last()) }
            proximo++
            val bruta = esperado.resposta.split(" ").map { it.toInt(16).toByte() }.toByteArray()
            val payload = if (esperado.payload.isBlank()) ByteArray(0) else esperado.payload.split(" ").map { it.toInt(16).toByte() }.toByteArray()
            return UsbProtocolReply(ok = true, status = esperado.status, payload = payload, request = request, echo = request, rawResponse = bruta)
        }
        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean) = responder(request)
        override fun <T> unit(reason: String, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean, waitTimeoutMs: Long, block: (Mp48SerialUnit) -> T): T {
            classes += workClass
            return block(object : Mp48SerialUnit {
                override val sessionId = 7L
                override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean) = responder(request)
            })
        }
    }

    private fun esperar(leitor: () -> String, vararg estados: String): JSONObject {
        val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < limite) {
            val s = JSONObject(leitor())
            if (s.getString("state") in estados) return s
            Thread.sleep(1)
        }
        throw AssertionError("estado $estados não chegou: ${leitor()}")
    }

    private fun flag(serial: Mp48SerialScheduler) = AntiStalloFlagWriter(serial, { _, _ -> }, { _, _, _ -> })
    private fun params(serial: Mp48SerialScheduler) = AntiStalloParamsWriter(serial, { _, _ -> }, { _, _, _ -> })

    @Test fun `interruptor LIGAR - quadros identicos aos da central emulada, em ordem, e CONFIRMED`() {
        val serial = Replay(passos("flag_ligar")); val w = flag(serial)
        assertTrue(w.apply(true).getBoolean("ok"))
        val done = esperar({ w.statusJson() }, "DONE")
        assertEquals(done.toString() + serial.falhas, "CONFIRMED", done.getString("outcome"))
        assertEquals(4, serial.proximo) // todos os passos do diálogo foram consumidos, nenhum a mais
        assertEquals("35 FA 00 30 00 34 12 A5", done.getString("frameHex"))
        assertEquals(listOf(Mp48WorkClass.MANUAL_WRITE), serial.classes)
    }

    @Test fun `interruptor DESLIGAR - so o bit 0x20 muda, o resto da palavra e preservado`() {
        val serial = Replay(passos("flag_desligar")); val w = flag(serial)
        w.apply(false)
        val done = esperar({ w.statusJson() }, "DONE")
        assertEquals(done.toString() + serial.falhas, "CONFIRMED", done.getString("outcome"))
        assertEquals(4, serial.proximo)
    }

    @Test fun `rotacao maxima 1800 - quadro 37 33 0C 01 igual ao da central emulada e CONFIRMED`() {
        val serial = Replay(passos("valores_rotacao_maxima")); val w = params(serial)
        assertTrue(w.apply("""{"0":1800}""").getBoolean("ok"))
        val done = esperar({ w.statusJson() }, "DONE")
        assertEquals(done.toString() + serial.falhas, "CONFIRMED", done.getString("outcome"))
        assertEquals(4, serial.proximo)
        assertTrue(done.getString("frameHex").startsWith("37 33 0C 01 08 07 64 00"))
        assertEquals(listOf(Mp48WorkClass.MANUAL_WRITE), serial.classes)
    }

    @Test fun `restaurar fabrica - os 5 numeros voltam num unico quadro`() {
        val serial = Replay(passos("valores_restaurar_fabrica")); val w = params(serial)
        w.apply(AntiStalloParamsCore.factoryChangesJson())
        val done = esperar({ w.statusJson() }, "DONE")
        assertEquals(done.toString() + serial.falhas, "CONFIRMED", done.getString("outcome"))
        assertEquals(4, serial.proximo)
    }

    @Test fun `o app manda quadro diferente do da central emulada - o teste derruba (prova que o teste morde)`() {
        val serial = Replay(passos("valores_rotacao_maxima")); val w = params(serial)
        w.apply("""{"0":1700}""") // outro valor: o quadro de escrita não é o do diálogo
        val done = esperar({ w.statusJson() }, "DONE")
        assertTrue(done.toString(), done.getString("outcome") != "CONFIRMED") // quadro errado nunca vira sucesso (aqui: leitura de volta falha = estado incerto)
        assertTrue(serial.proximo < 4)
        assertTrue(serial.falhas.first().contains("esperado 37 33 0C 01 08 07"))
    }

    /** Sistema serial de um só trabalho por vez (como o motor real), com telemetria READ_ONLY martelando em paralelo. */
    private class MotorDeVerdade(private val passos: List<Passo>) : Mp48SerialScheduler {
        private val trava = Any()
        val replay = Replay(passos)
        val telemetrias = AtomicInteger(0)
        override fun isConnected() = true
        override fun currentSessionId() = 7L
        override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean) =
            synchronized(trava) { replay.transaction(request, reason, timeoutMs, purgeBefore, expectedSessionId, workClass, telemetryAfter) }
        override fun <T> unit(reason: String, expectedSessionId: Long, workClass: Mp48WorkClass, telemetryAfter: Boolean, waitTimeoutMs: Long, block: (Mp48SerialUnit) -> T): T =
            if (reason.startsWith("telemetria")) { // leitura secundária em andamento: ocupa a vaga da faixa, mas não a porta
                telemetrias.incrementAndGet()
                block(object : Mp48SerialUnit { override val sessionId = 7L; override fun transaction(request: ByteArray, reason: String, timeoutMs: Int, purgeBefore: Boolean) = UsbProtocolReply(ok = true, status = 0x53) })
            } else synchronized(trava) { replay.unit(reason, expectedSessionId, workClass, telemetryAfter, waitTimeoutMs, block) }
    }

    @Test fun `faixa de leitura SATURADA e telemetria em paralelo - a gravacao manual ainda passa (nao depende da faixa READ_ONLY)`() {
        val motor = MotorDeVerdade(passos("valores_rotacao_maxima"))
        val admissao = Mp48BackpressureScheduler(motor, readOnlyCapacity = 1, criticalCapacity = 2)
        val parar = CountDownLatch(1)
        val ocupado = CountDownLatch(1)
        // Uma leitura secundária lenta segura a única vaga da faixa READ_ONLY: qualquer outra leitura é recusada na hora.
        val segurando = Thread {
            admissao.unit("telemetria (lenta)", 7L, Mp48WorkClass.READ_ONLY, false, 100L) { ocupado.countDown(); parar.await(10, TimeUnit.SECONDS) }
        }.apply { isDaemon = true; start() }
        assertTrue(ocupado.await(5, TimeUnit.SECONDS))
        val w = params(admissao)
        assertTrue(w.apply("""{"0":1800}""").getBoolean("ok"))
        val done = esperar({ w.statusJson() }, "DONE")
        parar.countDown(); segurando.join(2000)
        assertEquals(done.toString() + motor.replay.falhas, "CONFIRMED", done.getString("outcome"))
        assertEquals(4, motor.replay.proximo)
    }

    @Test fun `o caminho antigo (prepare le pela faixa READ_ONLY) NAO consegue com a faixa saturada - e por isso a chamada unica`() {
        val motor = MotorDeVerdade(passos("valores_rotacao_maxima"))
        val admissao = Mp48BackpressureScheduler(motor, readOnlyCapacity = 1, criticalCapacity = 2)
        val parar = CountDownLatch(1); val ocupado = CountDownLatch(1)
        val segurando = Thread { admissao.unit("telemetria (lenta)", 7L, Mp48WorkClass.READ_ONLY, false, 100L) { ocupado.countDown(); parar.await(10, TimeUnit.SECONDS) } }.apply { isDaemon = true; start() }
        assertTrue(ocupado.await(5, TimeUnit.SECONDS))
        val w = params(admissao)
        w.prepare("""{"0":1800}""")
        val s = esperar({ w.statusJson() }, "READY", "ERROR")
        parar.countDown(); segurando.join(2000)
        assertEquals("ERROR", s.getString("state")) // a prévia lê pela faixa saturada e falha depois de esgotar a espera
    }
}
