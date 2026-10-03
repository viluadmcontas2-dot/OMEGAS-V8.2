package com.omegas.prohub.calibration

import java.util.concurrent.atomic.AtomicReference

/**
 * Trava única da unidade serial para operações que MUDAM a ECU (escritores K e ações AutoCal).
 *
 * Cada escritor mantém a própria flag `busy`, mas isso não impede que uma ação AutoCal e uma
 * escrita K rodem ao mesmo tempo (as duas intercalam transações na mesma serial). Quem vai
 * mudar a ECU precisa adquirir esta trava primeiro e liberá-la no `finally`.
 * Não envia nenhum byte; só arbitra quem pode começar.
 */
class SerialWriteGuard {
    private val holder = AtomicReference<String?>(null)

    /** `true` se [owner] agora detém a trava; `false` se outra operação a detém. */
    fun tryAcquire(owner: String): Boolean = holder.compareAndSet(null, owner)

    /** Só libera se [owner] ainda for o detentor; liberar duas vezes é inofensivo. */
    fun release(owner: String) {
        holder.compareAndSet(owner, null)
    }

    fun holder(): String? = holder.get()

    fun isHeld(): Boolean = holder.get() != null

    companion object {
        const val OWNER_K_FACTOR = "CURVA_K"
        const val OWNER_K_MAP = "MAPA_K"
        const val OWNER_AUTOCAL = "AUTOCAL"

        /** Instância única do processo; os testes injetam a própria. */
        val shared = SerialWriteGuard()

        fun label(owner: String?): String = when (owner) {
            OWNER_K_FACTOR -> "uma alteração da Curva K"
            OWNER_K_MAP -> "uma alteração do Mapa K"
            OWNER_AUTOCAL -> "uma ação do AutoCal"
            else -> "outra operação na ECU"
        }
    }
}
