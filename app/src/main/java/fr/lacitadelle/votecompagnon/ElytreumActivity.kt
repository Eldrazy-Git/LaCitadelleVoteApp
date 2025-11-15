package fr.lacitadelle.votecompagnon

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager

class ElytreumActivity : ComponentActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        // ✅ Active edge-to-edge et gère les insets
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_elytreum)

        webView = findViewById(R.id.elytreumWebView)

        // ✅ Gère le padding pour éviter chevauchement barre notif/navigation
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, sys.top, v.paddingRight, sys.bottom)
            insets
        }

        // ✅ Réglages WebView identiques à Rankup
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webChromeClient = WebChromeClient()

        webView = findViewById(R.id.elytreumWebView)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        // Laisser l'overscroll/stretch natif (rebon) quand il y a du contenu
        webView.overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useMedieval = prefs.getBoolean("pref_custom_font", true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!useMedieval) {
                    view.evaluateJavascript("document.body.classList.add('no-medieval');", null)
                }
            }
        }

        // ✅ Charge la page Elytreum
        webView.loadUrl("file:///android_asset/elytreum/elytreum.html")
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useMedieval = prefs.getBoolean("pref_custom_font", true)
        if (!::webView.isInitialized) return
        if (!useMedieval) {
            webView.evaluateJavascript("document.body.classList.add('no-medieval');", null)
        } else {
            webView.evaluateJavascript("document.body.classList.remove('no-medieval');", null)
        }
    }
}
