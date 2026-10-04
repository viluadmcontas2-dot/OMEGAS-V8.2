package com.omegas.prohub.autocal

import java.util.concurrent.atomic.AtomicLong

/**
 * Estado transitório do round de aquisição do monitor nativo (grupo lido à espera de confirmação, grupos de
 * referência retidos, contadores de gasolina do round). Tudo que nasceu ANTES de uma gravação K / ação AutoCal
 * deixa de valer depois dela: [invalidate] zera os três. Sem Android: testável na JVM.
 * O acesso é sob o lock do monitor (como antes da extração).
 */
class RoundScratch<P, H> {
    var pending: P? = null
    var petrolCounters: IntArray? = null
    val hold = ArrayList<H>(3)

    fun invalidate() {
        pending = null
        petrolCounters = null
        hold.clear()
    }
}

/**
 * Geração de escrita: sobe a cada gravação K / ação AutoCal confirmada. Quem lê em várias fatias amostra antes e
 * confere depois; se mudou, o que leu atravessou uma escrita e é descartado (a contagem do AutoMatch não muda
 * numa gravação do dono, então a guarda de época sozinha não vê isso).
 */
class WriteFence {
    private val generation = AtomicLong(0L)
    fun current(): Long = generation.get()
    fun bump(): Long = generation.incrementAndGet()
    fun changedSince(sample: Long): Boolean = generation.get() != sample
}
