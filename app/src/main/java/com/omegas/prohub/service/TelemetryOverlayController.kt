package com.omegas.prohub.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Flutuante estritamente observacional.
 * Não possui referência a writer, USB, KMap ou KFactor.
 */
class TelemetryOverlayController(private val context: Context) : AutoCloseable {
    companion object {
        const val MIN_SCALE = 1.0f
        const val MAX_SCALE = 1.8f
        const val DEFAULT_SCALE = 1.25f
    }

    private val main = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("power_policy", Context.MODE_PRIVATE)
    private var root: LinearLayout? = null
    private var details: LinearLayout? = null
    private var fuelChip: TextView? = null
    private var liveDot: TextView? = null
    private var rpmText: TextView? = null
    private var petrolText: TextView? = null
    private var mapText: TextView? = null
    private var gasText: TextView? = null
    private var gasBlock: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = true
    private var showPending = false
    private var lastDrawAt = 0L
    @Volatile private var closed = false
    private val showEpoch = AtomicLong(0L)
    @Volatile private var lastSnapshot = Snapshot()

    /**
     * Valores desconhecidos são null (o balão mostra "—", nunca 0). [live] = telemetria fresca; sem ela
     * os números ficam "—" e o ponto de frescor fica apagado.
     */
    data class Snapshot(
        val cell: String = "—",
        val stft: Double? = null,
        val petrolMs: Double? = null,
        val rpm: Double? = null,
        val fuel: String? = null,
        val mapBar: Double? = null,
        val gasMs: Double? = null,
        val live: Boolean = false,
    )

    /** Tamanho do balão: 1,0 pequeno · 1,25 médio (padrão) · 1,6 grande. */
    fun scale(): Float = prefs.getFloat("telemetry_overlay_scale", DEFAULT_SCALE).coerceIn(MIN_SCALE, MAX_SCALE)

    fun setScale(value: Double): JSONObject {
        if (closed) return statusJson().put("ok", false)
        prefs.edit().putFloat("telemetry_overlay_scale", value.toFloat().coerceIn(MIN_SCALE, MAX_SCALE)).apply()
        if (visible()) { hide(); show() }
        return statusJson().put("ok", true)
    }

    fun permissionGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    fun requestedEnabled(): Boolean = prefs.getBoolean("telemetry_overlay_enabled", false)
    fun visible(): Boolean = root != null

    fun statusJson(): JSONObject = JSONObject()
        .put("supported", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        .put("permissionGranted", permissionGranted())
        .put("requestedEnabled", requestedEnabled())
        .put("visible", visible())
        .put("showPending", showPending)
        .put("scale", scale().toDouble())
        .put("closed", closed)
        .put("observationalOnly", true)

    /** O balão nunca cobre o próprio OMEGAS: some com o app na tela e volta ao sair dele. */
    @Volatile private var appForeground = false

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
        if (foreground) hide() else restoreIfAllowed()
    }

    fun restoreIfAllowed() {
        if (!closed && requestedEnabled() && permissionGranted()) show()
    }

    fun setEnabled(enabled: Boolean): JSONObject {
        if (closed) return statusJson().put("ok", false)
        prefs.edit().putBoolean("telemetry_overlay_enabled", enabled).apply()
        if (!enabled) {
            hide()
            return statusJson().put("ok", true)
        }
        if (!permissionGranted()) return statusJson().put("ok", false).put("permissionRequired", true)
        show() // com o OMEGAS na tela, show() espera o app sair (appForeground)
        return statusJson().put("ok", true)
    }

    /**
     * `true` só se o balão está na tela E já passou o intervalo de 250 ms desde o último desenho.
     * O serviço consulta isto ANTES de montar o status (que não é barato) para não gastar à toa.
     */
    fun wantsUpdate(): Boolean =
        !closed && visible() && SystemClock.elapsedRealtime() - lastDrawAt >= 250L

    fun update(snapshot: Snapshot) {
        if (closed) return
        lastSnapshot = snapshot
        if (!visible()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDrawAt < 250L) return
        lastDrawAt = now
        main.post { if (!closed) render(lastSnapshot) }
    }

    private fun show() {
        if (closed || appForeground || root != null || showPending || !permissionGranted()) return
        val epoch = showEpoch.get()
        showPending = true
        main.post {
            try {
                if (closed || epoch != showEpoch.get() || root != null || !permissionGranted() || !requestedEnabled()) return@post
                val k = scale()
                val panel = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14 * k), dp(10 * k), dp(14 * k), dp(12 * k))
                    background = rounded(0xF2090E18L.toInt(), 18f * k, 0x884F8EF7.toInt())
                    elevation = dp(10).toFloat()
                    minimumWidth = dp(150 * k)
                }
                // Cabeçalho: Ω (toque abre/fecha), combustível e ponto de frescor da telemetria.
                val header = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val button = TextView(context).apply {
                    text = "Ω"
                    textSize = 24f * k
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    minWidth = dp(48)
                    minHeight = dp(48)
                    setPadding(0, 0, dp(10 * k), 0)
                    contentDescription = "OMEGAS telemetria flutuante: toque para abrir ou fechar"
                }
                val chip = TextView(context).apply {
                    text = "—"
                    textSize = 12f * k
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    setPadding(dp(10 * k), dp(3 * k), dp(10 * k), dp(3 * k))
                    background = rounded(0xFF3A4458.toInt(), 10f * k, 0x00000000)
                }
                val dot = TextView(context).apply {
                    text = "●"
                    textSize = 14f * k
                    setTextColor(0xFF59627A.toInt())
                    setPadding(dp(10 * k), 0, 0, 0)
                    contentDescription = "Frescor da telemetria"
                }
                header.addView(button)
                header.addView(chip)
                header.addView(dot)
                val rpmBlock = metricBlock("RPM", 30f * k, 11f * k)
                val data = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (expanded) View.VISIBLE else View.GONE
                }
                val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                val petrolBlock = metricBlock("PETROL INJ.", 26f * k, 11f * k)
                row1.addView(petrolBlock.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                val mapBlock = metricBlock("MAP", 20f * k, 11f * k)
                val gasBlockPair = metricBlock("GÁS", 20f * k, 11f * k)
                val row2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                row2.addView(mapBlock.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row2.addView(gasBlockPair.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                data.addView(row1)
                data.addView(row2)
                panel.addView(header)
                panel.addView(rpmBlock.first)
                panel.addView(data)
                details = data
                fuelChip = chip
                liveDot = dot
                rpmText = rpmBlock.second
                petrolText = petrolBlock.second
                mapText = mapBlock.second
                gasText = gasBlockPair.second
                gasBlock = gasBlockPair.first

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
                if (closed || epoch != showEpoch.get() || !requestedEnabled()) return@post
                windowManager.addView(panel, lp)
                if (closed || epoch != showEpoch.get() || !requestedEnabled()) {
                    try { windowManager.removeView(panel) } catch (_: Exception) {}
                    return@post
                }
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
        showEpoch.incrementAndGet()
        showPending = false
        val view = root
        root = null
        main.post {
            if (view != null) {
                try { windowManager.removeView(view) } catch (_: Exception) {}
            }
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
        val live = snapshot.live
        rpmText?.text = if (live) snapshot.rpm?.let { "%,.0f".format(java.util.Locale.forLanguageTag("pt-BR"), it) } ?: "—" else "—"
        petrolText?.text = if (live) snapshot.petrolMs?.let { "%.2f ms".format(java.util.Locale.forLanguageTag("pt-BR"), it) } ?: "—" else "—"
        mapText?.text = if (live) snapshot.mapBar?.let { "%.2f bar".format(java.util.Locale.forLanguageTag("pt-BR"), it) } ?: "—" else "—"
        val onGas = live && snapshot.fuel == "GNV"
        gasText?.text = if (onGas) snapshot.gasMs?.let { "%.2f ms".format(java.util.Locale.forLanguageTag("pt-BR"), it) } ?: "—" else "—"
        gasBlock?.alpha = if (onGas) 1f else 0.45f
        val label = if (live) snapshot.fuel ?: "—" else "SEM DADO"
        fuelChip?.text = label
        fuelChip?.background = rounded(
            when (label) { "GNV" -> 0xFF168A6B.toInt(); "GASOLINA" -> 0xFFB7791F.toInt(); else -> 0xFF3A4458.toInt() },
            10f * scale(), 0x00000000,
        )
        liveDot?.setTextColor(if (live) 0xFF3DDC97.toInt() else 0xFF59627A.toInt())
    }

    @Suppress("DEPRECATION")
    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    /** Rótulo pequeno em cima, número grande embaixo. Devolve (bloco, texto do número). */
    private fun metricBlock(label: String, valueSp: Float, labelSp: Float): Pair<LinearLayout, TextView> {
        val value = TextView(context).apply {
            text = "—"
            textSize = valueSp
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFF2F6FC.toInt())
        }
        val caption = TextView(context).apply {
            text = label
            textSize = labelSp
            setTextColor(0xFF93A0B8.toInt())
            letterSpacing = 0.06f
        }
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), dp(10), 0)
            addView(caption)
            addView(value)
        }
        return block to value
    }

    private fun signed(value: Double): String = (if (value > 0) "+" else "") + "%.1f%%".format(value)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private fun rounded(fill: Int, radiusDp: Float, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
        setStroke(dp(1), stroke)
    }

    override fun close() {
        if (closed) return
        closed = true
        hide()
        fuelChip = null
        liveDot = null
        rpmText = null
        petrolText = null
        mapText = null
        gasText = null
        gasBlock = null
        details = null
        params = null
    }
}
