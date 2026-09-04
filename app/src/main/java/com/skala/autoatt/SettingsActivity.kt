package com.skala.autoatt

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

/**
 * 이름 / 구글 이메일 / 지역 / 반을 입력받는 화면.
 * 맥 버전의 .env 파일 편집에 해당.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        val inputName = findViewById<TextInputEditText>(R.id.inputName)
        val inputEmail = findViewById<TextInputEditText>(R.id.inputEmail)
        val inputRegion = findViewById<TextInputEditText>(R.id.inputRegion)
        val inputClass = findViewById<TextInputEditText>(R.id.inputClass)

        inputName.setText(prefs.userName)
        inputEmail.setText(prefs.targetEmail)
        inputRegion.setText(prefs.regionName)
        inputClass.setText(prefs.className)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonSave).setOnClickListener {
            val name = inputName.text?.toString()?.trim().orEmpty()
            val email = inputEmail.text?.toString()?.trim().orEmpty()
            val region = inputRegion.text?.toString()?.trim().orEmpty()
            val className = inputClass.text?.toString()?.trim().orEmpty()

            if (name.isBlank() || email.isBlank() || region.isBlank() || className.isBlank()) {
                Toast.makeText(this, R.string.error_missing_settings, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.userName = name
            prefs.targetEmail = email
            prefs.regionName = region
            prefs.className = className

            setResult(RESULT_OK)
            finish()
        }
    }
}
