package com.omegas.prohub.autocal

import com.omegas.prohub.ecu.AutoCalProtocol
import org.json.JSONObject

/**
 * Época da evidência nativa do GNV (buffers PETR_INJ_TBUF_GAS / NUM_BUF_UPD_GAS da ECU).
 *
 * O alvo do refino é K_alvo(T_p) = K(T_g)·T_g/T_p, onde K(T_g) tem de ser o K sob o qual o T_g foi ADQUIRIDO. Os
 * buffers do GNV só são zerados pelo AutoMatch nativo; quando é o APP que grava a Curva K (ou o Mapa K, ou a curva
 * muda por fora), os buffers continuam com o T_g medido sob a curva antiga, e o snapshot seguinte já traz o MUL_ACT
 * novo. Usar esse T_g com o K novo aplica a correção duas vezes.
 *
 * Solução (a mais simples que nunca mistura épocas): no instante de cada gravação ([markWrite]) a época abre; a
 * primeira leitura dos buffers do GNV feita depois dela vira a LINHA DE BASE dos contadores; dali em diante cada banda
 * vale só pelo que o contador GANHOU desde a linha de base ([effectiveGasCounts]) — banda que não subiu não é
 * evidência (contagem 0), banda que subiu pouco é fina (< [AutoMatchRefinedEngine.BAND_MATURE_COUNT]). Contador que
 * VOLTOU abaixo da linha de base foi zerado (AutoMatch nativo) depois da gravação: tudo nele é da época nova.
 * Snapshot lido inteiro antes da gravação (buffers e MUL_ACT) é coerente consigo mesmo e passa intacto.
 *
 * Limites conhecidos (falham fechados ou são conservadores): a linha de base é a PRIMEIRA leitura depois da gravação,
 * então amostras novas feitas antes dela são descartadas; contador saturado (10 na ECU observada) não sobe e a banda
 * só volta a valer depois do próximo AutoMatch; o valor do buffer de uma banda que subiu pode ainda carregar parte da
 * média antiga (a ECU não informa como pondera). A época vive em memória do processo: reiniciar o app a esquece.
 *
 * O serviço marca as gravações em [shared]; a análise (AutoMatchSnapshotAnalysis.analyzeRefined) e o monitor
 * observam os snapshots. Testes usam instâncias próprias.
 */
class NativeGasEvidenceEpoch {
    companion object {
        /** Instância do processo: o serviço marca, a ponte lê (a ponte não recebe dependências novas). */
        val shared = NativeGasEvidenceEpoch()
    }

    private val lock = Any()
    private var writeAtMs: Long? = null
    private var baseline: IntArray? = null

    /** O app gravou a Curva K / Mapa K (ou a curva mudou por fora) em [atMs]: os buffers do GNV de antes deixam de valer. */
    fun markWrite(atMs: Long) = synchronized(lock) {
        writeAtMs = atMs
        baseline = null
    }

    /** Há uma época aberta (alguma gravação marcada neste processo)? */
    fun active(): Boolean = synchronized(lock) { writeAtMs != null }

    /** Só registra a linha de base com um snapshot (o monitor chama a cada leitura nova; não depende da tela aberta). */
    fun observe(snapshot: JSONObject) {
        val counts = gasCounts(snapshot) ?: return
        effectiveGasCounts(counts, fieldTime(snapshot, AutoCalProtocol.NUM_BUF_UPD_GAS.key), fieldTime(snapshot, AutoCalProtocol.MUL_ACT.key))
    }

    /**
     * Contadores do GNV que valem como evidência nesta leitura. [gasCapturedAtMs]/[mulCapturedAtMs] = instante em que
     * NUM_BUF_UPD_GAS e MUL_ACT foram lidos.
     */
    fun effectiveGasCounts(counts: IntArray, gasCapturedAtMs: Long, mulCapturedAtMs: Long): IntArray = synchronized(lock) {
        val write = writeAtMs ?: return counts.copyOf()
        if (gasCapturedAtMs < write && mulCapturedAtMs < write) return counts.copyOf()
        if (gasCapturedAtMs >= write && baseline == null) baseline = counts.copyOf()
        val base = baseline ?: return IntArray(counts.size)
        IntArray(counts.size) { b ->
            val before = base.getOrElse(b) { 0 }
            if (counts[b] >= before) counts[b] - before else counts[b]
        }
    }

    private fun gasCounts(snapshot: JSONObject): IntArray? {
        val field = field(snapshot, AutoCalProtocol.NUM_BUF_UPD_GAS.key) ?: return null
        if (field.optString("status") != AutoCalFieldStatus.VALID.name) return null
        val raw = field.optJSONArray("rawValues") ?: return null
        return IntArray(raw.length()) { raw.optInt(it) }
    }

    internal fun fieldTime(snapshot: JSONObject, key: String): Long =
        field(snapshot, key)?.optLong("capturedAtMs", 0L)?.takeIf { it > 0L } ?: snapshot.optLong("capturedAtMs", 0L)

    private fun field(snapshot: JSONObject, key: String): JSONObject? {
        val fields = snapshot.optJSONArray("fields") ?: return null
        for (i in 0 until fields.length()) {
            val f = fields.optJSONObject(i) ?: continue
            if (f.optString("key") == key) return f
        }
        return null
    }
}
