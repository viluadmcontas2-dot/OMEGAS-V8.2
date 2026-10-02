package com.omegas.prohub.ui

/** Contrato restrito do toque na notificação; não autoriza operação na ECU. */
object NotificationRoute {
    const val EXTRA_UI_ROUTE = "omegas.ui.route"
    const val REFINEMENT = "refino"
    fun fromExtra(value: String?): String? = value?.takeIf { it == REFINEMENT }
}
