package com.omegas.dyno.lab

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

class DynoActivity : Activity() {
    private lateinit var runtime: DynoRuntime.Session
    private val hub get() = runtime.hub
    private lateinit var web: WebView
    private val vault get() = runtime.vault
    private val obd get() = runtime.obd
    private lateinit var mp48: Mp48UsbReader
    private var usb: UsbManager? = null
    private var bluetoothPending = false
    private var pendingObdId: String? = null
    private var pendingUsb: UsbDevice? = null
    private var choosingExport = false
    private val usbPermissionAction get() = "$packageName.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != usbPermissionAction) return
            val expected = pendingUsb ?: return
            @Suppress("DEPRECATION")
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            }
            if (device != null && device != expected) return
            pendingUsb = null
            if (device == null || usb?.hasPermission(device) != true ||
                !intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            ) {
                hub.link(Source.MP48, TelemetryHub.OFFLINE, "Acesso USB não autorizado")
                return
            }
            mp48.start(device)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = DynoRuntime.get(applicationContext)
        usb = getSystemService(UsbManager::class.java)
        mp48 = Mp48UsbReader(usb, { state, error -> hub.link(Source.MP48, state, error) }, ::sample)

        val filter = IntentFilter(usbPermissionAction)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbReceiver, filter)
        }

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
        // Restore the last paired OBD, or auto-select the only classic device named OBD.
        restoreObdConnection()
        // Restore MP48 only when Android already granted permission for this USB device.
        mp48.devices().singleOrNull()?.let { device ->
            if (usb?.hasPermission(device) == true) requestMp48()
        }
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) requestMp48()
        web.postDelayed({ if (!isFinishing && !isDestroyed && hasWindowFocus()) showBatteryGuideOnce() }, 1200)
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.postDelayed({
            if (!isFinishing && !isDestroyed && hasWindowFocus()) showBatteryGuideOnce()
        }, 900)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) requestMp48()
    }

    private fun sample(observation: Observation) = runtime.observe(observation)

    private fun restoreObdConnection() {
        if (obd.needsPermission()) return
        val prefs = getSharedPreferences(DynoRuntime.PREFS, MODE_PRIVATE)
        val saved = prefs.getString(DynoRuntime.LAST_OBD, null)
        val paired = runCatching { obd.devices() }.getOrDefault(emptyList())
        // A saved, previously selected adapter must survive Bluetooth being temporarily off.
        val address = saved?.takeIf { android.bluetooth.BluetoothAdapter.checkBluetoothAddress(it) }
            ?: paired.filter { it.name?.contains("OBD", ignoreCase = true) == true }
                .singleOrNull()?.address
        if (address != null) startPersistentObd(address)
    }

    private fun startPersistentObd(id: String): Boolean {
        val prefs = getSharedPreferences(DynoRuntime.PREFS, MODE_PRIVATE)
        val remembered = prefs.getString(DynoRuntime.LAST_OBD, null) == id
        val paired = runCatching { obd.devices().any { it.address == id } }.getOrDefault(false)
        if (!paired && !remembered) {
            hub.link(Source.OBD, TelemetryHub.OFFLINE, "Adaptador OBD não está pareado")
            return false
        }
        getSharedPreferences(DynoRuntime.PREFS, MODE_PRIVATE).edit()
            .putString(DynoRuntime.LAST_OBD, id).apply()
        return try {
            val service = Intent(this, ObdKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            true
        } catch (exception: RuntimeException) {
            hub.link(Source.OBD, TelemetryHub.OFFLINE,
                "O Android impediu a conexão contínua; abra o Dinamômetro e tente novamente")
            false
        }
    }

    private fun batteryIsUnrestricted(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

    private fun openBatteryAuthorization() {
        if (batteryIsUnrestricted()) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"))
        try {
            startActivity(direct)
        } catch (_: RuntimeException) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun showBatteryGuideOnce() {
        if (batteryIsUnrestricted()) return
        val prefs = getSharedPreferences(DynoRuntime.PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(DynoRuntime.BATTERY_PROMPT_SHOWN, false)) return
        prefs.edit().putBoolean(DynoRuntime.BATTERY_PROMPT_SHOWN, true).apply()
        AlertDialog.Builder(this)
            .setTitle("Manter OBD conectado automaticamente")
            .setMessage("Para observar o motor mesmo com outra tela aberta, o Ômegas usa um serviço de conexão contínua. Permita o funcionamento sem restrição de bateria. O Android vai pedir sua autorização.")
            .setPositiveButton("Autorizar") { _, _ -> openBatteryAuthorization() }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun askBluetooth(id: String? = null) {
        if (id != null) pendingObdId = id
        if (bluetoothPending) return
        bluetoothPending = true
        hub.link(Source.OBD, "aguardando permissão")
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != BLUETOOTH_PERMISSION) return
        bluetoothPending = false
        val id = pendingObdId
        pendingObdId = null
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            hub.link(Source.OBD, TelemetryHub.OFFLINE, "Acesso ao Bluetooth não autorizado")
        } else if (id != null) {
            startPersistentObd(id)
        } else {
            hub.link(Source.OBD, TelemetryHub.OFFLINE)
        }
    }

    private fun requestMp48() {
        if (pendingUsb != null) return
        val manager = usb
        val devices = mp48.devices()
        if (manager == null || devices.isEmpty()) {
            hub.link(Source.MP48, TelemetryHub.OFFLINE, "Adaptador USB MP48 não encontrado")
            return
        }
        val device = devices.singleOrNull()
        if (device == null) {
            hub.link(Source.MP48, TelemetryHub.OFFLINE, "Deixe apenas um adaptador MP48 conectado")
            return
        }
        if (manager.hasPermission(device)) {
            mp48.start(device)
            return
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val callback = PendingIntent.getBroadcast(this, 0, Intent(usbPermissionAction).setPackage(packageName), flags)
        pendingUsb = device
        hub.link(Source.MP48, "aguardando permissão")
        try {
            manager.requestPermission(device, callback)
        } catch (exception: RuntimeException) {
            pendingUsb = null
            callback.cancel()
            hub.link(Source.MP48, TelemetryHub.OFFLINE, exception.message ?: "Permissão USB indisponível")
        }
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
        unregisterReceiver(usbReceiver)
        // The OBD connection and vault now outlive the Activity; only USB is UI-owned.
        mp48.close()
        web.removeJavascriptInterface("DynoNative")
        setContentView(android.widget.FrameLayout(this))
        web.destroy()
        super.onDestroy()
    }

    private fun onUi(action: () -> Unit): String {
        runOnUiThread { if (!isDestroyed && !isFinishing) action() }
        return reply(true)
    }

    private fun reply(ok: Boolean, error: String? = null): String =
        JSONObject().put("ok", ok).put("pending", ok).put("error", error ?: UNKNOWN).toString()

    @SuppressLint("MissingPermission")
    private inner class Bridge {
        @JavascriptInterface fun getSnapshot(): String = hub.snapshot(SystemClock.elapsedRealtimeNanos()).toString()
        @JavascriptInterface fun getStatus(): String = hub.status().toString()
        @JavascriptInterface fun getVault(): String = vault.listing

        @JavascriptInterface fun listObdDevices(): String {
            if (obd.needsPermission()) {
                onUi { askBluetooth() }
                return "[]"
            }
            return try {
                JSONArray().apply {
                    obd.devices().forEach { device ->
                        put(JSONObject().put("id", device.address).put("name", device.name ?: UNKNOWN))
                    }
                }.toString()
            } catch (_: SecurityException) {
                "[]"
            }
        }

        @JavascriptInterface fun connectObd(id: String): String {
            if (!android.bluetooth.BluetoothAdapter.checkBluetoothAddress(id)) {
                return reply(false, "Selecione um adaptador OBD pareado")
            }
            if (obd.needsPermission()) return onUi { askBluetooth(id) }
            return if (startPersistentObd(id)) reply(true) else reply(false, "Serviço de conexão OBD indisponível")
        }

        @JavascriptInterface fun connectMp48(): String = onUi { requestMp48() }
        @JavascriptInterface fun batteryStatus(): String = JSONObject()
            .put("unrestricted", batteryIsUnrestricted()).toString()
        @JavascriptInterface fun requestBatteryOptimization(): String = onUi { openBatteryAuthorization() }

        @JavascriptInterface fun startCapture(): String =
            if (!hub.captureReady(SystemClock.elapsedRealtimeNanos())) {
                reply(false, "Captura bloqueada: aguarde leitura real de RPM do OBD ou MP48")
            } else if (vault.start()) reply(true) else reply(false, "Captura já solicitada")

        @JavascriptInterface fun stopCapture(): String =
            if (vault.stop()) reply(true) else reply(false, "Nenhuma captura ativa")

        @JavascriptInterface fun exportVault(): String = onUi { chooseExport() }
    }

    private companion object {
        const val DASHBOARD = "file:///android_asset/dashboard.html"
        const val BLUETOOTH_PERMISSION = 1
        const val EXPORT = 2
    }
}
