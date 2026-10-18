package com.omegas.prohub.service

/** Política pura: nenhum relógio; uma I/O, sessão cercada e eventos concorrentes preservados. */
internal class CalibrationReadEvents {
    data class Ticket(val session: Long, val revision: Long, val trigger: String, val parameters: Boolean)
    companion object {
        /**
         * Nunca é motivo de releitura: a gravação manual já termina com ACK + leitura de volta (e atualiza o cache), e o
         * KWriteManager chama o mesmo callback ao fim de TODA leitura completa do mapa — inclusive a automática. Pedir
         * releitura aqui fazia a leitura automática gerar a próxima, sem fim (serial ocupada, operador recusado).
         */
        const val WRITE_CONFIRMED = "WRITE_CONFIRMED"
    }
    private var session = 0L
    private var revision = 0L
    private var pending: String? = null
    private var attempts = 0
    private var parameters = false
    private var active: Ticket? = null

    private fun connect(id: Long) {
        if (session == id) return
        session = id
        revision++
        pending = "AUTO_START"
        attempts = 0
        parameters = false
    }

    @Synchronized fun request(id: Long, trigger: String) {
        if (id <= 0L || trigger == WRITE_CONFIRMED) return
        connect(id)
        // AUTO_START tem precedência até a primeira leitura da conexão.
        if (pending != "AUTO_START") pending = trigger
        revision++
        attempts = 0
    }

    @Synchronized fun next(id: Long, busy: Boolean): Ticket? {
        if (id <= 0L) return null
        connect(id)
        if (busy || active != null || attempts >= 4) return null
        val trigger = pending ?: return null
        val ticket = Ticket(session, revision, trigger, !parameters)
        parameters = true
        attempts++
        active = ticket
        // Novos eventos devem permanecer pendentes até a próxima leitura.
        pending = null
        return ticket
    }

    @Synchronized fun complete(ticket: Ticket, ok: Boolean) {
        if (active != ticket) return
        active = null
        if (session != ticket.session || revision != ticket.revision) return
        if (!ok) pending = ticket.trigger
    }
}
