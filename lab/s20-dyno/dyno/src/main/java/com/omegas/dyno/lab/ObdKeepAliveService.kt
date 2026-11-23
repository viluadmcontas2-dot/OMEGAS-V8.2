package com.omegas.dyno.lab

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Persistent read-only OBD ownership, independent of WebView/Activity lifetime.
 * START_STICKY restores the saved paired device after ordinary process recreation.
 * No transport or ECU writes are permitted here.
 */
class ObdKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val savedId = getSharedPreferences(DynoRuntime.PREFS, MODE_PRIVATE)
            .getString(DynoRuntime.LAST_OBD, null)
        if (savedId == null || !android.bluetooth.BluetoothAdapter.checkBluetoothAddress(savedId)) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        try {
            ensureNotification()
            DynoRuntime.get(applicationContext).obd.start(savedId)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Não foi possível manter o serviço OBD em execução", exception)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun ensureNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "OBD Ômegas · conexão contínua", NotificationManager.IMPORTANCE_LOW
        ))
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, DynoActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_dyno)
            .setContentTitle("OBD Ômegas · monitoramento ativo")
            .setContentText("Reconexão Bluetooth automática ativada")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val TAG = "ObdKeepAlive"
        const val CHANNEL = "dyno_obd_persistent_v10"
        const val NOTIFICATION_ID = 3102
    }
}
