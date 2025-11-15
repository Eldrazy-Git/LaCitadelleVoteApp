package fr.lacitadelle.votecompagnon.legal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import fr.lacitadelle.votecompagnon.R

class LegalPageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_LaCitadelleVoteApp_Preferences)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal_page)

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val titleExtra = intent.getStringExtra("title") ?: getString(R.string.app_name)
        val asset = intent.getStringExtra("asset") ?: "legal_mentions.html"

        val webView = findViewById<WebView>(R.id.legalWebView)


        // WebView config
        webView?.apply {
            val s: WebSettings = settings
            s.javaScriptEnabled = false
            s.domStorageEnabled = false
            s.allowFileAccess = true
            s.allowContentAccess = true
            s.cacheMode = WebSettings.LOAD_NO_CACHE

            // Pas de scrollbars visibles
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            // Autorise l’effet d’étirement si le contenu dépasse
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS


            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    return handleUrl(view, url)
                }

                @Suppress("OverridingDeprecatedMember")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return handleUrl(view, url)
                }

                private fun handleUrl(view: WebView, url: String): Boolean {
                    // 1) Liens internes vers d'autres pages légales (relative type "legal_xxx.html")
                    if (!url.startsWith("http") && url.endsWith(".html")) {
                        // On suppose que c’est un autre asset du dossier /assets
                        val fileName = url.substringAfterLast('/')
                        view.loadUrl("file:///android_asset/$fileName")
                        return true
                    }

                    // 2) Liens assets explicites
                    if (url.startsWith("file:///android_asset/") && url.endsWith(".html")) {
                        view.loadUrl(url)
                        return true
                    }

                    // 3) Liens externes → navigateur système
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            // si aucun navigateur, on laisse tomber
                        }
                        return true
                    }

                    // le reste, on laisse le WebView gérer
                    return false
                }
            }

            loadUrl("file:///android_asset/$asset")
        }
    }
}
