package com.omegas.prohub.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.omegas.prohub.MainActivity
import com.omegas.prohub.R
import com.omegas.prohub.model.HubStatus

class NotificationController(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "omegas_telemetry_native"
        const val NOTIFICATION_ID = 4301
        const val REFINEMENT_CHANNEL_ID = "omegas_refinement"
        const val REFINEMENT_NOTIFICATION_ID = 4302
        /** Extra do Intent: rota da WebView a abrir (o toque no aviso do refino abre o Refino). */
        const val EXTRA_ROUTE = "com.omegas.prohub.ROUTE"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telemetria OMEGAS",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantém a conexão MP48 e a telemetria Android ativas"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val refinement = NotificationChannel(
                REFINEMENT_CHANNEL_ID,
                "Calibração OMEGAS",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Avisa quando a curva refinada está pronta, piorou ou ficou estável" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(refinement)
        }
    }

    /** Aviso único por fase do refino; o toque abre o app direto na aba Refino. */
    fun buildRefinementAlert(headline: String, next: String): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            5,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ROUTE, "refino"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, REFINEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_omegas)
            .setContentTitle("OMEGAS — Calibração")
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$headline\n$next"))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    // Os quatro PendingIntents são sempre os mesmos: criados uma vez, não a cada postagem.
    private val openIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private val toggleIntent: PendingIntent by lazy {
        PendingIntent.getService(
            context,
            2,
            Intent(context, TelemetryForegroundService::class.java)
                .setAction(TelemetryForegroundService.ACTION_TOGGLE_ENGINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private val disconnectIntent: PendingIntent by lazy {
        PendingIntent.getService(
            context,
            3,
            Intent(context, TelemetryForegroundService::class.java)
                .setAction(TelemetryForegroundService.ACTION_DISCONNECT_USB),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private val stopIntent: PendingIntent by lazy {
        PendingIntent.getService(
            context,
            4,
            Intent(context, TelemetryForegroundService::class.java)
                .setAction(TelemetryForegroundService.ACTION_STOP_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Tudo que a notificação mostra; o serviço só posta de novo quando isto muda. */
    data class Content(
        val title: String,
        val line1: String,
        val line2: String,
        val pauseLabel: String,
        val usbLabel: String,
    )

    fun content(status: HubStatus): Content {
        val title = when {
            status.engineStuck -> "OMEGAS — NÚCLEO BLOQUEADO"
            status.engineReady -> "OMEGAS — ECU ONLINE"
            status.engineRunning -> "OMEGAS — CONECTANDO À ECU"
            status.usbConnected -> "OMEGAS — MP48 CONECTADO"
            else -> "OMEGAS — AGUARDANDO MP48"
        }
        val line1 = if (status.engineReady) {
            "${status.rpm} RPM • ${status.fuelState} • ${"%.3f".format(status.petrolMs)} ms"
        } else {
            "USB ${if (status.usbConnected) "conectado" else "desconectado"} • núcleo ${if (status.engineRunning) "ativo" else "parado"}"
        }
        val line2 = status.lastError.ifBlank {
            if (status.engineReady) {
                "MAP ${"%.3f".format(status.mapBar)} bar • resposta orientada pela ECU"
            } else {
                "Android nativo • ${status.baudRate} ${status.serialFormat}"
            }
        }.take(140)
        return Content(
            title = title,
            line1 = line1,
            line2 = line2,
            pauseLabel = if (status.engineRunning) "Pausar" else "Retomar",
            usbLabel = if (status.usbConnected) "Desconectar" else "Conectar",
        )
    }

    fun build(status: HubStatus): Notification = build(content(status))

    fun build(content: Content): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_omegas)
            .setContentTitle(content.title)
            .setContentText(content.line1)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${content.line1}\n${content.line2}"))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, content.pauseLabel, toggleIntent)
            .addAction(0, content.usbLabel, disconnectIntent)
            .addAction(0, "Parar", stopIntent)
            .build()
}
