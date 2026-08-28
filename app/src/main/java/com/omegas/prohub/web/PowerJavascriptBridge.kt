package com.omegas.prohub.web

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import com.omegas.prohub.MainActivity
import org.json.JSONObject

/**
 * Ponte somente para estado/ações de energia e overlay controladas pelo Android.
 * Não possui acesso a writers, protocolo MP48 ou calibração.
 */
class PowerJavascriptBridge(activity: MainActivity) {
    private val activityRef = java.lang.ref.WeakReference(activity)

    @JavascriptInterface
    fun getBatteryOptimizationStatus(): String =
        activityRef.get()?.batteryOptimizationStatusJson() ?: "{}"

    @JavascriptInterface
    fun requestBatteryOptimizationExemption(): String =
        activityRef.get()?.requestBatteryOptimizationExemption(manual = true) ?: "{}"

    @JavascriptInterface
    fun getOverlayStatus(): String {
        val activity = activityRef.get() ?: return JSONObject().put("ok", false).put("error", "Activity indisponível").toString()
        val service = activity.serviceOrNull()
        val status = try { JSONObject(service?.overlayStatusJson() ?: "{}") } catch (_: Exception) { JSONObject() }
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(activity)
        return status
            .put("ok", true)
            .put("supported", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            .put("permissionGranted", permission)
            .toString()
    }

    @JavascriptInterface
    fun requestOverlayPermissionAndEnable(): String {
        val activity = activityRef.get() ?: return JSONObject().put("ok", false).put("error", "Activity indisponível").toString()
        val service = activity.serviceOrNull()
            ?: return JSONObject().put("ok", false).put("error", "Serviço ainda iniciando").toString()

        // Marca a intenção antes de abrir o Android. Se a permissão já existe,
        // o mesmo serviço mostra o overlay imediatamente.
        val requested = try { JSONObject(service.setTelemetryOverlayEnabled(true)) } catch (error: Exception) {
            return JSONObject().put("ok", false).put("error", error.message ?: "Falha ao preparar flutuante").toString()
        }
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(activity)
        if (granted) return requested.put("permissionGranted", true).toString()

        return try {
            activity.runOnUiThread {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}"),
                    ),
                )
            }
            requested
                .put("ok", true)
                .put("permissionRequired", true)
                .put("permissionGranted", false)
                .put("launched", true)
                .toString()
        } catch (error: Exception) {
            JSONObject()
                .put("ok", false)
                .put("permissionRequired", true)
                .put("error", error.message ?: "Não foi possível abrir a autorização do flutuante")
                .toString()
        }
    }

    @JavascriptInterface
    fun setOverlayEnabled(enabled: Boolean): String {
        val activity = activityRef.get() ?: return JSONObject().put("ok", false).put("error", "Activity indisponível").toString()
        val service = activity.serviceOrNull()
            ?: return JSONObject().put("ok", false).put("error", "Serviço ainda iniciando").toString()
        return service.setTelemetryOverlayEnabled(enabled)
    }
}
