package com.skala.autoatt

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 이름 / 구글 이메일 / 지역 / 반을 입력받는 화면. 맥 버전의 .env 편집에 해당한다.
 * 지역은 고정 목록에서, 반은 지역을 고른 순간 실제 SKALA 사이트에서 읽어온
 * 진짜 목록에서 드롭다운으로 고른다 (타이핑 오타로 인한 실패를 원천적으로 없앤다).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var inputRegion: AutoCompleteTextView
    private lateinit var inputClass: AutoCompleteTextView
    private lateinit var classInputLayout: TextInputLayout

    /** 현재 inputClass 드롭다운에 채워진 반 목록이 어느 지역 기준인지. */
    private var classOptionsForRegion: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        findViewById<android.view.View>(R.id.rootLayout).applySystemBarInsetsAsPadding()
        // 이 화면은 흰 배경이라 상태바 아이콘을 어두운 색으로 맞춘다.
        setStatusBarIconsLight(false)

        val inputName = findViewById<TextInputEditText>(R.id.inputName)
        val inputEmail = findViewById<TextInputEditText>(R.id.inputEmail)
        inputRegion = findViewById(R.id.inputRegion)
        inputClass = findViewById(R.id.inputClass)
        classInputLayout = findViewById(R.id.classInputLayout)

        inputName.setText(prefs.userName)
        inputEmail.setText(prefs.targetEmail)

        val regionOptions = resources.getStringArray(R.array.region_options)
        inputRegion.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, regionOptions))
        inputRegion.setOnClickListener { inputRegion.showDropDown() }
        inputRegion.setOnItemClickListener { _, _, position, _ ->
            fetchClassOptions(regionOptions[position])
        }

        // 이전에 저장해 둔 값이 있으면 미리 채워두고, 반 목록도 바로 다시 불러온다.
        if (prefs.regionName.isNotBlank()) {
            inputRegion.setText(prefs.regionName, false)
            fetchClassOptions(prefs.regionName)
        }

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

    private fun fetchClassOptions(regionName: String) {
        classOptionsForRegion = null
        inputClass.setText("", false)
        inputClass.isEnabled = false
        inputClass.setAdapter(null)
        classInputLayout.hint = getString(R.string.label_class_loading)

        ClassOptionsFetcher(this).fetch(
            regionName = regionName,
            onResult = { classNames ->
                classInputLayout.hint = getString(R.string.label_class)
                inputClass.isEnabled = true
                inputClass.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, classNames))
                inputClass.setOnClickListener { inputClass.showDropDown() }
                classOptionsForRegion = regionName

                // 이전에 저장해 둔 반이 새로 불러온 목록에도 있으면 그대로 유지.
                if (regionName == prefs.regionName && classNames.contains(prefs.className)) {
                    inputClass.setText(prefs.className, false)
                }
            },
            onError = { message ->
                classInputLayout.hint = getString(R.string.label_class_placeholder)
                Toast.makeText(this, getString(R.string.error_class_fetch_failed, message), Toast.LENGTH_LONG).show()
            }
        )
    }
}
