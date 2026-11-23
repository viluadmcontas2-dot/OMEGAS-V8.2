package com.omegas.dyno.lab

import android.content.Context
import android.hardware.usb.UsbManager

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
        val stream = TelemetryStream(context)
        val obd = ObdBluetoothReader(context, { status, error ->
            hub.link(Source.OBD, status, error)
        }, ::observe)
        val mp48 = Mp48UsbReader(context.getSystemService(UsbManager::class.java),
            { status, error -> hub.link(Source.MP48, status, error) }, ::observe)

        @Volatile var autoRecording = true
        private var autoCaptureStarted = false

        @Synchronized fun observe(observation: Observation) {
            hub.accept(observation)
            // Record genuine live sensor data without asking the driver to touch the screen.
            if (autoRecording && !autoCaptureStarted && observation.values["rpm"] is Number) {
                if (vault.start()) autoCaptureStarted = true
            }
            vault.sample(observation)
            stream.record(observation)
        }
    }

    const val PREFS = "dyno_connection_preferences"
    const val LAST_OBD = "last_obd_bluetooth_address"
    const val BATTERY_PROMPT_SHOWN = "battery_prompt_shown_v10"
}
