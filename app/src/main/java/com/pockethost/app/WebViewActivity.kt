package com.pockethost.app

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val url = intent.getStringExtra("url") ?: "http://127.0.0.1:8080"
        val title = intent.getStringExtra("title") ?: "Preview"

        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient() // Keeps navigation inside the app
        webView.loadUrl(url)

        setContentView(webView)
    }

    // Handle the back arrow in the top bar
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
