package com.omegas.prohub.autocal

/**
 * Ordem de invalidação da evidência quando o K muda: primeiro o que invalida (cada passo no seu
 * try/catch), só depois o que registra. Disco cheio no gravador nunca deixa evidência velha valendo.
 */
object EvidenceInvalidation {
    fun run(
        invalidate: List<Pair<String, () -> Unit>>,
        record: () -> Unit,
        warn: (String) -> Unit,
    ) {
        for ((name, step) in invalidate) {
            try { step() } catch (error: Throwable) { warn("Invalidação '$name' falhou: ${error.message}") }
        }
        try { record() } catch (error: Throwable) { warn("Gravador de sessão falhou: ${error.message}") }
    }
}
