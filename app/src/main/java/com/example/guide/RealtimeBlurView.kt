package com.example.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 毛玻璃（磨砂）底栏容器。
 *
 * 原理：监听根视图的 OnPreDraw，每帧绘制前把"自己正下方"的内容从 DecorView 的
 * drawing cache 里截出来（截取期间跳过自身绘制，避免把底栏自己模糊进去），缩图后做
 * 两次盒式模糊，再叠加半透明表面色。像素与上次完全一致时不重绘，避免空闲时无限刷新。
 */
@Suppress("DEPRECATION")
class RealtimeBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 模糊半径（作用在缩小后的快照上），由 hbBlurRadius(dp) / hbDownsample 换算。 */
    private var smallBlurRadius = 4

    /** 采样缩小倍数：越大越省、越糊。 */
    private var downsample = 4

    /** 顶部圆角半径（px），底部贴屏幕为直角。 */
    private var cornerRadius = 0f

    /** 磨砂玻璃表面覆盖色（含 alpha）。 */
    private var overlayColor = Color.TRANSPARENT

    /** 当前模糊好的背景。 */
    private var blurredBitmap: Bitmap? = null

    /** 上次模糊结果的像素，用于判断内容是否变化、避免空闲时无限重绘。 */
    private var lastPixels: IntArray? = null

    /** 截取 drawing cache 时跳过自身绘制，防止把底栏自己模糊进去。 */
    private var capturing = false

    /** 持有的根视图，用于拿 drawing cache 和注册监听。 */
    private var decor: View? = null

    /** 顶部圆角裁剪路径，同时作为阴影 outline。 */
    private val clipPath = Path()

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        setWillNotDraw(false)

        val density = resources.displayMetrics.density
        val a = context.obtainStyledAttributes(attrs, R.styleable.RealtimeBlurView)
        try {
            val blurDip = a.getDimension(R.styleable.RealtimeBlurView_hbBlurRadius, 32f * density)
            downsample = a.getInt(R.styleable.RealtimeBlurView_hbDownsample, 4).coerceIn(1, 8)
            cornerRadius = a.getDimensionPixelSize(
                R.styleable.RealtimeBlurView_hbCornerRadius,
                (24 * density).roundToInt()
            ).toFloat()
            smallBlurRadius = max(1, (blurDip / downsample).roundToInt())
            overlayColor = if (a.hasValue(R.styleable.RealtimeBlurView_hbOverlayColor)) {
                a.getColor(R.styleable.RealtimeBlurView_hbOverlayColor, Color.TRANSPARENT)
            } else {
                // 默认用当前主题的表面色 + 40% 不透明度做磨砂面纱
                ColorUtils.setAlphaComponent(resolveSurfaceColor(), 0x66)
            }
        } finally {
            a.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val root = rootView
        decor = root
        root.isDrawingCacheEnabled = true
        root.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    override fun onDetachedFromWindow() {
        decor?.let {
            it.isDrawingCacheEnabled = false
            it.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        decor = null
        blurredBitmap?.recycle()
        blurredBitmap = null
        lastPixels = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        clipPath.reset()
        clipPath.addRoundRect(
            RectF(0f, 0f, w.toFloat(), h.toFloat()),
            floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f),
            Path.Direction.CW
        )
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setPath(clipPath)
            }
        }
        clipToOutline = true
    }

    override fun onDraw(canvas: Canvas) {
        // 给 DecorView 重建 drawing cache 时不画自己，让缓存里是底栏下方的真实内容
        if (capturing) return
        val bitmap = blurredBitmap ?: return
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bitmapPaint)
        canvas.drawColor(overlayColor)
        canvas.restore()
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 截取背景时连子 View（导航图标/文字）也一起跳过，避免把底栏自己模糊进背景
        if (capturing) return
        super.dispatchDraw(canvas)
    }

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        capture()
        true
    }

    /** 截取底栏下方的内容 → 缩图 → 盒式模糊 → 有变化才重绘。 */
    private fun capture() {
        val decorView = decor ?: return
        val viewWidth = width
        val viewHeight = height
        if (viewWidth <= 0 || viewHeight <= 0) return

        val location = IntArray(2)
        getLocationInWindow(location)

        val cache: Bitmap
        try {
            capturing = true
            cache = decorView.getDrawingCache(false) ?: return
        } finally {
            capturing = false
        }
        if (cache.width <= 0 || cache.height <= 0) return

        val x = location[0].coerceIn(0, cache.width - 1)
        val y = location[1].coerceIn(0, cache.height - 1)
        val cropWidth = minOf(viewWidth, cache.width - x)
        val cropHeight = minOf(viewHeight, cache.height - y)
        if (cropWidth <= 0 || cropHeight <= 0) return

        val region = Bitmap.createBitmap(cache, x, y, cropWidth, cropHeight)
        val smallWidth = max(1, cropWidth / downsample)
        val smallHeight = max(1, cropHeight / downsample)
        val small = Bitmap.createScaledBitmap(region, smallWidth, smallHeight, true)
        if (region !== small) region.recycle()

        blur(small, smallBlurRadius)

        // 内容没变化（像素完全一致）就不重绘，让预绘制循环停下来
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val previous = lastPixels
        if (previous != null && previous.size == pixels.size && pixels.contentEquals(previous)) {
            small.recycle()
            return
        }
        lastPixels = pixels

        val old = blurredBitmap
        blurredBitmap = small
        if (old != null && old !== small) old.recycle()
        invalidate()
    }

    /** 两次盒式模糊近似高斯模糊，作用在缩小后的快照上。 */
    private fun blur(bitmap: Bitmap, radius: Int) {
        if (radius < 1) return
        val width = bitmap.width
        val height = bitmap.height
        if (width < 2 || height < 2) return
        val source = IntArray(width * height)
        val buffer = IntArray(width * height)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)
        repeat(2) {
            blurHorizontal(source, buffer, width, height, radius)
            blurVertical(buffer, source, width, height, radius)
        }
        bitmap.setPixels(source, 0, width, 0, 0, width, height)
    }

    private fun blurHorizontal(
        input: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val alpha = IntArray(width + 1)
        val red = IntArray(width + 1)
        val green = IntArray(width + 1)
        val blue = IntArray(width + 1)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val color = input[row + x]
                alpha[x + 1] = alpha[x] + (color ushr 24)
                red[x + 1] = red[x] + ((color shr 16) and 0xFF)
                green[x + 1] = green[x] + ((color shr 8) and 0xFF)
                blue[x + 1] = blue[x] + (color and 0xFF)
            }
            for (x in 0 until width) {
                // 窗口越界时向两侧收缩，保证边缘像素也被模糊
                val left = max(0, x - radius)
                val right = minOf(width - 1, x + radius)
                val count = right - left + 1
                output[row + x] = pack(
                    (alpha[right + 1] - alpha[left]) / count,
                    (red[right + 1] - red[left]) / count,
                    (green[right + 1] - green[left]) / count,
                    (blue[right + 1] - blue[left]) / count
                )
            }
        }
    }

    private fun blurVertical(
        input: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val alpha = IntArray(height + 1)
        val red = IntArray(height + 1)
        val green = IntArray(height + 1)
        val blue = IntArray(height + 1)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = input[y * width + x]
                alpha[y + 1] = alpha[y] + (color ushr 24)
                red[y + 1] = red[y] + ((color shr 16) and 0xFF)
                green[y + 1] = green[y] + ((color shr 8) and 0xFF)
                blue[y + 1] = blue[y] + (color and 0xFF)
            }
            for (y in 0 until height) {
                val top = max(0, y - radius)
                val bottom = minOf(height - 1, y + radius)
                val count = bottom - top + 1
                output[y * width + x] = pack(
                    (alpha[bottom + 1] - alpha[top]) / count,
                    (red[bottom + 1] - red[top]) / count,
                    (green[bottom + 1] - green[top]) / count,
                    (blue[bottom + 1] - blue[top]) / count
                )
            }
        }
    }

    private fun pack(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)

    /** 读取主题里的 colorSurface 作为磨砂面纱的基色。 */
    private fun resolveSurfaceColor(): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurface,
                value,
                true
            )
        ) {
            value.data
        } else {
            Color.WHITE
        }
    }
}
