package fr.lacitadelle.votecompagnon

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.preference.PreferenceManager


class RankupActivity : ComponentActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rankup)

        webView = findViewById(R.id.rankupWebView)

        // 🔧 réglages importants pour que localStorage fonctionne dans une WebView
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true              // <— indispensable pour localStorage
        settings.databaseEnabled = true                // old webview compat
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        // si jamais tu as d’autres imports HTML locaux
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webChromeClient = WebChromeClient()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useMedieval = prefs.getBoolean("pref_custom_font", true)

        webView = findViewById(R.id.rankupWebView)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        // Laisser l'overscroll/stretch natif (rebon) quand il y a du contenu
        webView.overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // on applique ton switch de police
                if (!useMedieval) {
                    view.evaluateJavascript(
                        "document.body.classList.add('no-medieval');",
                        null
                    )
                }
            }
        }

        // ⚠️ charger APRES avoir configuré le WebView
        webView.loadUrl("file:///android_asset/rankup/rankup.html")
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
