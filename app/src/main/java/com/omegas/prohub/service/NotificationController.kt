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
        }
    }

    fun build(status: HubStatus): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, TelemetryForegroundService::class.java)
                .setAction(TelemetryForegroundService.ACTION_TOGGLE_ENGINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = PendingIntent.getService(
            context,
            3,
            Intent(context, TelemetryForegroundService::class.java)
                .setAction(TelemetryForegroundService.ACTION_DISCONNECT_USB),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            4,
            Intent(context, TelemetryForegroundService::class.java)
                .setAction(TelemetryForegroundService.ACTION_STOP_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_omegas)
            .setContentTitle(title)
            .setContentText(line1)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$line1\n$line2"))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (status.engineRunning) "Pausar" else "Retomar", toggleIntent)
            .addAction(0, if (status.usbConnected) "Desconectar" else "Conectar", disconnectIntent)
            .addAction(0, "Parar", stopIntent)
            .build()
    }
}

