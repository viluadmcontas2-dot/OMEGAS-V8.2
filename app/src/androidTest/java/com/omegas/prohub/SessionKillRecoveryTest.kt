package com.omegas.prohub

import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omegas.prohub.diagnostics.DocumentsSessionMirror
import com.omegas.prohub.diagnostics.SessionPartPlanner
import com.omegas.prohub.diagnostics.SessionRecorder
import com.omegas.prohub.diagnostics.SessionResumo
import com.omegas.prohub.settings.AppSettings
import com.omegas.prohub.storage.AppPaths
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Prova de queda do app no emulador (classe 4, sem ECU: só o caminho de gravação e publicação).
 *
 * Regra do produto: UMA sessão = UM ZIP, publicado ao fechar ou na recuperação. Nada no Drive
 * enquanto grava.
 * Fase 1 grava uma sessão de verdade com o SessionRecorder real, confirma que NADA foi publicado
 * em Download/Omegas e MORRE (SIGKILL, como um force-stop).
 * Fase 2 roda num processo novo, recupera a sessão órfã e prova: exatamente um ZIP, com a sessão
 * inteira, nenhum evento perdido ou duplicado, e o RESUMO.md dentro dele conta fases, apagões,
 * gravação e veredito.
 * O script de CI roda as duas fases em chamadas separadas de `am instrument`.
 */
@RunWith(AndroidJUnit4::class)
class SessionKillRecoveryTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val evidenceDir get() = File(ctx.getExternalFilesDir(null), "omegas-evidence").also { it.mkdirs() }
    private val expectedFile get() = File(evidenceDir, "kill-proof-expected.json")

    private fun telemetry(recorder: SessionRecorder, count: Int, rpm: Int) {
        repeat(count) { recorder.record("telemetry", "mp48", JSONObject().put("rpm", rpm + it).put("fuel", "GNV").put("petrol_ms", 2.4), force = true) }
    }

    @Test
    fun faseUmGravaEMorre() {
        val paths = AppPaths(ctx)
        paths.sessionLogsRoot.listFiles()?.forEach { it.deleteRecursively() } // emulador de CI limpo
        val recorder = SessionRecorder(paths, AppSettings(ctx), DocumentsSessionMirror(ctx))
        assertTrue(recorder.start("prova de queda do app").optBoolean("ok"))
        val sessionId = JSONObject(recorder.statusJson()).getString("sessionId")

        telemetry(recorder, 300, 1_500)
        recorder.record("refinement_phase", "autocal", JSONObject().put("phase", "ECU_TRABALHANDO").put("headline", "A ECU está no automático"), force = true)
        recorder.record("refinement_phase", "autocal", JSONObject().put("phase", "COLETANDO_NOSSOS").put("headline", "Dirija para medir"), force = true)
        recorder.record("engine_stall", "autocal", JSONObject().put("kind", "APAGOU").put("at", 1_000L).put("petrolMs", 2.2).put("mapBar", 0.31)
            .put("rpmBefore", 1_200.0).put("rpmMin", 0.0).put("speedKmh", 24.0), force = true)
        recorder.record("engine_stall_after", "autocal", JSONObject().put("at", 1_000L).put("depois", "RELIGOU").put("religou", true).put("religouEmS", 1.8), force = true)
        recorder.record("k_factor_batch_confirmed", "k_factor", JSONObject().put("adjustmentId", "KF-PROVA").put("points", JSONArray(List(30) { it })), force = true)
        telemetry(recorder, 200, 1_700)
        recorder.record("refinement_phase", "autocal", JSONObject().put("phase", "VERIFICANDO").put("headline", "Verificando a gravação"), force = true)
        recorder.record("engine_stall", "autocal", JSONObject().put("kind", "QUASE_APAGOU").put("at", 50_000L).put("petrolMs", 3.1).put("mapBar", 0.40)
            .put("rpmBefore", 1_900.0).put("rpmMin", 480.0), force = true)
        recorder.record("refinement_verdict", "autocal", JSONObject().put("id", "exp-prova").put("status", "VERIFICADO").put("appliedAt", 2_000L)
            .put("ratioBefore", 1.06).put("ratioAfter", 1.01).put("bands", JSONArray()), force = true)
        recorder.record("manual_marker", "test", JSONObject().put("note", "lote 2 no disco"), force = true) // crítico: descarrega e grava no flash

        val events = File(paths.sessionLogsRoot, sessionId).resolve("events_0001.jsonl")
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < deadline && !(events.isFile && events.readText().contains("lote 2 no disco"))) SystemClock.sleep(100L)
        assertTrue("lote 2 chegou ao disco antes da queda", events.readText().contains("lote 2 no disco"))

        assertEquals("nada vai para o Drive enquanto a sessão grava", 0, readParts(sessionId).size)
        expectedFile.writeText(JSONObject().put("sessionId", sessionId).put("killedAtMs", System.currentTimeMillis()).toString(2))
        // SIGKILL: sem fechar a sessão, sem hook de desligamento. É o que um force-stop ou a energia faz.
        android.os.Process.killProcess(android.os.Process.myPid())
        SystemClock.sleep(30_000L)
    }

    private data class Part(val name: String, val entries: Map<String, ByteArray>)

    private fun readParts(sessionId: String): List<Part> {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = "Download/Omegas/${DocumentsSessionMirror.safeName(sessionId)}/"
        val found = ArrayList<Pair<String, android.net.Uri>>()
        resolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            MediaStore.MediaColumns.RELATIVE_PATH + "=?", arrayOf(relative), MediaStore.MediaColumns.DISPLAY_NAME + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (name.endsWith(".zip")) found += name to android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }
        return found.map { (name, uri) ->
            val entries = LinkedHashMap<String, ByteArray>()
            ZipInputStream(resolver.openInputStream(uri)!!.buffered()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
            }
            Part(name, entries)
        }
    }

    @Test
    fun faseDoisRecupera() {
        val expected = JSONObject(expectedFile.readText())
        val sessionId = expected.getString("sessionId")
        val paths = AppPaths(ctx)
        val dir = File(paths.sessionLogsRoot, sessionId)
        assertTrue("a sessão sobreviveu à queda do processo", dir.isDirectory)
        val before = File(dir, SessionResumo.FILE_NAME).readText()
        assertTrue("o resumo ao vivo já tinha as fases antes da queda", before.contains("coletando os pontos do OMEGAS"))
        assertFalse("a sessão foi morta, não fechada", before.contains(SessionResumo.CLOSED_MARK))

        // Processo novo: como o app reabrindo depois da queda.
        SessionRecorder(paths, AppSettings(ctx), DocumentsSessionMirror(ctx)).recoverDocumentsMirrorAsync()
        val marker = File(dir, ".documents_mirrored")
        val deadline = SystemClock.elapsedRealtime() + 60_000L
        while (SystemClock.elapsedRealtime() < deadline && !marker.isFile) SystemClock.sleep(200L)
        assertTrue("a recuperação publicou a parte final", marker.isFile)

        val parts = readParts(sessionId)
        val safe = DocumentsSessionMirror.safeName(sessionId)
        assertEquals("exatamente um ZIP para a sessão: ${parts.map { it.name }}", listOf("$safe.zip"), parts.map { it.name })
        val zip = parts.single()
        val info = JSONObject(String(zip.entries.getValue("$safe/parte.json")))
        assertTrue("o ZIP único é final", info.getBoolean("final"))
        assertTrue("o ZIP único é a sessão inteira", info.getBoolean("single"))

        // Sem perda e sem duplicata: o arquivo de eventos dentro do ZIP é o arquivo do disco, byte a byte.
        val onDisk = File(dir, "events_0001.jsonl")
        val complete = SessionPartPlanner.completeLength(onDisk)
        val diskBytes = onDisk.readBytes().copyOf(complete.toInt())
        fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
        val joined = zip.entries.getValue("$safe/events_0001.jsonl")
        assertEquals("ZIP = arquivo da sessão (sem perda e sem duplicata)", sha(diskBytes), sha(joined))
        val sequences = String(joined).lines().filter { it.isNotBlank() }.map { JSONObject(it).getLong("sequence") }
        assertEquals("sequências contíguas", (1L..sequences.size.toLong()).toList(), sequences)
        val text = String(joined)
        assertTrue("lote 1 dentro do ZIP", text.contains("KF-PROVA"))
        assertTrue("lote 2 dentro do ZIP", text.contains("lote 2 no disco"))

        // RESUMO.md na parte final.
        val resumo = String(zip.entries.getValue("$safe/RESUMO.md"))
        for (needle in listOf(
            SessionResumo.OPEN_MARK, "ECU trabalhando no automático", "coletando os pontos do OMEGAS", "verificando a última gravação",
            "Apagou: 1 (religou: 1). Quase apagou: 1.", "depois: o motor religou em 1,8 s", "Curva K: 30 pontos, ajuste KF-PROVA",
            "verificação concluída", "Último registro",
        )) assertTrue("RESUMO.md precisa conter '$needle':\n$resumo", resumo.contains(needle))

        assertFalse("sem faixas confirmadas não há melhora comprovada", resumo.contains("melhorou e foi confirmada"))

        File(evidenceDir, "session-kill-recovery-RESUMO.md").writeText(resumo)
        File(evidenceDir, "session-kill-recovery.json").writeText(
            JSONObject().put("sessionId", sessionId).put("zips", JSONArray(parts.map { it.name }))
                .put("eventsOnDisk", sequences.size).put("sha256", sha(joined)).put("provenance", "EMULATOR_KILL_RECOVERY_NO_ECU")
                .put("killedAtMs", expected.getLong("killedAtMs")).toString(2),
        )
    }
}
