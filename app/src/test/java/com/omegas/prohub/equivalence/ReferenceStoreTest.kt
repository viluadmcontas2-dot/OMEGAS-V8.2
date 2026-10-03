package com.omegas.prohub.equivalence

import com.omegas.prohub.autocal.RealSessionReplaySupport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReferenceStoreTest {
    private val ref = RealSessionReplaySupport.fixture(RealSessionReplaySupport.REFERENCE)
    private fun acq(seq: Int) = RealSessionReplaySupport.acquisition(RealSessionReplaySupport.snapshot(ref, seq))

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `congela a aquisicao madura de gasolina da ECU e rele do arquivo`() {
        val file = tmp.newFile("reference.json")
        val r = ReferenceStore(file).freeze(acq(95), 1_000L)
        assertEquals("REF-1000", r.id)
        assertEquals(1_000L, r.frozenAt)
        assertEquals(16, r.points.size)
        assertEquals(RefPoint(0.1865234375, 1.77734375, 10), r.points.first())
        assertEquals(r, ReferenceStore(file).current())
    }

    @Test
    fun `aquisicao imatura nao congela e nao apaga a atual`() {
        val s = ReferenceStore(null)
        val r = s.freeze(acq(95), 1L)
        val e = assertThrows(IllegalStateException::class.java) { s.freeze(acq(962), 2L) }
        assertEquals("AQUISICAO_IMATURA", e.message)
        assertEquals(r, s.current())
    }

    @Test
    fun `ECU reaprende gasolina depois do congelamento - referencia intacta e deriva reportada`() {
        val file = tmp.newFile("reference.json")
        val s = ReferenceStore(file)
        val r = s.freeze(acq(95), 1L)
        val bytes = file.readBytes()
        assertNull(s.ecuDrift(acq(962)))
        assertTrue(s.ecuDrift(acq(2183))!! > 0.10)
        assertEquals(r, s.current())
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `congelar de novo guarda a anterior so ate o fim da sessao`() {
        val s = ReferenceStore(null)
        val a = s.freeze(acq(95), 1L)
        val b = s.freeze(acq(2183), 2L)
        assertEquals(a, s.previous())
        assertEquals(a, s.restorePrevious())
        assertEquals(b, s.previous())
        s.endSession()
        assertNull(s.previous())
    }

    @Test
    fun `arquivo corrompido ou de outro formato abre sem referencia`() {
        val f = tmp.newFile("reference.json")
        f.writeText("{nao json")
        assertNull(ReferenceStore(f).current())
        f.writeText("""{"format":"outro"}""")
        assertNull(ReferenceStore(f).current())
    }

    @Test
    fun `provisoria vem da ECU ao vivo e nunca persiste`() {
        val f = File(tmp.root, "reference.json")
        val p = ReferenceStore(f).provisional(acq(95))!!
        assertEquals(ReferenceStore.PROVISIONAL_ID, p.id)
        assertEquals(16, p.points.size)
        assertFalse(f.exists())
        assertNull(ReferenceStore(f).provisional(acq(962)))
    }
}
