package com.example.guide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 挖洞形状示例用的预览按钮。
 *
 * ## 特点
 * - 按钮本身画成对应形状（矩形 / 圆角 / 圆形 / 椭圆 / 星形）；
 * - 点击前后都能直观看到所选形状，而不是所有按钮都是同一种胶囊；
 * - 通过 `previewShape` / `previewText` 属性配置。
 */
class ShapePreviewButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 画笔和路径在实例内复用
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    /** 屏幕密度，用于把 dp 转成 px。 */
    private val density = resources.displayMetrics.density

    /** XML 中 previewShape 的值。 */
    private val shapeKind: Int

    /** XML 中 previewText 的值。 */
    private val label: String

    private val fillColor: Int = resolveThemeColor(
        com.google.android.material.R.attr.colorSecondaryContainer,
        0xFFE0E0E0.toInt()
    )
    private val strokeColor: Int = resolveThemeColor(
        com.google.android.material.R.attr.colorOutline,
        0xFF888888.toInt()
    )
    private val textColor: Int = resolveThemeColor(
        com.google.android.material.R.attr.colorOnSecondaryContainer,
        0xFF222222.toInt()
    )

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        val ta = context.obtainStyledAttributes(attrs, R.styleable.ShapePreviewButton)
        shapeKind = ta.getInt(R.styleable.ShapePreviewButton_previewShape, SHAPE_RECT)
        label = ta.getString(R.styleable.ShapePreviewButton_previewText).orEmpty()
        ta.recycle()

        contentDescription = label
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 12f * density
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = textColor
    }

    /** 按压时降低整体透明度，作为简单的点击反馈。 */
    override fun drawableStateChanged() {
        super.drawableStateChanged()
        alpha = if (isPressed) 0.72f else 1f
    }

    /** 按 [shapeKind] 画填充、描边，并把文字居中放在形状内部。 */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = density
        rect.set(inset, inset, width - inset, height - inset)
        if (rect.isEmpty) return

        path.reset()
        when (shapeKind) {
            SHAPE_ROUND_RECT -> {
                val radius = 12f * density
                path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            }

            SHAPE_CIRCLE -> path.addCircle(
                rect.centerX(),
                rect.centerY(),
                min(rect.width(), rect.height()) / 2f,
                Path.Direction.CW
            )

            SHAPE_OVAL -> path.addOval(rect, Path.Direction.CW)

            SHAPE_STAR -> buildStarPath(rect, path)

            else -> path.addRect(rect, Path.Direction.CW)
        }

        fillPaint.style = Paint.Style.FILL
        fillPaint.color = fillColor
        canvas.drawPath(path, fillPaint)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = density
        strokePaint.color = strokeColor
        canvas.drawPath(path, strokePaint)

        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }

    /** 解析主题颜色属性。 */
    private fun resolveThemeColor(@AttrRes attr: Int, fallback: Int): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) return fallback
        return if (value.resourceId != 0) {
            ContextCompat.getColor(context, value.resourceId)
        } else {
            value.data
        }
    }

    private companion object {
        // 与 attrs.xml 中 previewShape 的枚举值保持一致
        const val SHAPE_RECT = 0
        const val SHAPE_ROUND_RECT = 1
        const val SHAPE_CIRCLE = 2
        const val SHAPE_OVAL = 3
        const val SHAPE_STAR = 4
    }
}

/**
 * 在 `rect` 内构造五角星路径。
 *
 * ## 说明
 * 形状预览按钮和挖洞示例共用同一套几何，避免两处公式不一致。
 */
internal fun buildStarPath(rect: RectF, path: Path, innerRatio: Float = 0.45f) {
    path.reset()
    val cx = rect.centerX()
    val cy = rect.centerY()
    val outer = min(rect.width(), rect.height()) / 2f
    val inner = outer * innerRatio
    repeat(10) { index ->
        val angle = Math.toRadians(-90.0 + index * 36.0)
        val radius = if (index % 2 == 0) outer else inner
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}
