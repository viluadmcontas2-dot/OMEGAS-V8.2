package com.omegas.prohub.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import kotlin.math.max

class GpsTelemetryManager(
    private val context: Context,
    private val log: RingLog,
    private val onUpdate: () -> Unit,
) : LocationListener {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastLocation: Location? = null
    private var totalDistanceMeters = 0.0

    @Volatile var running = false
        private set
    @Volatile var lastError = ""
        private set

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun start(intervalMs: Long): Boolean {
        if (running) return true
        if (!hasPermission()) {
            lastError = "Permissão de localização não concedida"
            return false
        }
        return try {
            val safeInterval = intervalMs.coerceIn(5000L, 60_000L)
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, safeInterval, 10f, this)
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, max(5000L, safeInterval), 10f, this)
            }
            running = true
            lastError = ""
            log.add("INFO", "GPS", "GPS iniciado em ${safeInterval}ms")
            onUpdate()
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Falha ao iniciar GPS"
            log.add("WARN", "GPS", lastError)
            false
        }
    }

    fun stop() {
        try { manager.removeUpdates(this) } catch (_: Exception) {}
        if (running) log.add("INFO", "GPS", "GPS encerrado")
        running = false
        onUpdate()
    }

    override fun onLocationChanged(location: Location) {
        lastLocation?.let { previous ->
            val jump = previous.distanceTo(location).toDouble()
            if (jump in 0.0..2_000.0 && location.accuracy <= 100f) totalDistanceMeters += jump
        }
        lastLocation = Location(location)
        lastError = ""
        onUpdate()
    }

    @Deprecated("Deprecated in API 31")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = onUpdate()
    override fun onProviderDisabled(provider: String) = onUpdate()

    fun json(): JSONObject {
        val location = lastLocation
        return JSONObject()
            .put("enabled", running)
            .put("permission", hasPermission())
            .put("provider", location?.provider ?: "--")
            .put("latitude", location?.latitude ?: JSONObject.NULL)
            .put("longitude", location?.longitude ?: JSONObject.NULL)
            .put("speedKmh", if (location?.hasSpeed() == true) location.speed * 3.6 else 0.0)
            .put("accuracyM", location?.accuracy ?: 0f)
            .put("altitudeM", if (location?.hasAltitude() == true) location.altitude else JSONObject.NULL)
            .put("bearing", if (location?.hasBearing() == true) location.bearing else JSONObject.NULL)
            .put("distanceKm", totalDistanceMeters / 1000.0)
            .put("timestamp", location?.time ?: 0L)
            .put("lastError", lastError)
    }
}

