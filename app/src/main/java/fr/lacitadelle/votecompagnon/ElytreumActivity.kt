package fr.lacitadelle.votecompagnon

import android.os.Bundle
import android.view.View
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
        // edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_elytreum)

        webView = findViewById(R.id.elytreumWebView)

        // Inset pour barre de statut / navigation
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, sys.top, v.paddingRight, sys.bottom)
            insets
        }

        webView.apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val settings = webView.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_DEFAULT

            // 🔧 Anti-zoom
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
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
