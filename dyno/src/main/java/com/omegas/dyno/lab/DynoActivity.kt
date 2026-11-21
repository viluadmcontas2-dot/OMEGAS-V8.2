package com.omegas.dyno.lab

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class DynoActivity : Activity() {
    private val hub = TelemetryHub()
    private lateinit var web: WebView
    private lateinit var vault: CaptureVault
    private var choosingExport = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = CaptureVault(applicationContext, hub)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !request.isForMainFrame || request.url.toString() != DASHBOARD
            }
            addJavascriptInterface(Bridge(), "DynoNative")
        }
        setContentView(web)
        web.loadUrl(DASHBOARD)
    }

    @Suppress("DEPRECATION")
    private fun chooseExport() {
        if (choosingExport) return
        choosingExport = true
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "dinamometro-omegas-${System.currentTimeMillis()}.zip")
            }, EXPORT)
        } catch (exception: RuntimeException) {
            choosingExport = false
            hub.vaultError(exception.message ?: "Seletor de arquivos indisponível")
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT) {
            choosingExport = false
            if (resultCode == RESULT_OK) data?.data?.let(vault::export)
        }
    }

    override fun onDestroy() {
        web.removeJavascriptInterface("DynoNative")
        setContentView(android.widget.FrameLayout(this))
        web.destroy()
        vault.close()
        super.onDestroy()
    }

    private fun onUi(action: () -> Unit): String {
        runOnUiThread { if (!isDestroyed && !isFinishing) action() }
        return reply(true)
    }

    private fun reply(ok: Boolean, error: String? = null): String =
        JSONObject().put("ok", ok).put("pending", ok).put("error", error ?: UNKNOWN).toString()

    private fun passiveOnly(): String =
        JSONObject().put("ok", false).put("error", "Somente recepção passiva; sem emitir comandos").toString()

    private inner class Bridge {
        @JavascriptInterface fun getSnapshot(): String = hub.snapshot(SystemClock.elapsedRealtimeNanos()).toString()
        @JavascriptInterface fun getStatus(): String = hub.status().toString()
        @JavascriptInterface fun getVault(): String = vault.listing

        @JavascriptInterface fun listObdDevices(): String = "[]"

        @Suppress("UNUSED_PARAMETER")
        @JavascriptInterface fun connectObd(id: String): String = passiveOnly()

        @JavascriptInterface fun connectMp48(): String = passiveOnly()

        @JavascriptInterface fun startCapture(): String =
            if (vault.start()) reply(true) else reply(false, "Captura já solicitada")

        @JavascriptInterface fun stopCapture(): String =
            if (vault.stop()) reply(true) else reply(false, "Nenhuma captura ativa")

        @JavascriptInterface fun exportVault(): String = onUi { chooseExport() }
    }

    private companion object {
        const val DASHBOARD = "file:///android_asset/dashboard.html"
        const val EXPORT = 2
    }
}
