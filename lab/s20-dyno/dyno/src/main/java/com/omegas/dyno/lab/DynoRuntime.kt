package com.omegas.dyno.lab

import android.content.Context

/** The live session is application-owned; recreating an Activity never closes sockets or the vault. */
internal object DynoRuntime {
    @Volatile private var shared: Session? = null

    fun get(context: Context): Session =
        shared ?: synchronized(this) {
            shared ?: Session(context.applicationContext).also { shared = it }
        }

    internal class Session(context: Context) {
        val hub = TelemetryHub()
        val vault = CaptureVault(context, hub)
        val obd = ObdBluetoothReader(context, { status, error ->
            hub.link(Source.OBD, status, error)
        }, ::observe)

        fun observe(observation: Observation) {
            hub.accept(observation)
            vault.sample(observation)
        }
    }

    const val PREFS = "dyno_connection_preferences"
    const val LAST_OBD = "last_obd_bluetooth_address"
    const val BATTERY_PROMPT_SHOWN = "battery_prompt_shown_v10"
}
