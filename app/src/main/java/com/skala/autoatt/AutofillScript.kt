package com.skala.autoatt

import org.json.JSONObject

/**
 * auto_att.py의 Playwright 로직(이름 입력 → 지역 선택 → 반 선택 → "다음" 클릭)을
 * 순수 JavaScript로 옮긴 것. WebView.evaluateJavascript로 페이지에 직접 주입한다.
 *
 * 구글 로그인 단계는 여기서 다루지 않는다 — MainActivity가 accounts.google.com으로의
 * 이동을 감지해서 Chrome 커스텀 탭으로 넘기고, 이 스크립트는 딱 "다음" 버튼 클릭까지만 담당한다.
 */
object AutofillScript {

    fun build(userName: String, regionName: String, className: String): String {
        val config = JSONObject().apply {
            put("name", userName)
            put("region", regionName)
            put("className", className)
        }
        return TEMPLATE.replace("__CONFIG_JSON__", config.toString())
    }

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
        if (Date.now() - start > timeoutMs) return reject(new Error('시간 초과: ' + cond.toString()));
        setTimeout(check, 200);
      })();
    });
  }

  function setNativeValue(el, value) {
    var proto = el.tagName === 'SELECT' ? window.HTMLSelectElement.prototype : window.HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value') &&
                 Object.getOwnPropertyDescriptor(proto, 'value').set;
    if (setter) { setter.call(el, value); } else { el.value = value; }
  }

  function selectByLabel(select, label) {
    var opt = Array.prototype.find.call(select.options, function(o) {
      return o.text.trim() === label;
    });
    if (!opt) throw new Error("옵션을 찾을 수 없음: " + label);
    setNativeValue(select, opt.value);
    select.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function findFieldSelect(labelSubstring) {
    var fields = Array.prototype.slice.call(document.querySelectorAll('.auth-field'));
    var field = fields.find(function(f) { return f.textContent.indexOf(labelSubstring) !== -1; });
    return field ? field.querySelector('select') : null;
  }

  async function run(cfg) {
    try {
      window.AndroidBridge && window.AndroidBridge.onLog('이름 입력 중...');
      var nameInput = await waitFor(function() {
        return document.querySelector("input.auth-input[placeholder='훈련생 이름 입력']");
      }, 15000);
      setNativeValue(nameInput, cfg.name);
      nameInput.dispatchEvent(new Event('input', { bubbles: true }));

      window.AndroidBridge && window.AndroidBridge.onLog('지역 선택 중: ' + cfg.region);
      var regionSelect = await waitFor(function() { return findFieldSelect('지역'); }, 10000);
      selectByLabel(regionSelect, cfg.region);

      window.AndroidBridge && window.AndroidBridge.onLog('반 목록 로딩 대기 중...');
      var classSelect = await waitFor(function() { return findFieldSelect('반 (클래스)'); }, 10000);
      await waitFor(function() { return !classSelect.disabled; }, 15000);
      await waitFor(function() {
        return Array.prototype.some.call(classSelect.options, function(o) {
          return o.text.trim() === cfg.className;
        });
      }, 15000);
      selectByLabel(classSelect, cfg.className);

      window.AndroidBridge && window.AndroidBridge.onLog("'다음' 버튼 클릭 중...");
      var nextBtn = await waitFor(function() { return document.querySelector('button.auth-btn'); }, 10000);
      await waitFor(function() { return !nextBtn.disabled; }, 15000);
      nextBtn.click();

      window.AndroidBridge && window.AndroidBridge.onDone();
    } catch (e) {
      window.AndroidBridge && window.AndroidBridge.onError(String(e && e.message ? e.message : e));
    }
  }

  run(__CONFIG_JSON__);
})();
"""
}
