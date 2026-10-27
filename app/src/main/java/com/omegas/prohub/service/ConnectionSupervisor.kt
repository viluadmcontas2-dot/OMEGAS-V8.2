package com.omegas.prohub.service

import org.json.JSONObject

/** Próxima ação que o serviço executa usando SÓ a API pública da USB/núcleo (o supervisor não as conhece). */
internal enum class ConnectionAction { NONE, REQUEST_PERMISSION, CONNECT, RESTART_ENGINE, SHOW_PROBLEM }

/** Pendência de gravação em etapas (vinda do WriteJournal): "[done] gravado. [missing] ainda não." */
internal data class PendingWriteInfo(val done: String, val missing: String)

/**
 * Decide a próxima ação a partir de eventos. Reconexão por CONTAGEM (tentativas e leituras sem quadro), nunca por
 * relógio (limites MAX_READS_WITHOUT_FRAME e MAX_RECONNECTS, em ConnectionPolicy). Nunca grava nem retoma gravação sozinho: só informa a pendência para a tela oferecer "Concluir".
 */
internal class ConnectionSupervisor {
    @Volatile var state: ConnectionState = ConnectionState()
        private set
    @Volatile var pending: PendingWriteInfo? = null

    @Synchronized fun onEvent(e: ConnectionEvent): ConnectionAction {
        val before = state
        state = ConnectionPolicy.next(before, e)
        return when (e) {
            ConnectionEvent.UsbAttached, ConnectionEvent.PermissionGranted -> ConnectionAction.CONNECT
            ConnectionEvent.UsbDetached ->
                if (state.phase == ConnectionPhase.RETOMANDO) ConnectionAction.CONNECT else ConnectionAction.NONE
            ConnectionEvent.UserRetry ->
                if (before.cause == ConnectionCause.SEM_PERMISSAO) ConnectionAction.REQUEST_PERMISSION else ConnectionAction.CONNECT
            is ConnectionEvent.Read ->
                if (state.phase == ConnectionPhase.PROBLEMA && before.phase != ConnectionPhase.PROBLEMA) ConnectionAction.SHOW_PROBLEM
                else if (!e.valid && state.readsWithoutFrame > 0 && state.readsWithoutFrame % (ConnectionPolicy.MAX_READS_WITHOUT_FRAME / 2) == 0) ConnectionAction.RESTART_ENGINE
                else ConnectionAction.NONE
            ConnectionEvent.IncompatibleDevice, ConnectionEvent.PermissionDenied -> ConnectionAction.SHOW_PROBLEM
            ConnectionEvent.PermissionRequested, ConnectionEvent.Opened, ConnectionEvent.HandshakeOk -> ConnectionAction.NONE
        }
    }

    /** Só a pendência ({feito, falta}) ou "null". */
    fun pendingJson(): String {
        val p = pending ?: return "null"
        return JSONObject().put("feito", p.done).put("falta", p.missing).toString()
    }

    /** JSON para a ponte: {fase, causa, tentativas, pendencia:{feito,falta}|null}. */
    fun toJson(): String {
        val s = state
        val o = JSONObject().put("fase", s.phase.name).put("tentativas", s.attempts)
        o.put("causa", s.cause?.name ?: JSONObject.NULL)
        val p = pending
        o.put("pendencia", if (p == null) JSONObject.NULL else JSONObject().put("feito", p.done).put("falta", p.missing))
        return o.toString()
    }
}

/** Ponto único (processo) onde o serviço publica o estado e a ponte lê. Ainda sem dono no serviço: ver relatório. */
internal object ConnectionHub {
    val supervisor = ConnectionSupervisor()
    fun json(): String = supervisor.toJson()
}
