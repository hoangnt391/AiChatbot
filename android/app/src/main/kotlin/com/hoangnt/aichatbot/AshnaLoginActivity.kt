package com.hoangnt.aichatbot

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AshnaLoginActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        root.addView(TextView(this).apply {
            text = "Đăng nhập Ashna một lần. Phiên đăng nhập sẽ được lưu trên máy."
            textSize = 15f
            setPadding(24, 18, 24, 18)
        })
        root.addView(Button(this).apply {
            text = "Xong — lưu phiên đăng nhập"
            setOnClickListener {
                CookieManager.getInstance().flush()
                getSharedPreferences("aichatbot", MODE_PRIVATE).edit().putBoolean("ashna_login_confirmed", true).apply()
                finish()
            }
        })
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { CookieManager.getInstance().flush() }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (state != null) webView.restoreState(state) else webView.loadUrl("https://app.ashna.ai/chat?agent=gpt-6.1-sol")
    }

    override fun onSaveInstanceState(outState: Bundle) { webView.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() { runCatching { webView.stopLoading() }; runCatching { webView.destroy() }; super.onDestroy() }
}
