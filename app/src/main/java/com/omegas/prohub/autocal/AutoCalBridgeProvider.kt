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

/** Inicializador interno; não exporta dados nem cria outra Activity. */
class AutoCalBridgeProvider : ContentProvider() {
    private val bridges = WeakHashMap<MainActivity, AutoCalJavascriptBridge>()
    private val attached = WeakHashMap<MainActivity, Boolean>()
    private val cachedScripts = linkedMapOf<String, String>()

    override fun onCreate(): Boolean {
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

    private fun attach(main: MainActivity) {
        val webView = main.findViewById<WebView>(R.id.hubWebView) ?: return
        val bridge = bridges.getOrPut(main) { AutoCalJavascriptBridge(main) }
        val firstAttach = attached.put(main, true) != true
        webView.addJavascriptInterface(bridge, INTERFACE_NAME)

        // addJavascriptInterface fica disponível ao JavaScript no próximo carregamento.
        // A primeira retomada após a WebView existir força somente uma recarga local.
        if (firstAttach && !webView.url.isNullOrBlank()) {
            webView.reload()
        }
        listOf(250L, 700L, 1_400L).forEach { delayMs ->
            webView.postDelayed({ injectLocalUi(webView) }, delayMs)
        }
    }

    private fun injectLocalUi(webView: WebView) {
        injectAsset(webView, "hub/autocal-ui.js", "__omegasAutoCalUi")
        injectAsset(webView, "hub/autocal-draft.js", "__omegasAutoCalDraft")
        injectAsset(webView, "hub/autocal-residual.js", "__omegasAutoCalResidual")
        injectAsset(webView, "hub/autocal-actions.js", "__omegasAutoCalActions")
        // Sempre por último: substitui os handlers anteriores da faixa K depois de
        // qualquer módulo que tenha sido carregado ou reinjetado.
        injectAsset(webView, "hub/kfactor-range.js", "__omegasKFactorQ14Range")
    }

    private fun injectAsset(webView: WebView, asset: String, marker: String) {
        val source = synchronized(cachedScripts) {
            cachedScripts[asset] ?: try {
                context?.assets?.open(asset)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?.also { cachedScripts[asset] = it }
            } catch (_: Exception) {
                null
            }
        } ?: return
        val wrapped = "if(!window.$marker){$source}else{window.$marker.render();}"
        try {
            webView.evaluateJavascript(wrapped, null)
        } catch (_: Exception) {
        }
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
    }
}
