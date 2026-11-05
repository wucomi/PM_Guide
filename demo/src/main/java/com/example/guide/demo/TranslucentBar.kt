package com.example.guide.demo

import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import kotlin.math.roundToInt

/**
 * 给覆盖式底栏应用跟随主题的半透明背景。
 *
 * 只改背景 alpha，栏内的按钮和文字仍然完全不透明；圆角和描边保持和之前一致。
 */
internal fun View.applyTranslucentBarBackground(alpha: Float = 0.7f) {
    val density = resources.displayMetrics.density
    val surface = MaterialColors.getColor(this, MaterialR.attr.colorSurfaceContainer)
    val outline = MaterialColors.getColor(this, MaterialR.attr.colorOutlineVariant)
    val radius = 22f * density

    background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        setColor(ColorUtils.setAlphaComponent(surface, (255 * alpha).roundToInt()))
        setStroke(density.roundToInt().coerceAtLeast(1), outline)
    }
}
