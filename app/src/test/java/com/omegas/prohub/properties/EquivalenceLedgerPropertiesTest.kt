package com.omegas.prohub.properties

import com.omegas.prohub.autocal.EquivalenceLedger
import com.omegas.prohub.properties.PropertySupport.DriveScript
import com.omegas.prohub.properties.PropertySupport.SEEDS
import com.omegas.prohub.properties.PropertySupport.assertFiniteJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Propriedades do livro de equivalência com sequências aleatórias de semente fixa (Lote W, parte 3).
 * Mesma entrada → mesma saída; reaplicar a mesma impressão digital da curva não zera nada; salvar → carregar →
 * salvar dá o mesmo arquivo; arquivo no formato antigo continua carregando; nenhum JSON emitido tem número inválido.
 */
class EquivalenceLedgerPropertiesTest {
    private fun feed(ledger: EquivalenceLedger, frames: List<EquivalenceLedger.Frame>) = frames.forEach { ledger.accept(it) }

    private fun snapshotOf(ledger: EquivalenceLedger): String = listOf(
        ledger.index().toString(),
        ledger.pairs().toString(),
        ledger.drivingPairs().toString(),
        ledger.petrolObservations().toString(),
        ledger.gasObservations().toString(),
        ledger.denseBandsJson().toString(),
        ledger.typicalBandsJson().toString(),
        ledger.gasPerAir().toString(),
    ).joinToString("|")

    @Test
    fun `mesma entrada produz exatamente a mesma saida em 12 sementes`() {
        for (seed in SEEDS) {
            val frames = DriveScript(seed).frames(1_500)
            var now = 0L
            val a = EquivalenceLedger(null) { now }
            val b = EquivalenceLedger(null) { now }
            now = 5_000L
            feed(a, frames)
            feed(b, frames)
            assertEquals("semente $seed", snapshotOf(a), snapshotOf(b))
            assertEquals(a.revision(), b.revision())
        }
    }

    @Test
    fun `ler duas vezes sem dado novo nao muda nada (indice e pares idempotentes)`() {
        for (seed in SEEDS) {
            val ledger = EquivalenceLedger(null)
            feed(ledger, DriveScript(seed).frames(1_200))
            val first = snapshotOf(ledger)
            val revision = ledger.revision()
            assertEquals(first, snapshotOf(ledger))
            assertEquals(first, snapshotOf(ledger))
            assertEquals("ler não pode mexer na revisão", revision, ledger.revision())
        }
    }

    @Test
    fun `alinhar ou adotar a MESMA curva varias vezes e no-op e nao descarta o GNV`() {
        for (seed in SEEDS) {
            val ledger = EquivalenceLedger(null)
            val frames = DriveScript(seed).frames(1_500)
            feed(ledger, frames.take(400))
            ledger.alignCurve("curva-A")
            feed(ledger, frames.drop(400))
            val gasBefore = ledger.gasObservations()
            val petrolBefore = ledger.petrolObservations()
            val reasonBefore = ledger.index().getString("gasEpochReason")
            repeat(6) {
                ledger.alignCurve("curva-A")
                ledger.adoptCurve("curva-A")
            }
            assertEquals("semente $seed: GNV mudou ao reaplicar a mesma curva", gasBefore, ledger.gasObservations())
            assertEquals(petrolBefore, ledger.petrolObservations())
            assertEquals(reasonBefore, ledger.index().getString("gasEpochReason"))
        }
    }

    @Test
    fun `curva diferente descarta o GNV UMA vez e preserva a gasolina`() {
        for (seed in SEEDS) {
            val ledger = EquivalenceLedger(null)
            val frames = DriveScript(seed).frames(1_500)
            ledger.alignCurve("A")
            feed(ledger, frames)
            val petrol = ledger.petrolObservations()
            if (ledger.gasObservations().isEmpty()) continue
            ledger.adoptCurve("B")
            assertTrue("GNV da curva antiga precisa sair", ledger.gasObservations().isEmpty())
            assertEquals("a gasolina nunca é descartada", petrol, ledger.petrolObservations())
            val epochAfter = ledger.index().getString("gasEpochReason")
            ledger.adoptCurve("B")
            ledger.alignCurve("B")
            assertEquals(epochAfter, ledger.index().getString("gasEpochReason"))
        }
    }

    @Test
    fun `apos qualquer sequencia aleatoria de quadros, trocas de combustivel, gravacoes de K e resets o indice e valido`() {
        for (seed in SEEDS) {
            val rnd = Random(seed * 7919)
            var now = 0L
            val ledger = EquivalenceLedger(null) { now }
            val frames = DriveScript(seed).frames(2_000)
            var fp = 0
            frames.forEachIndexed { i, frame ->
                now += 120L
                ledger.accept(frame)
                when (rnd.nextInt(180)) {
                    0 -> ledger.adoptCurve("K-${fp++}")
                    1 -> ledger.alignCurve("K-${rnd.nextInt(3)}")
                    2 -> ledger.resetGas("RESET_$i")
                    3 -> ledger.setEcuPetrolReference((0..9).map { (0.2 + it * 0.07) to (2.0 + it * 0.9) })
                }
                if (i % 150 == 0) {
                    val index = ledger.index()
                    assertFiniteJson(index)
                    assertTrue(index.getInt("samples") >= 0)
                    if (!index.isNull("ratio")) assertTrue("razão negativa", index.getDouble("ratio") > 0.0)
                    val bands: JSONArray = index.getJSONArray("bands")
                    for (b in 0 until bands.length()) {
                        val band: JSONObject = bands.getJSONObject(b)
                        assertTrue(band.getInt("samples") >= 0)
                        if (!band.isNull("ratio")) assertTrue(band.getDouble("ratio") > 0.0)
                    }
                    assertFiniteJson(ledger.denseBandsJson())
                    assertFiniteJson(ledger.typicalBandsJson())
                    ledger.gasPerAir()?.let { assertTrue(it.isFinite() && it >= 0.0) }
                }
            }
            ledger.drivingPairs().forEach { p ->
                assertTrue(p.petrolRefMs.isFinite() && p.petrolRefMs > 0.0)
                assertTrue(p.gasPetrolMs.isFinite() && p.gasPetrolMs > 0.0)
            }
        }
    }

    @Test
    fun `salvar carregar salvar produz o MESMO arquivo`() {
        for (seed in SEEDS.take(6)) {
            val file = PropertySupport.tempFile("ledger-$seed")
            var now = 0L
            val first = EquivalenceLedger(file) { now }
            feed(first, DriveScript(seed).frames(1_400))
            first.alignCurve("curva-X")
            first.flush()
            assertTrue("arquivo gravado", file.isFile)
            val bytesA = file.readText()
            val second = EquivalenceLedger(file) { now }
            assertEquals("carregar reproduz as leituras", first.gasObservations().size, second.gasObservations().size)
            assertEquals(first.petrolObservations().size, second.petrolObservations().size)
            now += 1_000L
            second.alignCurve("curva-X") // marca sujo (mesma curva: nada é descartado)
            second.flush()
            val bytesB = file.readText()
            assertEquals("semente $seed: salvar→carregar→salvar mudou o arquivo", bytesA, bytesB)
            assertEquals(snapshotOf(second), snapshotOf(EquivalenceLedger(file) { now }))
            file.delete()
        }
    }

    @Test
    fun `arquivo no formato antigo (um array por leitura, sem episodios) continua carregando e vira indice valido`() {
        val file = PropertySupport.tempFile("ledger-old")
        val petrol = JSONArray()
        val gas = JSONArray()
        var t = 1_000L
        repeat(60) { i ->
            val map = 0.30 + 0.02 * (i % 30)
            petrol.put(JSONArray().put(t).put(2000.0 + i % 3).put(map).put(5.0))
            gas.put(JSONArray().put(t + 40_000L).put(2001.0 + i % 3).put(map).put(5.5))
            t += if (i % 20 == 19) 90_000L else 300L
        }
        file.writeText(JSONObject().put("format", EquivalenceLedger.FORMAT).put("petrol", petrol).put("gas", gas)
            .put("curveFingerprint", "antigo").toString())
        val ledger = EquivalenceLedger(file)
        assertEquals(60, ledger.petrolObservations().size)
        assertEquals(60, ledger.gasObservations().size)
        assertTrue("episódios derivados da lacuna entre leituras", ledger.gasObservations().map { it.episode }.toSet().size >= 3)
        val index = ledger.index()
        assertFiniteJson(index)
        assertEquals(1.1, index.getDouble("ratio"), 1e-6)
        ledger.adoptCurve("antigo")
        assertEquals(60, ledger.gasObservations().size)
        file.delete()
    }

    @Test
    fun `arquivo corrompido ou de outro formato nao derruba o livro`() {
        for (content in listOf("", "{", "[]", "{\"format\":\"outro\"}", "{\"format\":\"${EquivalenceLedger.FORMAT}\",\"petrol\":{\"t\":[1,2],\"rpm\":[1]}}", "null")) {
            val file = PropertySupport.tempFile("ledger-bad")
            file.writeText(content)
            val ledger = EquivalenceLedger(file)
            assertFiniteJson(ledger.index())
            feed(ledger, DriveScript(4).frames(300))
            assertFiniteJson(ledger.index())
            file.delete()
        }
    }

    @Test
    fun `quadros degenerados (NaN, infinito, negativos) nunca geram JSON invalido nem excecao`() {
        val ledger = EquivalenceLedger(null)
        var t = 0L
        val poison = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0, 1e12)
        for (a in poison) for (b in poison) {
            repeat(4) {
                t += 100L
                ledger.accept(EquivalenceLedger.Frame(t, "GNV", a, b, 5.0, 6.0))
                ledger.accept(EquivalenceLedger.Frame(t + 1, "GASOLINA", 2000.0, b, a, 0.0))
            }
        }
        feed(ledger, DriveScript(9).frames(400))
        assertFiniteJson(ledger.index())
        assertFiniteJson(ledger.denseBandsJson())
        assertFiniteJson(ledger.typicalBandsJson())
        ledger.petrolObservations().forEach { assertTrue("observação não finita entrou no livro: $it", it.rpm.isFinite() && it.map.isFinite() && it.petrolMs.isFinite()) }
        ledger.gasObservations().forEach { assertTrue("observação não finita entrou no livro: $it", it.rpm.isFinite() && it.map.isFinite() && it.petrolMs.isFinite()) }
    }
}
