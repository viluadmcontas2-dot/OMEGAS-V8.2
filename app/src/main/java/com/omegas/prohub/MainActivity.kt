package com.omegas.prohub

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.omegas.prohub.service.TelemetryForegroundService
import com.omegas.prohub.web.HubJavascriptBridge
import com.omegas.prohub.web.PowerJavascriptBridge
import com.omegas.prohub.web.CalibrationJavascriptBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        /** Única origem que a WebView abre: a UI empacotada no APK (assets são lidos mesmo com allowFileAccess=false). */
        const val APP_UI_ORIGIN = "file:///android_asset/ui/"

        /** Só a UI do próprio APK: nada de arquivos do aparelho, rede ou outro esquema; sem "../" para sair da pasta. */
        fun isAppUiUrl(url: String?): Boolean {
            val value = url ?: return false
            return value.startsWith(APP_UI_ORIGIN) && !value.contains("..") && !value.contains("%2e%2e", ignoreCase = true)
        }
    }

    /** Exportações em segundo plano da tela; canceladas quando a tela é destruída (nada fica solto). */
    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var webView: WebView
    private var service: TelemetryForegroundService? = null
    private var bound = false
    private var pendingSessionExportId = ""
    private var pendingSummaryExportId = ""
    private var jsBridge: HubJavascriptBridge? = null
    private var calibrationBridge: CalibrationJavascriptBridge? = null
    private var powerBridge: PowerJavascriptBridge? = null

    private val exportDataLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runWithServiceAsync { svc ->
            try {
                svc.archives.exportData(contentResolver, uri, svc.learningArchive.snapshotJson())
                toast("Dados exportados")
            } catch (error: Exception) {
                toast("Falha ao exportar: ${error.message}", true)
            }
        }
    }

    private val exportLogsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runWithServiceAsync { svc ->
            try {
                svc.archives.exportLogs(contentResolver, uri, svc.log.text())
                toast("Logs exportados")
            } catch (error: Exception) {
                toast("Falha ao exportar logs: ${error.message}", true)
            }
        }
    }

    private val exportSessionLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runWithServiceAsync { svc ->
            val result = JSONObject(svc.exportSession(uri, pendingSessionExportId))
            pendingSessionExportId = ""
            toast(
                if (result.optBoolean("ok")) "Sessão exportada" else "Falha: ${result.optString("error")}",
                !result.optBoolean("ok"),
            )
            refreshWebUi()
        }
    }

    /** Pacote leve da sessão (resumo + série de 1 Hz + manifesto + livro-caixa), separado do bruto. */
    private val exportSummaryLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runWithServiceAsync { svc ->
            val result = JSONObject(svc.exportSessionSummary(uri, pendingSummaryExportId))
            pendingSummaryExportId = ""
            val kb = (result.optLong("bytes", 0L) + 1023L) / 1024L
            toast(
                if (result.optBoolean("ok")) "Resumo exportado ($kb KB)" else "Falha: ${result.optString("error")}",
                !result.optBoolean("ok"),
            )
            refreshWebUi()
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val legacyStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) runWithService { it.sessionRecorder.recoverDocumentsMirrorAsync() }
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        runWithService { svc ->
            val result = svc.setGpsEnabled(granted)
            toast(
                if (granted && result.optBoolean("ok")) "GPS ativado" else "GPS não ativado",
                !granted || !result.optBoolean("ok"),
            )
            refreshWebUi()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as TelemetryForegroundService.LocalBinder).service()
            bound = true
            refreshWebUi()
            // App aberto: o balão flutuante não pode cobrir os botões do próprio OMEGAS.
            if (activityResumed) {
                service?.takeIf { it.overlayReady() }?.overlay?.setAppForeground(true)
            }
            if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                service?.connectUsb()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
            refreshWebUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("crash_logs", Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        if (lastCrash != null) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Crash Detectado")
                .setMessage(lastCrash)
                .setPositiveButton("Copiar e Fechar") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash", lastCrash))
                    prefs.edit().remove("last_crash").apply()
                }
                .setCancelable(false)
                .show()
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val sw = java.io.StringWriter()
            exception.printStackTrace(java.io.PrintWriter(sw))
            prefs.edit().putString("last_crash", sw.toString()).commit()
            defaultHandler?.uncaughtException(thread, exception)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        applySystemInsets()
        requestNotificationPermission()
        startHubService()
        requestLegacyStoragePermission()
        configureWebView()
        webView.post { maybePromptBatteryOptimization() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    /** Tela do OMEGAS visível (entre onResume e onPause). */
    private var activityResumed = false

    override fun onResume() {
        super.onResume()
        activityResumed = true
        refreshWebUi()
        serviceOrNull()?.let { if (it.overlayReady()) it.overlay.setAppForeground(true) }
    }

    override fun onPause() {
        activityResumed = false
        serviceOrNull()?.let { if (it.overlayReady()) it.overlay.setAppForeground(false) }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRouteFromIntent(intent)
        if (intent.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            runWithService { it.connectUsb() }
        }
    }

    /** Abre a rota pedida pelo Intent (aviso do refino → aba Refino). Só rotas simples. */
    private fun openRouteFromIntent(intent: Intent?) {
        val route = intent?.getStringExtra(com.omegas.prohub.service.NotificationController.EXTRA_ROUTE) ?: return
        if (!route.matches(Regex("[a-z]{2,24}")) || !::webView.isInitialized) return
        intent.removeExtra(com.omegas.prohub.service.NotificationController.EXTRA_ROUTE)
        webView.evaluateJavascript("window.OmegasApp&&OmegasApp.router&&OmegasApp.router.navigate('$route')", null)
    }

    override fun onDestroy() {
        screenScope.cancel()
        if (bound) {
            try { unbindService(connection) } catch (_: Exception) { /* serviço já desligado: nada a soltar */ }
            bound = false
        }
        jsBridge?.destroy()
        jsBridge = null
        calibrationBridge?.destroy()
        calibrationBridge = null
        powerBridge = null
        if (::webView.isInitialized) {
            // Tela fechando: falha ao soltar a WebView não afeta o carro nem os dados.
            try { webView.removeJavascriptInterface("OmegasNative") } catch (_: Exception) { /* tela fechando */ }
            try { webView.removeJavascriptInterface("OmegasCalibration") } catch (_: Exception) { /* tela fechando */ }
            try { webView.removeJavascriptInterface("OmegasPower") } catch (_: Exception) { /* tela fechando */ }
            try { webView.destroy() } catch (_: Exception) { /* tela fechando */ }
        }
        super.onDestroy()
    }

    private fun applySystemInsets() {
        val root = findViewById<android.view.View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        root.requestApplyInsets()
    }

    private fun startHubService() {
        val serviceIntent = Intent(this, TelemetryForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView = findViewById(R.id.hubWebView)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            // Assets (file:///android_asset/) continuam legíveis; o resto do disco do aparelho não.
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
        }
        jsBridge = HubJavascriptBridge(this)
        calibrationBridge = CalibrationJavascriptBridge(this)
        powerBridge = PowerJavascriptBridge(this)
        webView.addJavascriptInterface(jsBridge!!, "OmegasNative")
        webView.addJavascriptInterface(calibrationBridge!!, "OmegasCalibration")
        webView.addJavascriptInterface(powerBridge!!, "OmegasPower")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    service?.log?.add("DEBUG", "WEB", "${it.message()} @${it.lineNumber()}")
                }
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Notificação do refino abriu o app a frio: navega depois que a UI montar.
                webView.postDelayed({ openRouteFromIntent(intent) }, 800L)
            }
            // Navegar para fora da UI empacotada (link externo, outro arquivo): bloqueado.
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                !isAppUiUrl(request?.url?.toString())

            // Recurso de fora (rede, arquivo do aparelho, content://) nunca carrega; data:/blob: não passam por aqui.
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (isAppUiUrl(request?.url?.toString())) return null
                return WebResourceResponse("text/plain", "utf-8", 403, "Bloqueado", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
        }
        webView.loadUrl("file:///android_asset/ui/index.html")
    }

    fun exportData() = runOnUiThread {
        exportDataLauncher.launch("OMEGAS_Dados_${exportStamp()}.zip")
    }

    fun exportLogs() = runOnUiThread {
        exportLogsLauncher.launch("OMEGAS_Logs_${exportStamp()}.txt")
    }

    fun exportSession(sessionId: String) = runOnUiThread {
        pendingSessionExportId = sessionId.trim()
        exportSessionLauncher.launch("OMEGAS_Sessao_${sessionStamp(pendingSessionExportId)}.zip")
    }

    fun exportSessionSummary(sessionId: String) = runOnUiThread {
        pendingSummaryExportId = sessionId.trim()
        exportSummaryLauncher.launch("OMEGAS_Resumo_${sessionStamp(pendingSummaryExportId)}.zip")
    }

    private fun exportStamp(timeMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(timeMs))

    private fun sessionStamp(sessionId: String): String {
        val match = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})[_-]([0-9]{2})-([0-9]{2})").find(sessionId)
        return if (match != null) {
            val p = match.groupValues
            "${p[1]}-${p[2]}-${p[3]}_${p[4]}-${p[5]}"
        } else exportStamp()
    }

    fun setGpsEnabled(enabled: Boolean) = runOnUiThread {
        if (!enabled) {
            runWithService { it.setGpsEnabled(false) }
            return@runOnUiThread
        }
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (fine || coarse) runWithService { it.setGpsEnabled(true) }
        else locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    fun batteryOptimizationStatusJson(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return JSONObject()
                .put("supported", false)
                .put("ignoringOptimizations", true)
                .put("promptedAutomatically", true)
                .toString()
        }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val prompted = getSharedPreferences("power_policy", Context.MODE_PRIVATE)
            .getBoolean("battery_optimization_prompted_v1", false)
        return JSONObject()
            .put("supported", true)
            .put("ignoringOptimizations", power.isIgnoringBatteryOptimizations(packageName))
            .put("promptedAutomatically", prompted)
            .put("action", Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .toString()
    }

    fun requestBatteryOptimizationExemption(manual: Boolean = true): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return JSONObject().put("ok", true).put("supported", false).put("alreadyAllowed", true).toString()
        }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            return JSONObject().put("ok", true).put("supported", true).put("alreadyAllowed", true).toString()
        }
        return try {
            getSharedPreferences("power_policy", Context.MODE_PRIVATE)
                .edit().putBoolean("battery_optimization_prompted_v1", true).apply()
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
            JSONObject()
                .put("ok", true)
                .put("supported", true)
                .put("launched", true)
                .put("manual", manual)
                .toString()
        } catch (error: Exception) {
            JSONObject()
                .put("ok", false)
                .put("supported", true)
                .put("error", error.message ?: "Não foi possível abrir a autorização de bateria")
                .toString()
        }
    }

    private fun maybePromptBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isFinishing || isDestroyed) return
        val prefs = getSharedPreferences("power_policy", Context.MODE_PRIVATE)
        if (prefs.getBoolean("battery_optimization_prompted_v1", false)) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            prefs.edit().putBoolean("battery_optimization_prompted_v1", true).apply()
            return
        }
        requestBatteryOptimizationExemption(manual = false)
    }

    fun openAppSettings() = runOnUiThread {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    fun serviceOrNull(): TelemetryForegroundService? = service

    fun runWithService(action: (TelemetryForegroundService) -> Unit) = runOnUiThread {
        val current = service
        if (current == null) toast("Serviço ainda iniciando") else action(current)
    }

    fun runWithServiceAsync(action: (TelemetryForegroundService) -> Unit) {
        val current = service
        if (current == null) {
            toast("Serviço ainda iniciando")
            return
        }
        screenScope.launch {
            try {
                action(current)
            } catch (error: Exception) {
                if (isActive) toast("Falha: ${error.message}", true)
            }
        }
    }

    fun refreshWebUi() = runOnUiThread {
        if (::webView.isInitialized) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new Event('omegas-refresh'))",
                null,
            )
        }
    }

    fun toast(message: String, long: Boolean = false) = runOnUiThread {
        Toast.makeText(
            this,
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }

    private fun requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}