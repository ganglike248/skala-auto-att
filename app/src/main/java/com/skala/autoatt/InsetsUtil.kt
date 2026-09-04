package com.skala.autoatt

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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
