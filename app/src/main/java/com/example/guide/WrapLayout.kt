package com.example.guide

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import kotlin.math.max

/** 自动换行的行布局，保证所有锚点标签都在屏幕内可见。 */
class WrapLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    /** 使用 MarginLayoutParams，读取标签的 marginEnd。 */
    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        MarginLayoutParams(context, attrs)

    /** 默认布局参数。 */
    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    /** 复用外部布局参数。 */
    override fun generateLayoutParams(params: LayoutParams): LayoutParams =
        MarginLayoutParams(params)

    /** 按可用宽度逐行测量。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val maxWidth = if (widthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else widthSize

        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin

            if (rowWidth > 0 && rowWidth + childWidth > maxWidth) {
                totalHeight += rowHeight
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += childWidth
            rowHeight = max(rowHeight, childHeight)
        }

        totalHeight += rowHeight
        setMeasuredDimension(
            if (widthMode == MeasureSpec.UNSPECIFIED) rowWidth else widthSize,
            resolveSize(totalHeight, heightMeasureSpec)
        )
    }

    /** 逐行摆放子 View。 */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val maxWidth = right - left
        var x = 0
        var y = 0
        var rowHeight = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue

            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin

            if (x > 0 && x + childWidth > maxWidth) {
                x = 0
                y += rowHeight
                rowHeight = 0
            }
            child.layout(
                x + params.leftMargin,
                y + params.topMargin,
                x + params.leftMargin + child.measuredWidth,
                y + params.topMargin + child.measuredHeight
            )
            x += childWidth
            rowHeight = max(rowHeight, childHeight)
        }
    }
}
