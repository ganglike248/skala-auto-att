package com.skala.autoatt

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * targetSdk 35+에서는 엣지투엣지가 기본으로 강제되어, 인셋을 직접 안 잡아주면
 * 콘텐츠가 상태바/내비게이션 바 밑으로 깔려 겹쳐 보인다.
 * 이 뷰의 기존 padding 위에 시스템 바 인셋만큼을 더해서 겹침을 막는다.
 */
fun View.applySystemBarInsetsAsPadding() {
    val basePaddingLeft = paddingLeft
    val basePaddingTop = paddingTop
    val basePaddingRight = paddingRight
    val basePaddingBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(
            basePaddingLeft + bars.left,
            basePaddingTop + bars.top,
            basePaddingRight + bars.right,
            basePaddingBottom + bars.bottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** 왼쪽/오른쪽/아래쪽 인셋만 패딩으로 더한다 (위쪽은 별도 뷰가 처리할 때 사용). */
fun View.applySideAndBottomInsetsAsPadding() {
    val basePaddingLeft = paddingLeft
    val basePaddingTop = paddingTop
    val basePaddingRight = paddingRight
    val basePaddingBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(
            basePaddingLeft + bars.left,
            basePaddingTop,
            basePaddingRight + bars.right,
            basePaddingBottom + bars.bottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * 위쪽 인셋(상태바 높이)만큼 패딩을 더한다. 툴바 같은 뷰에 적용하면
 * 뷰의 배경색이 상태바 영역까지 그대로 이어지고, 실제 내용(제목 등)은
 * 상태바 아이콘과 안 겹치도록 그 아래로 밀려난다.
 */
fun View.applyTopInsetAsPadding() {
    val basePaddingTop = paddingTop

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(paddingLeft, basePaddingTop + bars.top, paddingRight, paddingBottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * 상태바 아이콘(와이파이/시계 등) 색을 지정한다.
 * lightIcons = true  → 흰색 아이콘 (어두운/색이 있는 상태바 배경용)
 * lightIcons = false → 어두운 아이콘 (밝은/흰 배경용)
 */
fun Activity.setStatusBarIconsLight(lightIcons: Boolean) {
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !lightIcons
}
