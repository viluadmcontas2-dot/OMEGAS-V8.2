package com.omegas.prohub.runtime

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Latest-only in-memory state cache for UI consumers.
 *
 * Lote D: além do quadro "present" (revisão própria), mantém UMA revisão por tipo de dado
 * ([Kind]): `live`, `evidence`, `tables`, `session`. Cada revisão só sobe quando o dado realmente
 * mudou ([bump] explícito ou [publishIfChanged] por assinatura/hash), então a UI pergunta
 * `getRevisions`/`...IfChanged` e só relê o que mudou (D2: só o vivo é tempo real; tabelas só ao mudar).
 * Não toca ECU, serial nem writer.
 */
class RuntimeSnapshotBus {
    enum class Kind(val wireName: String) {
        LIVE("live"),
        EVIDENCE("evidence"),
        TABLES("tables"),
        SESSION("session"),
        ;

        companion object {
            fun fromWire(name: String): Kind? = values().firstOrNull { it.wireName == name.trim().lowercase() }
        }
    }

    private val lock = Any()
    private val presentRevision = AtomicLong(0L)
    private var presentData = JSONObject()
    private val kindRevisions = Kind.values().associateWith { AtomicLong(0L) }
    private val signatures = HashMap<Kind, String>()

    fun publishPresent(payload: JSONObject): Long = synchronized(lock) {
        presentData = JSONObject(payload.toString())
        presentRevision.incrementAndGet()
    }

    fun presentJson(): JSONObject = synchronized(lock) {
        JSONObject()
            .put("ok", true)
            .put("revision", presentRevision.get())
            .put("data", JSONObject(presentData.toString()))
    }

    /** Sobe a revisão do tipo (o dado mudou); devolve a nova revisão. */
    fun bump(kind: Kind): Long = kindRevisions.getValue(kind).incrementAndGet()

    fun revision(kind: Kind): Long = kindRevisions.getValue(kind).get()

    /**
     * Só sobe a revisão se [signature] (hash/resumo do conteúdo) difere da última vista para o tipo.
     * Devolve a revisão corrente, alterada ou não. A primeira assinatura conta como mudança.
     */
    fun publishIfChanged(kind: Kind, signature: String): Long = synchronized(lock) {
        if (signatures[kind] == signature) {
            kindRevisions.getValue(kind).get()
        } else {
            signatures[kind] = signature
            kindRevisions.getValue(kind).incrementAndGet()
        }
    }

    /** Sobe o tipo de forma observável (`true` se mudou) — para quem já decidiu por comparação externa. */
    fun changedSince(kind: Kind, lastSeen: Long): Boolean = lastSeen < 0L || revision(kind) != lastSeen

    /** `{ok, revisions:{live,evidence,tables,session}}` — barato: nenhum payload é montado. */
    fun revisionsJson(liveRevision: Long? = null): JSONObject {
        val revisions = JSONObject()
        Kind.values().forEach { kind ->
            revisions.put(kind.wireName, if (kind == Kind.LIVE && liveRevision != null) liveRevision else revision(kind))
        }
        return JSONObject().put("ok", true).put("revisions", revisions)
    }
}

/**
 * Empurra `window.OmegasOnRevision(kind, revision)` à WebView sem inundá-la: por tipo, no máximo uma
 * entrega a cada [minIntervalMs] (padrão 100 ms), sempre com a revisão MAIS RECENTE. É só um acelerador:
 * a UI mantém o poll de segurança (>= 2 s). Não conhece ECU, serial nem WebView (injetados).
 */
class RevisionPushCoalescer(
    private val clock: () -> Long,
    private val schedule: (delayMs: Long, task: () -> Unit) -> Unit,
    private val dispatch: (kind: RuntimeSnapshotBus.Kind, revision: Long) -> Unit,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
) {
    private val lock = Any()
    private val lastDeliveredAt = HashMap<RuntimeSnapshotBus.Kind, Long>()
    private val latest = HashMap<RuntimeSnapshotBus.Kind, Long>()
    private val scheduled = HashSet<RuntimeSnapshotBus.Kind>()

    fun onRevision(kind: RuntimeSnapshotBus.Kind, revision: Long) {
        val delay = synchronized(lock) {
            latest[kind] = revision
            if (kind in scheduled) return // já há uma entrega marcada: ela levará a revisão mais nova
            val since = clock() - (lastDeliveredAt[kind] ?: (Long.MIN_VALUE / 2))
            scheduled += kind
            (minIntervalMs - since).coerceAtLeast(0L)
        }
        schedule(delay) { deliver(kind) }
    }

    private fun deliver(kind: RuntimeSnapshotBus.Kind) {
        val revision = synchronized(lock) {
            scheduled -= kind
            val newest = latest[kind] ?: return
            lastDeliveredAt[kind] = clock()
            newest
        }
        dispatch(kind, revision)
    }

    companion object {
        const val MIN_INTERVAL_MS = 100L
    }
}

/**
 * Onde a Activity registra o empurrão de revisão. A Activity limpa (null) no onDestroy: o serviço não segura
 * a Activity/WebView velha. Exceção do ouvinte nunca sobe ao serviço.
 */
class RevisionListenerSlot {
    @Volatile private var listener: ((RuntimeSnapshotBus.Kind, Long) -> Unit)? = null

    fun set(value: ((RuntimeSnapshotBus.Kind, Long) -> Unit)?) {
        listener = value
    }

    fun isSet(): Boolean = listener != null

    fun publish(kind: RuntimeSnapshotBus.Kind, revision: Long) {
        try { listener?.invoke(kind, revision) } catch (_: Throwable) {}
    }
}
