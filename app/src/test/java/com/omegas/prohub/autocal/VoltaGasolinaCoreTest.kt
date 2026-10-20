package com.omegas.prohub.autocal

import com.omegas.prohub.autocal.VoltaGasolinaCore.Io
import com.omegas.prohub.autocal.VoltaGasolinaCore.Outcome
import com.omegas.prohub.autocal.VoltaGasolinaCore.WriteReply
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Volta automática à gasolina por PRESSÃO BAIXA do gás (ProgBase F4: "Automatic change back to petrol", "Low pressure value
 * for change back"). Oráculo = o ORIGINAL executado: tests/fixtures/volta_gasolina_original_grade.json, gerado por
 * tools/emulacao/volta_gasolina_grade.py rodando NA CPU EMULADA o miolo de 0x4C526C e os setters 0x4327C0 / 0x432670 / 0x436444
 * do ProgBase.exe (sha256 8a2d297c…36f4) com o FPCW 0x1332 do Delphi. Aqui: o app == o original, quadro a quadro.
 */
class VoltaGasolinaCoreTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private val grade: JSONObject by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "tests/fixtures/volta_gasolina_original_grade.json")
            if (f.isFile) return@lazy JSONObject(f.readText())
            dir = dir.parentFile
        }
        error("tests/fixtures/volta_gasolina_original_grade.json não encontrado")
    }

    // ---------- tempo de volta (TEMPO_RITORNO_BENZINA, SC 62, u8) ----------
    @Test fun `tempo de 0 a 20 e 255 sai igual ao original`() {
        val t = grade.getJSONObject("tempo_ritorno_benzina")
        for (key in t.keys()) {
            val v = key.toInt()
            assertEquals("tempo $v", t.getJSONArray(key).getString(0), hex(VoltaGasolinaCore.tempoFrame(v)))
        }
        assertEquals("12 3E 00 FF 4F", hex(VoltaGasolinaCore.tempoFrame(255)))
        assertEquals("12 3E 00 03 53", hex(VoltaGasolinaCore.tempoFrame(3)))
        assertEquals("12 3E 00 00 50", hex(VoltaGasolinaCore.tempoFrame(0)))
    }

    @Test fun `caixa do original - desmarcar grava 255 e remarcar devolve o tempo guardado ou 0`() {
        assertEquals(255, VoltaGasolinaCore.toggleTarget(on = false, saved = 3))
        assertEquals(3, VoltaGasolinaCore.toggleTarget(on = true, saved = 3))
        assertEquals(0, VoltaGasolinaCore.toggleTarget(on = true, saved = 0))
        assertEquals(0, VoltaGasolinaCore.toggleTarget(on = true, saved = 255)) // nunca religa com "desligado"
    }

    // ---------- tabela de 15 pontos (RIF_PRESS_DIFF SC 123 / COEFF_PRESS_DIFF SC 124) ----------
    @Test fun `tabela - os 30 quadros saem iguais ao original em toda a grade`() {
        val tabelas = grade.getJSONArray("tabelas")
        assertTrue(tabelas.length() >= 70)
        for (k in 0 until tabelas.length()) {
            val t = tabelas.getJSONObject(k)
            val mbar = Math.round(t.getDouble("pmp_bar") * 1000).toInt()
            val p0 = t.getInt("p0_bruto")
            val table = VoltaGasolinaCore.table(mbar, p0)
            val esperado = t.getJSONArray("quadros")
            val frames = VoltaGasolinaCore.tableFrames(table.rif, table.coeff)
            assertEquals(30, frames.size)
            for (i in frames.indices) assertEquals("pmp=$mbar p0=$p0 quadro $i", esperado.getString(i), hex(frames[i]))
            val a = t.getJSONArray("a_double_hex"); val b = t.getJSONArray("b_double_hex")
            for (i in 0 until 15) {
                assertEquals("A[$i] pmp=$mbar p0=$p0", a.getString(i), "%016x".format(java.lang.Double.doubleToRawLongBits(table.a[i])))
                assertEquals("B[$i] pmp=$mbar p0=$p0", b.getString(i), "%016x".format(java.lang.Double.doubleToRawLongBits(table.b[i])))
            }
        }
    }

    @Test fun `exemplo do documento - 1,25 bar com P0 1500`() {
        val table = VoltaGasolinaCore.table(1250, 1500)
        val frames = VoltaGasolinaCore.tableFrames(table.rif, table.coeff).map { hex(it) }
        assertEquals("14 7B 00 00 E2 04 75", frames[0])
        assertEquals("14 7C 00 00 41 04 D5", frames[1])
        assertEquals("14 7B 00 0A DC 05 7A", frames[20])
        assertEquals("14 7C 00 0E 22 FC BC", frames[29])
        assertEquals(1274, table.rif[1]) // o original trunca 1274,999…: o app faz igual
        assertEquals(-990, table.coeff[14].toShort().toInt())
    }

    // ---------- escrita do tempo (ler → gravar → reler → comparar) ----------
    private class FakeEcu(var tempo: Int, var rif: IntArray = IntArray(15), var coeff: IntArray = IntArray(15), val nakAt: Int = -1, val keepOld: Boolean = false) {
        val frames = ArrayList<ByteArray>()
        fun io() = Io(
            readTempo = { tempo },
            readRif = { rif.copyOf() },
            readCoeff = { coeff.copyOf() },
            write = { frame ->
                frames += frame
                if (frames.size - 1 == nakAt) WriteReply(false, false, "CA 01 10", "status 0xCA")
                else {
                    if (!keepOld) {
                        val f = IntArray(frame.size) { frame[it].toInt() and 0xFF }
                        when (f[0]) {
                            0x12 -> tempo = f[3]
                            0x14 -> (if (f[1] == 0x7B) rif else coeff)[f[3]] = f[4] or (f[5] shl 8)
                        }
                    }
                    WriteReply(true, false, "53 00 53", "ACK")
                }
            },
        )
    }

    @Test fun `desligar a volta grava 255 e so diz Gravado depois da releitura igual`() {
        val ecu = FakeEcu(0)
        val r = VoltaGasolinaCore.executeTempo(ecu.io(), 255)
        assertEquals(Outcome.CONFIRMED, r.outcome)
        assertEquals(listOf("12 3E 00 FF 4F"), ecu.frames.map { hex(it) })
        assertEquals(0, r.beforeTempo)
        assertEquals(255, r.tempo)
    }

    @Test fun `tempo igual nao envia nada`() {
        val ecu = FakeEcu(2)
        assertEquals(Outcome.NOTHING_TO_DO, VoltaGasolinaCore.executeTempo(ecu.io(), 2).outcome)
        assertTrue(ecu.frames.isEmpty())
    }

    @Test fun `central aceita mas releitura diverge = nao gravado`() {
        val ecu = FakeEcu(0, keepOld = true)
        assertEquals(Outcome.READBACK_DIVERGED, VoltaGasolinaCore.executeTempo(ecu.io(), 3).outcome)
    }

    @Test fun `tempo fora de 0 a 3 e diferente de 255 e recusado sem enviar`() {
        val ecu = FakeEcu(0)
        assertEquals(Outcome.INVALID, VoltaGasolinaCore.executeTempo(ecu.io(), 4).outcome)
        assertTrue(ecu.frames.isEmpty())
    }

    // ---------- escrita da pressão (atrás do interruptor interno) ----------
    /** Central do dono (PortmonLOGNOVO, OBS-FIO; tests/ui/fixtures/ecu-estado-lognovo.json): `29 7B 00` e `29 7C 00`. */
    private fun lognovo(request: String): IntArray {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "tests/ui/fixtures/ecu-estado-lognovo.json")
            if (f.isFile) {
                val bytes = JSONObject(f.readText()).getJSONObject("leituras").getString(request).split(" ").map { it.toInt(16) }
                return IntArray(bytes.size / 2) { (bytes[2 * it] or (bytes[2 * it + 1] shl 8)) }
            }
            dir = dir.parentFile
        }
        error("ecu-estado-lognovo.json não encontrado")
    }

    @Test fun `central do dono - a tabela lida no LOGNOVO e exatamente a do original para 1,25 bar com P0 2000`() {
        val rif = lognovo("29 7B 00 A4"); val coeff = lognovo("29 7C 00 A5")
        assertEquals(1250, rif[0])     // o "1,25 bar" da tela F4 é RIF_PRESS_DIFF[0]
        assertEquals(2000, rif[10])    // P0 real
        val t = VoltaGasolinaCore.table(1250, 2000)
        assertArrayEquals(t.rif, rif)
        assertArrayEquals(t.coeff, coeff)  // ×100 em 16 bits: SD+0x1DD4 = 0 nesta central
        assertTrue(VoltaGasolinaCore.consistentWithOriginal(rif, coeff))
        assertEquals(0, JSONObject(File(File("").absoluteFile.let { d -> generateSequence(d) { it.parentFile }.first { File(it, "tests/ui/fixtures/ecu-estado-lognovo.json").isFile } }, "tests/ui/fixtures/ecu-estado-lognovo.json").readText())
            .getJSONObject("leituras").getString("09 3E 00 47").toInt(16))   // espera 0 s, como a tela F4
    }

    @Test fun `interruptor interno desligado - a pressao nao envia nenhum byte`() {
        val ecu = FakeEcu(0, rif = VoltaGasolinaCore.table(1300, 1500).rif, coeff = VoltaGasolinaCore.table(1300, 1500).coeff)
        val r = VoltaGasolinaCore.executePressure(ecu.io(), 1250, enabled = false)
        assertEquals(Outcome.DISABLED, r.outcome)
        assertTrue(ecu.frames.isEmpty())
    }

    @Test fun `tabela que nao e a do original (outra familia de central) e recusada sem enviar`() {
        val t = VoltaGasolinaCore.table(1300, 1500)
        val coeff = t.coeff.copyOf().also { it[3] = it[3] + 1 }
        assertFalse(VoltaGasolinaCore.consistentWithOriginal(t.rif, coeff))
        val ecu = FakeEcu(0, rif = t.rif.copyOf(), coeff = coeff)
        assertEquals(Outcome.INCONSISTENT, VoltaGasolinaCore.executePressure(ecu.io(), 1250, enabled = true).outcome)
        assertTrue(ecu.frames.isEmpty())
    }

    @Test fun `pressao ligada no teste - 30 quadros na ordem do original e releitura igual`() {
        val start = VoltaGasolinaCore.table(1300, 1500)
        val ecu = FakeEcu(0, rif = start.rif.copyOf(), coeff = start.coeff.copyOf())
        val r = VoltaGasolinaCore.executePressure(ecu.io(), 1250, enabled = true)
        assertEquals(Outcome.CONFIRMED, r.outcome)
        val want = VoltaGasolinaCore.table(1250, 1500)
        assertEquals(VoltaGasolinaCore.tableFrames(want.rif, want.coeff).map { hex(it) }, ecu.frames.map { hex(it) })
        assertArrayEquals(start.rif, r.beforeRif)
        assertArrayEquals(start.coeff, r.beforeCoeff)
    }

    @Test fun `falha no meio - para de enviar, nao diz gravado e oferece desfazer com o antes`() {
        val start = VoltaGasolinaCore.table(1300, 1500)
        val ecu = FakeEcu(0, rif = start.rif.copyOf(), coeff = start.coeff.copyOf(), nakAt = 7)
        val r = VoltaGasolinaCore.executePressure(ecu.io(), 1250, enabled = true)
        assertEquals(Outcome.PARTIAL, r.outcome)
        assertEquals(8, ecu.frames.size)
        assertNotNull(r.beforeRif)
        // Desfazer: regrava o antes e confere
        val undo = VoltaGasolinaCore.executeRestore(ecu.io(), r.beforeRif!!, r.beforeCoeff!!)
        assertEquals(Outcome.CONFIRMED, undo.outcome)
        assertArrayEquals(start.rif, ecu.rif)
        assertArrayEquals(start.coeff, ecu.coeff)
    }

    @Test fun `pressao fora de 0,2 a 2,0 bar e recusada`() {
        val ecu = FakeEcu(0)
        assertEquals(Outcome.INVALID, VoltaGasolinaCore.executePressure(ecu.io(), 2100, enabled = true).outcome)
        assertTrue(ecu.frames.isEmpty())
    }
}
