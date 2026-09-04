package com.skala.autoatt

import android.content.Context

/**
 * 맥 버전의 .env에 해당하는 설정값 저장소.
 * USER_NAME / TARGET_EMAIL / REGION_NAME / CLASS_NAME 네 값을 SharedPreferences에 보관한다.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("skala_auto_att_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString(KEY_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_NAME, value).apply()

    var targetEmail: String
        get() = sp.getString(KEY_EMAIL, "") ?: ""
        set(value) = sp.edit().putString(KEY_EMAIL, value).apply()

    var regionName: String
        get() = sp.getString(KEY_REGION, "") ?: ""
        set(value) = sp.edit().putString(KEY_REGION, value).apply()

    var className: String
        get() = sp.getString(KEY_CLASS, "") ?: ""
        set(value) = sp.edit().putString(KEY_CLASS, value).apply()

    fun isComplete(): Boolean =
        userName.isNotBlank() && targetEmail.isNotBlank() && regionName.isNotBlank() && className.isNotBlank()

    companion object {
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "target_email"
        private const val KEY_REGION = "region_name"
        private const val KEY_CLASS = "class_name"
    }
}
