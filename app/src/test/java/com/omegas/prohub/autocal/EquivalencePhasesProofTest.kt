package com.omegas.prohub.autocal

import com.omegas.prohub.equivalence.EquivalencePoint
import com.omegas.prohub.equivalence.PointState
import com.omegas.prohub.equivalence.ProofOutcome
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Prova por ponto da Fatia F4: EM_PROVA → CONFIRMADO | CONTESTADO | INCONCLUSIVO. */
class EquivalencePhasesProofTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 0L
    private fun phases() = EquivalencePhases(null) { now }

    private fun point(
        index: Int, mixture: Double?, samples: Int, rough: Double? = null, tolerance: Double = 0.04,
        episodes: Int = 3, state: PointState = PointState.POBRE,
    ) = EquivalencePoint(
        index, 6.0, 1.0, null, mixture, tolerance, rough, null, null, 0.1, samples, emptySet(), state, episodes,
    )

    @Test
    fun `ajuste gravado entra em prova e confirma com amostra nova dentro da tolerancia`() {
        val p = phases()
        p.beginProof(listOf(12), listOf(point(12, 0.06, 20, rough = 1.0)))
        assertEquals(PointState.EM_PROVA, p.judgePoints(listOf(point(12, 0.01, 3)), true).states[12])
        assertEquals(PointState.CONFIRMADO, p.judgePoints(listOf(point(12, 0.01, 8, rough = 1.02)), true).states[12])
        // o veredito fica até nova prova do ponto
        assertEquals(PointState.CONFIRMADO, p.judgePoints(emptyList(), true).states[12])
    }

    @Test
    fun `mistura melhorou e tremor piorou vira CONTESTADO`() {
        val p = phases()
        p.beginProof(listOf(12), listOf(point(12, 0.06, 20, rough = 1.0)))
        assertEquals(PointState.CONTESTADO, p.judgePoints(listOf(point(12, 0.02, 8, rough = 1.10)), true).states[12])
    }

    @Test
    fun `piora de experiencia abaixo de 4 por cento nao contesta`() {
        val p = phases()
        p.beginProof(listOf(12), listOf(point(12, 0.06, 20, rough = 1.0)))
        assertEquals(PointState.CONFIRMADO, p.judgePoints(listOf(point(12, 0.01, 8, rough = 1.035)), true).states[12])
    }

    @Test
    fun `mistura que nao fechou e nao melhorou fecha INCONCLUSIVO com motivo e nunca some em silencio`() {
        val p = phases()
        p.beginProof(listOf(12), listOf(point(12, 0.06, 20)))
        val outcome = p.judgePoints(listOf(point(12, 0.09, 8)), true)
        assertEquals(PointState.INCONCLUSIVO, outcome.states[12])
        assertEquals(ProofOutcome.REASON_NO_CONVERGENCE, outcome.reasons[12])
        assertEquals(0, p.openProofCount())
        val json = p.json().getJSONArray("proofs").getJSONObject(0)
        assertEquals("INCONCLUSIVO", json.getString("state"))
        assertEquals("NAO_CONVERGIU", json.getString("reason"))
    }

    @Test
    fun `a re-proposta nao entra em laco - duas tentativas e depois fica parada com motivo`() {
        val p = phases()
        fun attempt(): ProofOutcome {
            p.beginProof(listOf(12), listOf(point(12, 0.09, 20)))
            return p.judgePoints(listOf(point(12, 0.09, 8)), true)
        }
        assertEquals(ProofOutcome.REASON_NO_CONVERGENCE, attempt().reasons[12])
        // depois de um tempo de condução o ponto volta a poder ser proposto (a prova fechada some)...
        var guard = 0
        while (guard++ < 200 && p.json().getJSONArray("proofs").length() > 0) {
            now += 10_000
            p.judgePoints(listOf(point(12, 0.09, 8)), true)
        }
        assertEquals(0, p.json().getJSONArray("proofs").length())
        // ...mas a segunda tentativa que não fecha esgota: sem nova proposta ali, e o motivo fica registrado.
        val second = attempt()
        assertEquals(PointState.INCONCLUSIVO, second.states[12])
        assertEquals(ProofOutcome.REASON_EXHAUSTED, second.reasons[12])
        repeat(100) { now += 10_000; p.judgePoints(listOf(point(12, 0.09, 8)), true) }
        assertEquals(1, p.json().getJSONArray("proofs").length())
        assertEquals(PointState.INCONCLUSIVO, p.judgePoints(emptyList(), true).states[12])
    }

    @Test
    fun `sem episodios independentes ou sem o ponto julgado a prova nao confirma`() {
        val p = phases()
        p.beginProof(listOf(12), listOf(point(12, 0.06, 20)))
        // 20 leituras dentro da tolerância mas numa visita só (episodes = 1): continua em prova
        assertEquals(PointState.EM_PROVA, p.judgePoints(listOf(point(12, 0.01, 20, episodes = 1)), true).states[12])
        // o cérebro não julgou o ponto (APRENDENDO): continua em prova
        assertEquals(PointState.EM_PROVA, p.judgePoints(listOf(point(12, 0.01, 20, state = PointState.APRENDENDO)), true).states[12])
        assertEquals(PointState.CONFIRMADO, p.judgePoints(listOf(point(12, 0.01, 20, episodes = 3)), true).states[12])
        assertEquals(1, p.json().getJSONArray("proofs").length())
    }

    @Test
    fun `sem amostra ate o timebox de 15 min de conducao vira INCONCLUSIVO e o restante e informado`() {
        val p = phases()
        p.beginProof(listOf(12), listOf(point(12, 0.06, 20)))
        p.judgePoints(listOf(point(12, null, 0)), true)
        repeat(6) {
            now += 10_000
            p.judgePoints(listOf(point(12, null, 0)), true)
        }
        assertEquals(14, p.judgePoints(listOf(point(12, null, 0)), false).remainingMinutes)
        // offline não conta
        now += 20 * 60_000L
        assertEquals(PointState.EM_PROVA, p.judgePoints(listOf(point(12, null, 0)), false).states[12])
        var verdict: PointState? = null
        var guard = 0
        while (verdict != PointState.INCONCLUSIVO && guard++ < 200) {
            now += 10_000
            verdict = p.judgePoints(listOf(point(12, null, 0)), true).states[12]
        }
        assertEquals(PointState.INCONCLUSIVO, verdict)
        assertTrue("prazo estourou cedo ou tarde: $guard iterações", guard in 80..90)
    }

    @Test
    fun `trocar referencia recomeca a prova sem perder o ponto`() {
        val p = phases()
        p.beginProof(listOf(12), listOf(point(12, 0.06, 20)))
        p.judgePoints(listOf(point(12, null, 0)), true)
        now += 10_000
        p.judgePoints(listOf(point(12, null, 0)), true)
        assertTrue(p.json().getJSONArray("proofs").getJSONObject(0).getLong("onlineMs") > 0L)
        p.restartProofs("REFERENCIA_CONGELADA")
        val proof = p.json().getJSONArray("proofs").getJSONObject(0)
        assertEquals(0L, proof.getLong("onlineMs"))
        assertEquals("EM_PROVA", proof.getString("state"))
    }

    @Test
    fun `Mapa K ou AutoMatch nativo apagam as provas abertas mas nao os vereditos`() {
        val p = phases()
        p.beginProof(listOf(12, 13), listOf(point(12, 0.06, 20), point(13, 0.06, 20)))
        p.judgePoints(listOf(point(12, 0.01, 8)), true)
        p.interruptProofs("AUTOMATCH_NATIVO")
        val states = p.judgePoints(emptyList(), true).states
        assertEquals(PointState.CONFIRMADO, states[12])
        assertNull(states[13])
    }

    @Test
    fun `provas persistem no mesmo arquivo e arquivo antigo sem proofs abre sem crash e sem provas`() {
        val file = tmp.root.resolve("refinement_autopilot.json")
        val p = EquivalencePhases(file) { now }
        p.beginProof(listOf(12), listOf(point(12, 0.06, 20, rough = 1.0)))
        val saved = JSONObject(file.readText())
        assertEquals("omegas-refinement-autopilot-v1", saved.getString("format"))
        assertEquals(1, saved.getJSONArray("proofs").length())
        assertEquals(1, EquivalencePhases(file) { now }.openProofCount())

        file.writeText("""{"format":"omegas-refinement-autopilot-v1","phase":"ESTAVEL","lastCount":3,"quietMs":0}""")
        val old = EquivalencePhases(file) { now }
        assertEquals(0, old.json().optJSONArray("proofs")?.length() ?: 0)
        assertTrue(old.judgePoints(emptyList(), true).states.isEmpty())
    }
}
