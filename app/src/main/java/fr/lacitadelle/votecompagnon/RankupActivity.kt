package fr.lacitadelle.votecompagnon

import android.os.Bundle
import android.view.View
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

        // Config d’affichage de la WebView
        webView.apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val settings = webView.settings
        settings.apply {
            // Déjà présents chez toi
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_DEFAULT

            // 🔧 IMPORTANT pour l’effet “zoom”
            useWideViewPort = true             // respecte le meta viewport du HTML
            loadWithOverviewMode = false       // ne zoome pas pour tout faire tenir
            setSupportZoom(false)              // pas de pinch-to-zoom
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100                     // échelle neutre du texte
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useMedieval = prefs.getBoolean("pref_custom_font", true)

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!useMedieval) {
                    view.evaluateJavascript(
                        "document.body.classList.add('no-medieval');",
                        null
                    )
                }
            }
        }

        // Charger APRES avoir tout configuré
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
