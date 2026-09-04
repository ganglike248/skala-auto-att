package com.skala.autoatt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * 설정 화면의 "반" 드롭다운을 채우기 위한 도우미.
 * 반 목록은 지역마다 다르고 실제 SKALA 사이트에서 지역을 선택해야만 로드되므로,
 * 화면에 보이지 않는 WebView로 실제 사이트에 접속해 해당 지역의 반 목록을 읽어온 뒤 버린다.
 * (AutofillScript와 같은 사이트/선택자를 쓰지만, 여기서는 "다음"을 누르지 않고 목록만 읽는다.)
 */
class ClassOptionsFetcher(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    fun fetch(regionName: String, onResult: (List<String>) -> Unit, onError: (String) -> Unit) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        fun finish(action: () -> Unit) {
            (context as? Activity)?.runOnUiThread {
                action()
                webView.stopLoading()
                webView.destroy()
            }
        }

        webView.addJavascriptInterface(
            Bridge(
                onResult = { json ->
                    val arr = JSONArray(json)
                    val names = (0 until arr.length()).map { arr.getString(it) }
                    finish { onResult(names) }
                },
                onError = { message -> finish { onError(message) } }
            ),
            "ClassFetchBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(buildScript(regionName), null)
            }
        }
        webView.loadUrl(TARGET_URL)
    }

    private fun buildScript(regionName: String): String {
        val regionJson = JSONObject.quote(regionName)
        return TEMPLATE.replace("__REGION_JSON__", regionJson)
    }

    private class Bridge(
        private val onResult: (String) -> Unit,
        private val onError: (String) -> Unit
    ) {
        @JavascriptInterface
        fun onResult(json: String) = onResult.invoke(json)

        @JavascriptInterface
        fun onError(message: String) = onError.invoke(message)
    }

    companion object {
        private const val TARGET_URL = "https://auth.skala-ai.com/"

        // language=JavaScript
        private const val TEMPLATE = """
(function() {
  function waitFor(cond, timeoutMs) {
    return new Promise(function(resolve, reject) {
      var start = Date.now();
      (function check() {
        var value;
        try { value = cond(); } catch (e) { value = null; }
        if (value) return resolve(value);
        if (Date.now() - start > timeoutMs) return reject(new Error('시간 초과'));
        setTimeout(check, 200);
      })();
    });
  }

  function setNativeValue(select, value) {
    var setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value') &&
                 Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value').set;
    if (setter) { setter.call(select, value); } else { select.value = value; }
  }

  function findFieldSelect(labelSubstring) {
    var fields = Array.prototype.slice.call(document.querySelectorAll('.auth-field'));
    var field = fields.find(function(f) { return f.textContent.indexOf(labelSubstring) !== -1; });
    return field ? field.querySelector('select') : null;
  }

  async function run(regionName) {
    try {
      var regionSelect = await waitFor(function() { return findFieldSelect('지역'); }, 10000);
      await waitFor(function() {
        return Array.prototype.some.call(regionSelect.options, function(o) { return o.text.trim() === regionName; });
      }, 10000);
      var regionOpt = Array.prototype.find.call(regionSelect.options, function(o) { return o.text.trim() === regionName; });
      setNativeValue(regionSelect, regionOpt.value);
      regionSelect.dispatchEvent(new Event('change', { bubbles: true }));

      var classSelect = await waitFor(function() { return findFieldSelect('반 (클래스)'); }, 10000);
      await waitFor(function() { return !classSelect.disabled; }, 15000);
      await waitFor(function() { return classSelect.options.length > 1; }, 15000);

      var names = Array.prototype.map.call(classSelect.options, function(o) { return o.text.trim(); })
        .filter(function(t) { return t && t.indexOf('선택') === -1; });
      window.ClassFetchBridge.onResult(JSON.stringify(names));
    } catch (e) {
      window.ClassFetchBridge && window.ClassFetchBridge.onError(String(e && e.message ? e.message : e));
    }
  }

  run(__REGION_JSON__);
})();
"""
    }
}
