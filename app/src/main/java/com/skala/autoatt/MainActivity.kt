package com.skala.autoatt

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.appbar.MaterialToolbar
import android.widget.TextView

/**
 * 앱을 열면 SKALA 출결 사이트에 접속해서 이름/지역/반을 자동으로 채우고
 * "다음" 버튼까지 자동으로 누른다. 구글 로그인 화면으로 넘어가는 순간부터는
 * (구글이 앱 내장 WebView에서의 로그인을 막기 때문에) Chrome 커스텀 탭으로 넘겨서
 * 사람이 직접 계정을 선택하게 한다. 출석 체크 버튼도 마찬가지로 사람이 직접 누른다.
 */
class MainActivity : AppCompatActivity() {

    private val targetUrl = "https://auth.skala-ai.com/"
    private lateinit var prefs: Prefs
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private var autofillInjected = false

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        loadIfReady()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        findViewById<android.view.View>(R.id.rootLayout).applySystemBarInsetsAsPadding()
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(JsBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                return if (!isTargetHost(url)) {
                    openInCustomTab(url)
                    true
                } else {
                    false
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (isTargetHost(Uri.parse(url))) {
                    autofillInjected = false
                    setStatus(getString(R.string.status_loading))
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (isTargetHost(Uri.parse(url)) && !autofillInjected) {
                    autofillInjected = true
                    setStatus(getString(R.string.status_filling))
                    val script = AutofillScript.build(prefs.userName, prefs.regionName, prefs.className)
                    view.evaluateJavascript(script, null)
                }
            }
        }

        loadIfReady()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.isComplete() && webView.url == null) {
            loadIfReady()
        }
    }

    private fun loadIfReady() {
        if (!prefs.isComplete()) {
            setStatus(getString(R.string.status_idle))
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            return
        }
        webView.loadUrl(targetUrl)
    }

    private fun isTargetHost(uri: Uri): Boolean = uri.host == Uri.parse(targetUrl).host

    private fun openInCustomTab(url: Uri) {
        CustomTabsIntent.Builder().build().launchUrl(this, url)
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onLog(message: String) {
            setStatus(message)
        }

        @JavascriptInterface
        fun onDone() {
            setStatus(getString(R.string.status_next_clicked))
        }

        @JavascriptInterface
        fun onError(message: String) {
            setStatus(getString(R.string.status_error_prefix, message))
        }
    }
}
