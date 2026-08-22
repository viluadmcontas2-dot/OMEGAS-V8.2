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
 */
class TelemetryOverlayController(private val context: Context) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("power_policy", Context.MODE_PRIVATE)
    private var root: LinearLayout? = null
    private var details: LinearLayout? = null
    private var cellText: TextView? = null
    private var stftText: TextView? = null
    private var petrolText: TextView? = null
    private var rpmText: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var showPending = false
    private var lastDrawAt = 0L
    @Volatile private var lastSnapshot = Snapshot()

    data class Snapshot(
        val cell: String = "—",
        val stft: Double? = null,
        val petrolMs: Double? = null,
        val rpm: Double? = null,
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
        .put("observationalOnly", true)

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
        if (now - lastDrawAt < 250L) return
        lastDrawAt = now
        main.post { render(lastSnapshot) }
    }

    private fun show() {
        if (root != null || showPending || !permissionGranted()) return
        showPending = true
        main.post {
            try {
                if (root != null || !permissionGranted() || !requestedEnabled()) return@post
                val panel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded(0xEB0B111CL.toInt(), 16f, 0x554F8EF7)
                    elevation = dp(8).toFloat()
                }
                val button = TextView(context).apply {
                    text = "Ω"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    minWidth = dp(56)
                    minHeight = dp(56)
                    contentDescription = "OMEGAS telemetria flutuante"
                }
                val data = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                    setPadding(dp(4), dp(4), dp(4), dp(6))
                }
                cellText = metric("CÉLULA  —").also(data::addView)
                stftText = metric("STFT  —").also(data::addView)
                petrolText = metric("PETROL  —").also(data::addView)
                rpmText = metric("RPM  —").also(data::addView)
                panel.addView(button)
                panel.addView(data)
                details = data

                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = dp(12)
                    y = dp(160)
                }
                params = lp
                installDrag(panel, button)
                windowManager.addView(panel, lp)
                root = panel
                render(lastSnapshot)
            } catch (_: Exception) {
                root = null
                params = null
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
                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) moved = true
                    lp.x = (startX - dx.toInt()).coerceAtLeast(0)
                    lp.y = (startY + dy.toInt()).coerceAtLeast(0)
                    try { windowManager.updateViewLayout(panel, lp) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_UP && !moved) clickTarget.performClick()
                    true
                }
                else -> false
            }
        }
        clickTarget.setOnClickListener {
            expanded = !expanded
            details?.visibility = if (expanded) View.VISIBLE else View.GONE
            params?.let { lp -> try { root?.let { windowManager.updateViewLayout(it, lp) } } catch (_: Exception) {} }
        }
    }

    private fun render(snapshot: Snapshot) {
        cellText?.text = "CÉLULA  ${snapshot.cell}"
        stftText?.text = "STFT  ${snapshot.stft?.let { signed(it) } ?: "—"}"
        petrolText?.text = "PETROL  ${snapshot.petrolMs?.let { "%.2f ms".format(it) } ?: "—"}"
        rpmText?.text = "RPM  ${snapshot.rpm?.let { "%.0f".format(it) } ?: "—"}"
    }

    @Suppress("DEPRECATION")
    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun metric(value: String): TextView = TextView(context).apply {
        text = value
        textSize = 13f
        setTextColor(0xFFE4EAF3.toInt())
        setPadding(dp(8), dp(4), dp(8), dp(4))
        gravity = Gravity.START
    }

    private fun signed(value: Double): String = (if (value > 0) "+" else "") + "%.1f%%".format(value)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private fun rounded(fill: Int, radiusDp: Float, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
        setStroke(dp(1), stroke)
    }

    override fun close() {
        hide()
        cellText = null
        stftText = null
        petrolText = null
        rpmText = null
        details = null
    }
}
