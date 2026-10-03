package com.omegas.prohub.autocal

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import com.omegas.prohub.MainActivity
import com.omegas.prohub.R
import java.util.WeakHashMap

/**
 * Inicializador interno do bridge AutoCal.
 *
 * A UI V8.2 limpa consome `OmegasAutoCal` pelos módulos em `assets/ui`.
 * Não injeta mais os assets legados da pasta `hub` e não cria outra Activity.
 */
class AutoCalBridgeProvider : ContentProvider() {
    private val bridges = WeakHashMap<MainActivity, AutoCalJavascriptBridge>()
    private val attached = WeakHashMap<MainActivity, Boolean>()

    override fun onCreate(): Boolean {
        instance = this
        val application = context?.applicationContext as? Application ?: return false
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                val main = activity as? MainActivity ?: return
                activity.window.decorView.post { attach(main) }
            }

            override fun onActivityResumed(activity: Activity) {
                val main = activity as? MainActivity ?: return
                attach(main)
            }

            override fun onActivityDestroyed(activity: Activity) {
                val main = activity as? MainActivity ?: return
                attached.remove(main)
                bridges.remove(main)?.destroy()
                try {
                    main.findViewById<WebView>(R.id.hubWebView)?.removeJavascriptInterface(INTERFACE_NAME)
                } catch (_: Exception) {
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
        return true
    }

    /**
     * Registra a ponte ANTES do `loadUrl`: a página já nasce com `OmegasAutoCal` e o attach tardio
     * não precisa recarregar a WebView (o que perdia o estado da UI na partida a frio).
     */
    private fun attachBeforeLoad(main: MainActivity) = attach(main)

    private fun attach(main: MainActivity) {
        val webView = main.findViewById<WebView>(R.id.hubWebView) ?: return
        val bridge = bridges.getOrPut(main) { AutoCalJavascriptBridge(main) }
        val firstAttach = attached.put(main, true) != true
        webView.addJavascriptInterface(bridge, INTERFACE_NAME)

        // addJavascriptInterface passa a existir para o JavaScript no próximo carregamento.
        // Uma única recarga local no primeiro attach substitui as antigas reinjeções hub/*.
        // Se o MainActivity já registrou a ponte antes do loadUrl, firstAttach é falso aqui e não recarrega.
        if (firstAttach && !webView.url.isNullOrBlank()) webView.reload()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val INTERFACE_NAME = "OmegasAutoCal"
        @Volatile private var instance: AutoCalBridgeProvider? = null

        /** Chamado pelo MainActivity antes de carregar a página. */
        @JvmStatic
        fun attachBeforeLoad(main: MainActivity) {
            instance?.attachBeforeLoad(main)
        }
    }
}