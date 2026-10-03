package com.omegas.prohub.link

import org.json.JSONObject

/**
 * Versão do protocolo OMEGAS Link. A V2 não sincroniza mais o aprendizado antigo:
 * um aparelho com APK anterior (V1) é reconhecido e recusado com frase humana, sem fusão.
 */
object LinkProtocol {
    const val CURRENT = "OMEGAS_LINK_V2"
    const val SYNC_SCHEMA = "omegas-link-v7"
    const val OLD_PEER_MESSAGE = "O outro aparelho tem uma versão antiga do OMEGAS. Atualize o app nele para voltar a sincronizar."

    private const val FAMILY = "OMEGAS_LINK_"

    /** Outro OMEGAS com protocolo diferente do atual. Beacon alheio (fora da família) não conta. */
    fun isOldPeer(protocol: String): Boolean = protocol.startsWith(FAMILY) && protocol != CURRENT

    fun oldPeerResponse(): JSONObject = JSONObject()
        .put("ok", false)
        .put("error", OLD_PEER_MESSAGE)
        .put("protocol", CURRENT)
}
