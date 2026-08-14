package com.omegas.prohub.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.abs

/**
 * Flutuante estritamente observacional.
 * Não possui referência a writer, USB, KMap ou KFactor.
 *
 * NEXT usa presença 2,5x maior que o legado e persiste apenas preferência
 * visual/posição. Dados adicionais vêm da mesma TelemetryStateStore do serviço;
 * nenhum pipeline, timer ou ciência nasce aqui.
 */
class TelemetryOverlayController(private val context: Context) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("power_policy", Context.MODE_PRIVATE)
    private var root: LinearLayout? = null
    private var details: LinearLayout? = null
    private var compactText: TextView? = null
    private var cellText: TextView? = null
    private var stftText: TextView? = null
    private var petrolText: TextView? = null
    private var gasText: TextView? = null
    private var rpmText: TextView? = null
    private var contextText: TextView? = null
    private var freshnessText: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = prefs.getBoolean(KEY_EXPANDED, false)
    private var showPending = false
    private var lastDrawAt = 0L
    @Volatile private var lastSnapshot = Snapshot()

    data class Snapshot(
        val cell: String = "—",
        val stft: Double? = null,
        val petrolMs: Double? = null,
        val gasMs: Double? = null,
        val rpm: Double? = null,
        val fuel: String = "—",
        val ecuState: String = "—",
        val telemetryAgeMs: Long? = null,
    )

    fun permissionGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    fun requestedEnabled(): Boolean = prefs.getBoolean("telemetry_overlay_enabled", false)
    fun visible(): Boolean = root != null

    fun statusJson(): JSONObject = JSONObject()
        .put("supported", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        .put("permissionGranted", permissionGranted())
        .put("requestedEnabled", requestedEnabled())
        .put("visible", visible())
        .put("showPending", showPending)
        .put("expanded", expanded)
        .put("positionXDp", prefs.getInt(KEY_X_DP, DEFAULT_X_DP))
        .put("positionYDp", prefs.getInt(KEY_Y_DP, DEFAULT_Y_DP))
        .put("positionBounded", true)
        .put("source", "TelemetryStateStore")
        .put("observationalOnly", true)
        .put("visualScaleVsLegacy", VISUAL_SCALE_VS_LEGACY)
        .put("compactSizeDp", COMPACT_SIZE_DP)
        .put("metricTextSp", METRIC_TEXT_SP)
        .put("redrawMinIntervalMs", REDRAW_MIN_INTERVAL_MS)

    fun restoreIfAllowed() {
        if (requestedEnabled() && permissionGranted()) show()
    }

    fun setEnabled(enabled: Boolean): JSONObject {
        prefs.edit().putBoolean("telemetry_overlay_enabled", enabled).apply()
        if (!enabled) {
            hide()
            return statusJson().put("ok", true)
        }
        if (!permissionGranted()) return statusJson().put("ok", false).put("permissionRequired", true)
        show()
        return statusJson().put("ok", true)
    }

    fun update(snapshot: Snapshot) {
        lastSnapshot = snapshot
        if (!visible()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDrawAt < REDRAW_MIN_INTERVAL_MS) return
        lastDrawAt = now
        main.post { render(lastSnapshot) }
    }

    private fun show() {
        if (root != null || showPending || !permissionGranted()) return
        showPending = true
        main.post {
            try {
                if (root != null || !permissionGranted() || !requestedEnabled()) return@post
                expanded = prefs.getBoolean(KEY_EXPANDED, expanded)
                val panel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = rounded(0xF20B111CL.toInt(), 22f, 0x884F8EF7.toInt())
                    elevation = dp(12).toFloat()
                }
                val button = TextView(context).apply {
                    text = "Ω\n— rpm\n—"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    minWidth = dp(COMPACT_SIZE_DP)
                    minHeight = dp(COMPACT_SIZE_DP)
                    setPadding(dp(8), dp(7), dp(8), dp(7))
                    contentDescription = "OMEGAS telemetria flutuante ampliada"
                }
                compactText = button
                val data = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (expanded) View.VISIBLE else View.GONE
                    minimumWidth = dp(EXPANDED_MIN_WIDTH_DP)
                    setPadding(dp(8), dp(8), dp(8), dp(10))
                }
                rpmText = metric("RPM  —", emphasized = true).also(data::addView)
                petrolText = metric("PETROL  —").also(data::addView)
                gasText = metric("GAS  —").also(data::addView)
                cellText = metric("CÉLULA  —").also(data::addView)
                contextText = metric("ECU / COMBUSTÍVEL  —").also(data::addView)
                freshnessText = metric("FRESCOR  —").also(data::addView)
                stftText = metric("STFT  —").also(data::addView)
                panel.addView(button)
                panel.addView(data)
                details = data

                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = dp(restoredXdp(expanded))
                    y = dp(restoredYdp(expanded))
                }
                params = lp
                installDrag(panel, button)
                windowManager.addView(panel, lp)
                root = panel
                render(lastSnapshot)
                panel.post {
                    clampActualPosition(panel, lp)
                    persistPosition(lp)
                    try { windowManager.updateViewLayout(panel, lp) } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                root = null
                params = null
                compactText = null
            } finally {
                showPending = false
            }
        }
    }

    private fun hide() {
        showPending = false
        val view = root ?: return
        root = null
        main.post {
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
    }

    private fun installDrag(panel: View, clickTarget: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        panel.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > dp(7) || abs(dy) > dp(7)) moved = true
                    lp.x = startX - dx.toInt()
                    lp.y = startY + dy.toInt()
                    clampActualPosition(panel, lp)
                    try { windowManager.updateViewLayout(panel, lp) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    clampActualPosition(panel, lp)
                    persistPosition(lp)
                    if (event.actionMasked == MotionEvent.ACTION_UP && !moved) clickTarget.performClick()
                    true
                }
                else -> false
            }
        }
        clickTarget.setOnClickListener {
            expanded = !expanded
            prefs.edit().putBoolean(KEY_EXPANDED, expanded).apply()
            details?.visibility = if (expanded) View.VISIBLE else View.GONE
            panel.post {
                params?.let { lp ->
                    clampActualPosition(panel, lp)
                    persistPosition(lp)
                    try { windowManager.updateViewLayout(panel, lp) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun render(snapshot: Snapshot) {
        val display = enrichFromCentralTelemetry(snapshot)
        val rpm = display.rpm?.let { "%.0f".format(it) } ?: "—"
        val fuel = display.fuel.takeIf { it.isNotBlank() && it != "—" } ?: "—"
        compactText?.text = "Ω\n$rpm rpm\n$fuel"
        rpmText?.text = "RPM      $rpm"
        petrolText?.text = "PETROL   ${display.petrolMs?.let { "%.2f ms".format(it) } ?: "—"}"
        gasText?.text = "GAS      ${display.gasMs?.let { "%.2f ms".format(it) } ?: "—"}"
        cellText?.text = "CÉLULA   ${display.cell}"
        contextText?.text = "ECU      ${display.ecuState.ifBlank { "—" }}  •  $fuel"
        freshnessText?.text = "FRESCOR  ${freshness(display.telemetryAgeMs)}"
        stftText?.text = "STFT     ${display.stft?.let { signed(it) } ?: "—"}"
    }

    /** Completa apenas campos ausentes usando a mesma Store central do serviço. */
    private fun enrichFromCentralTelemetry(snapshot: Snapshot): Snapshot {
        val service = context as? TelemetryForegroundService ?: return snapshot
        val live = try { service.telemetryStore.telemetryCopy() } catch (_: Exception) { JSONObject() }
        val age = try {
            service.telemetryStore.ageMs().takeIf { it != Long.MAX_VALUE }
        } catch (_: Exception) {
            null
        }
        return snapshot.copy(
            petrolMs = snapshot.petrolMs ?: live.nullableDouble("petrol_ms"),
            gasMs = snapshot.gasMs ?: live.nullableDouble("gas_ms_diagnostic") ?: live.nullableDouble("gas_ms"),
            rpm = snapshot.rpm ?: live.nullableDouble("rpm"),
            fuel = snapshot.fuel.takeUnless { it.isBlank() || it == "—" }
                ?: live.optString("fuel", live.optString("fuelState", "—")),
            ecuState = snapshot.ecuState.takeUnless { it.isBlank() || it == "—" }
                ?: live.optString("state", "—"),
            telemetryAgeMs = snapshot.telemetryAgeMs ?: age,
        )
    }

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (has(key) && !isNull(key) && opt(key) is Number) optDouble(key) else null

    private fun freshness(ageMs: Long?): String = when {
        ageMs == null || ageMs < 0L -> "—"
        ageMs <= 500L -> "ao vivo"
        ageMs <= 1_500L -> "$ageMs ms"
        else -> "${"%.1f".format(ageMs / 1_000.0)} s"
    }

    private fun clampActualPosition(panel: View, lp: WindowManager.LayoutParams) {
        val metrics = context.resources.displayMetrics
        val panelWidth = panel.width.takeIf { it > 0 } ?: dp(if (expanded) EXPANDED_MIN_WIDTH_DP else COMPACT_SIZE_DP)
        val panelHeight = panel.height.takeIf { it > 0 } ?: dp(if (expanded) EXPANDED_ESTIMATED_HEIGHT_DP else COMPACT_SIZE_DP)
        lp.x = lp.x.coerceIn(0, (metrics.widthPixels - panelWidth).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (metrics.heightPixels - panelHeight).coerceAtLeast(0))
    }

    private fun persistPosition(lp: WindowManager.LayoutParams) {
        prefs.edit()
            .putInt(KEY_X_DP, pxToDp(lp.x).coerceAtLeast(0))
            .putInt(KEY_Y_DP, pxToDp(lp.y).coerceAtLeast(0))
            .putBoolean(KEY_EXPANDED, expanded)
            .apply()
    }

    private fun restoredXdp(expandedNow: Boolean): Int {
        val widthDp = screenWidthDp()
        val footprint = if (expandedNow) EXPANDED_MIN_WIDTH_DP else COMPACT_SIZE_DP
        return prefs.getInt(KEY_X_DP, DEFAULT_X_DP).coerceIn(0, (widthDp - footprint).coerceAtLeast(0))
    }

    private fun restoredYdp(expandedNow: Boolean): Int {
        val heightDp = screenHeightDp()
        val footprint = if (expandedNow) EXPANDED_ESTIMATED_HEIGHT_DP else COMPACT_SIZE_DP
        return prefs.getInt(KEY_Y_DP, DEFAULT_Y_DP).coerceIn(0, (heightDp - footprint).coerceAtLeast(0))
    }

    @Suppress("DEPRECATION")
    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun metric(value: String, emphasized: Boolean = false): TextView = TextView(context).apply {
        text = value
        textSize = if (emphasized) 21f else METRIC_TEXT_SP.toFloat()
        setTextColor(if (emphasized) Color.WHITE else 0xFFE4EAF3.toInt())
        setPadding(dp(12), dp(7), dp(12), dp(7))
        minHeight = dp(40)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        if (emphasized) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun signed(value: Double): String = (if (value > 0) "+" else "") + "%.1f%%".format(value)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private fun pxToDp(value: Int): Int = (value / context.resources.displayMetrics.density).toInt().coerceAtLeast(0)
    private fun screenWidthDp(): Int = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    private fun screenHeightDp(): Int = (context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density).toInt()
    private fun rounded(fill: Int, radiusDp: Float, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
        setStroke(dp(2), stroke)
    }

    override fun close() {
        hide()
        compactText = null
        cellText = null
        stftText = null
        petrolText = null
        gasText = null
        rpmText = null
        contextText = null
        freshnessText = null
        details = null
    }

    companion object {
        const val VISUAL_SCALE_VS_LEGACY = 2.5
        const val COMPACT_SIZE_DP = 105
        const val EXPANDED_MIN_WIDTH_DP = 260
        const val EXPANDED_ESTIMATED_HEIGHT_DP = 410
        const val METRIC_TEXT_SP = 18
        const val REDRAW_MIN_INTERVAL_MS = 250L
        private const val KEY_X_DP = "telemetry_overlay_x_dp"
        private const val KEY_Y_DP = "telemetry_overlay_y_dp"
        private const val KEY_EXPANDED = "telemetry_overlay_expanded"
        private const val DEFAULT_X_DP = 16
        private const val DEFAULT_Y_DP = 140
    }
}