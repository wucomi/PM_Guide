package com.pm.guide

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding
import com.pm.guide.databinding.ViewGuideTooltipDefaultBinding
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 引导的渲染层。
 *
 * ## 职责
 * - 绘制遮罩、挖洞、箭头和气泡；
 * - 跟踪锚点并处理跟随、失效与 pending 状态；
 * - 处理气泡内点击、锚点穿透和遮罩外穿透。
 *
 * ## 布局
 * - 作为 `DialogFragment` 的根 View 全屏铺开，不参与业务布局；
 * - 气泡是它的子 View，位置由 [GuidePositioner] 计算后手动 layout；
 * - 遮罩和挖洞直接画在 Canvas 上，使用 Overlay 本地坐标。
 */
internal class GuideOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** 当前步骤的锚点失效时回调；具体是关闭、跳过还是保留由宿主决定。 */
    var anchorLostListener: (() -> Unit)? = null

    /** 点击气泡和洞以外的区域是否关闭引导。 */
    var dismissOnTouchOutside: Boolean = false

    /** [GuideStep.followAnchor] 为 null 时使用的全局默认值。 */
    var followAnchorDefault: Boolean = true

    /** 几何信息变化时回调，由宿主转发到 [GuideListener.onStepLocated]。 */
    var stepLocatedListener: ((Int, Rect, Rect?, Rect) -> Unit)? = null

    /** 气泡容器；内容由 [buildTooltip] 挂载，位置由 [onLayout] 手动摆放。 */
    private val bubble = FrameLayout(context)

    /** 遮罩画笔；每帧只更新颜色。 */
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 箭头画笔。 */
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 洞内高亮层画笔；用于 [GuideHighlight.holeOverlayColor]。 */
    private val holeOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 遮罩区域路径。 */
    private val maskPath = Path()

    /** 当前洞形状路径。 */
    private val holeShapePath = Path()

    /** 遮罩减去洞之后的绘制路径。 */
    private val holeResultPath = Path()

    /** 箭头三角形路径。 */
    private val arrowPath = Path()

    /** 当前步骤；null 表示未绑定或已释放。 */
    private var step: GuideStep? = null

    /** 当前会话，用于 next / finish 等流程控制。 */
    private var session: GuideSession? = null

    /** 目标挖洞矩形；命中测试和回调使用它。 */
    private val holeRect = RectF()

    /** 实际绘制的挖洞矩形；入场动画期间与 [holeRect] 不同。 */
    private val animatedHoleRect = RectF()

    /** 遮罩绘制范围；[GuideHighlight.maskRectProvider] 为 null 时铺满窗口。 */
    private val maskRect = RectF()

    /** 气泡最终矩形（本地坐标）。 */
    private val tooltipRect = RectF()

    /** 箭头尖端（本地坐标）；无箭头时为 null。 */
    private var arrowTip: PointF? = null

    /** 实际生效的气泡方位，用于决定箭头从气泡哪条边伸出。 */
    private var resolvedSide: Side = Side.BOTTOM

    /** 锚点当前可见矩形（屏幕坐标）；null 表示尚未解析到。 */
    private var anchorScreenRect: Rect? = null

    // ---- 锚点跟踪 ----

    /** 锚点 View；RecyclerView 锚点返回 RecyclerView 本身，用来挂 attach 监听。 */
    private var anchorView: View? = null

    /** 锚点的 attach/detach 监听。 */
    private var attachListener: OnAttachStateChangeListener? = null

    /** 锚点所在窗口的 pre-draw 监听。 */
    private var anchorPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    /** 当前挂载 pre-draw 的窗口根 View。 */
    private var anchorPreDrawTarget: View? = null

    /** 引导自身窗口的 pre-draw 监听。 */
    private var overlayPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    /** 是否已经历过首帧检查，用于区分“还没准备好”和“后来丢失”。 */
    private var initialCheckDone = false

    /** 首帧连续拿不到锚点的次数。 */
    private var missedFrames = 0

    /** 是否已经通知过锚点丢失，避免重复回调。 */
    private var anchorLostNotified = false

    /** 气泡是否已完成首次有效定位并显示。 */
    private var positioned = false

    /** 锚点丢失且策略为 KEEP 时，是否允许气泡回退到屏幕中央。 */
    private var fallbackAllowed = false

    // ---- 挖洞动画 ----

    /** 挖洞入场动画；null 表示没有正在播放的动画。 */
    private var holeAnimator: ValueAnimator? = null

    /** 当前步骤是否已经播过入场动画，避免锚点移动时重播。 */
    private var holeAnimationStarted = false

    /** 遮罩 alpha 系数，入场时从 0 淡入到 1。 */
    private var scrimAlphaFactor = 1f

    // ---- pending 锚点（RecyclerView 自动滚动）----

    /** 是否已经安排了 pending 复查，避免重复 post。 */
    private var pendingCheckScheduled = false

    /** 上一次检查时锚点是否处于 pending 状态。 */
    private var lastAnchorCheckWasPending = false

    // ---- 触摸状态 ----

    /** 非气泡区域按下时的起点，用于判断是否为一次点击。 */
    private var downPoint: PointF? = null

    /** 当前手势是否从气泡内部开始（用于把事件交给子 View）。 */
    private var touchStartedInTooltip = false

    /** 当前手势是否已经转发给宿主 Activity。 */
    private var passThroughActive = false

    /** 系统触摸抖动阈值，用于区分点击和滑动。 */
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // ---- 几何回调去重 ----

    /** 上一次回调的锚点矩形。 */
    private var lastLocatedAnchor: Rect? = null

    /** 上一次回调的洞矩形；无洞时为 null。 */
    private var lastLocatedHole: Rect? = null

    /** 上一次回调的气泡矩形。 */
    private var lastLocatedTooltip: Rect? = null

    // ---- 复用对象，减少 onLayout / onDraw / 事件分发中的分配 ----

    /** Overlay 窗口左上角的屏幕坐标。 */
    private val originLocation = IntArray(2)

    /** 宿主 Activity decorView 左上角的屏幕坐标。 */
    private val decorLocation = IntArray(2)

    /** 锚点矩形换算到 Overlay 本地坐标后的结果。 */
    private val localAnchorRect = Rect()

    /** 高亮/挖洞基准矩形（本地坐标）。 */
    private val highlightRect = Rect()

    /** 气泡可用区域（本地坐标）。 */
    private val availableRect = Rect()

    /** 传给 holeRectProvider 的锚点矩形（屏幕坐标）。 */
    private val providerAnchorRect = Rect()

    /** 传给 maskRectProvider 的容器矩形（屏幕坐标）。 */
    private val providerContainerRect = Rect()

    /** maskRectProvider 的返回值（屏幕坐标）。 */
    private val providerMaskRect = Rect()

    /** updateHole 中复用的临时矩形。 */
    private val holeRectF = RectF()

    /** 自定义 Path 构建时复用的临时矩形。 */
    private val customShapeRect = RectF()

    /** 屏幕密度，用于 dp 转 px。 */
    private val density: Float = resources.displayMetrics.density

    /** 当前布局是否 RTL。 */
    private val isRtl: Boolean get() = layoutDirection == LAYOUT_DIRECTION_RTL

    /** 默认气泡背景色（主题 colorSurface）。 */
    private val surfaceBubbleColor: Int = resolveThemeColor(
        com.google.android.material.R.attr.colorSurface,
        Color.WHITE
    )

    /** 默认气泡描边色（主题 colorOutlineVariant）。 */
    private val surfaceBubbleStrokeColor: Int = resolveThemeColor(
        com.google.android.material.R.attr.colorOutlineVariant,
        Color.TRANSPARENT
    )

    /** 当前步骤实际使用的气泡背景色，箭头会复用这个颜色。 */
    private var bubbleFillColor: Int = surfaceBubbleColor

    init {
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)

        bubble.clipChildren = false
        bubble.clipToPadding = false
        bubble.elevation = 8.dp.px(density).toFloat()
        applyBubbleBackground(
            fill = surfaceBubbleColor,
            stroke = surfaceBubbleStrokeColor,
            radiusPx = 16.dp.px(density).toFloat(),
            strokeWidthPx = 1.dp.px(density)
        )
        addView(bubble, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * 绑定一个步骤。每次步骤切换都会重建气泡内容并重新挂载锚点监听。
     */
    fun bind(
        step: GuideStep,
        session: GuideSession,
        index: Int,
        total: Int
    ) {
        releaseTracking()
        resetTransientState()
        this.step = step
        this.session = session

        applyBubbleStyle(step.tooltip)
        buildTooltip(step, session, index, total)
        installTracking(step)

        // 第一帧先隐藏，等拿到有效锚点并完成定位后再淡入，避免居中闪烁。
        bubble.alpha = 0f

        // 立即解析一次锚点。切换步骤时宿主窗口通常不会重新布局，
        // 只能依赖这一帧主动拿到矩形，否则气泡会一直停在 alpha = 0。
        onPreDraw()
        requestLayout()
        invalidate()
    }

    /**
     * 释放所有监听和动画。DialogFragment 结束或销毁时必须调用。
     */
    fun release() {
        releaseTracking()
        resetTransientState()
        bubble.removeAllViews()
        step = null
        session = null
        anchorLostListener = null
        stepLocatedListener = null
    }

    /**
     * 重置与单个步骤相关的临时状态。
     *
     * 在步骤切换和引导结束时调用；不处理 step/session/listener，
     * 这三者由调用方决定生命周期。
     */
    private fun resetTransientState() {
        holeAnimator?.cancel()
        holeAnimator = null
        bubble.animate().cancel()
        holeAnimationStarted = false
        scrimAlphaFactor = 1f
        pendingCheckScheduled = false
        lastAnchorCheckWasPending = false
        initialCheckDone = false
        missedFrames = 0
        anchorLostNotified = false
        positioned = false
        fallbackAllowed = false
        anchorScreenRect = null
        tooltipRect.setEmpty()
        holeRect.setEmpty()
        animatedHoleRect.setEmpty()
        maskRect.setEmpty()
        arrowTip = null
        downPoint = null
        touchStartedInTooltip = false
        passThroughActive = false
        lastLocatedAnchor = null
        lastLocatedHole = null
        lastLocatedTooltip = null
    }

    /** 根据 GuideTooltip 配置应用气泡背景、圆角和描边。 */
    private fun applyBubbleStyle(tooltip: GuideTooltip) {
        applyBubbleBackground(
            fill = tooltip.backgroundColor ?: surfaceBubbleColor,
            stroke = tooltip.strokeColor ?: surfaceBubbleStrokeColor,
            radiusPx = tooltip.cornerRadius.px(density).toFloat(),
            strokeWidthPx = tooltip.strokeWidth.px(density)
        )
    }

    /** 重建气泡背景 Drawable，并记录箭头需要复用的填充色。 */
    private fun applyBubbleBackground(
        fill: Int,
        stroke: Int,
        radiusPx: Float,
        strokeWidthPx: Int
    ) {
        bubbleFillColor = fill
        bubble.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fill)
            if (strokeWidthPx > 0 && Color.alpha(stroke) != 0) {
                setStroke(strokeWidthPx, stroke)
            }
        }
    }

    /** 把当前步骤的 GuideContent 转成 View 挂到气泡容器。 */
    private fun buildTooltip(
        step: GuideStep,
        session: GuideSession,
        index: Int,
        total: Int
    ) {
        bubble.removeAllViews()
        val contentView = when (val content = step.tooltip.content) {
            is GuideContent.Default -> {
                // 默认气泡布局也用 ViewBinding，避免逐个 findViewById。
                val binding = ViewGuideTooltipDefaultBinding.inflate(
                    LayoutInflater.from(context), bubble, false
                )
                bindDefaultContent(binding, content, session, index, total)
                binding.root
            }

            is GuideContent.Custom -> {
                LayoutInflater.from(context)
                    .inflate(content.layoutRes, bubble, false)
                    .also { content.bind(it, session) }
            }

            is GuideContent.CustomBinding<*> -> buildBoundContent(content, session)
        }
        bubble.addView(
            contentView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
    }

    /** 解包 CustomBinding 泛型并绑定；create 返回的正是 bind 期望的类型，cast 安全。 */
    private fun buildBoundContent(
        content: GuideContent.CustomBinding<*>,
        session: GuideSession
    ): View {
        @Suppress("UNCHECKED_CAST")
        val typed = content as GuideContent.CustomBinding<ViewBinding>
        val binding = typed.create(LayoutInflater.from(context), bubble)
        typed.bind(binding, session)
        return binding.root
    }

    /** 填充默认气泡布局：标题、正文、步骤指示和两个按钮。 */
    private fun bindDefaultContent(
        binding: ViewGuideTooltipDefaultBinding,
        content: GuideContent.Default,
        session: GuideSession,
        index: Int,
        total: Int
    ) {
        val title = binding.guideTitle
        val message = binding.guideMessage
        val indicator = binding.guideIndicator
        val skip = binding.guideSkip
        val primary = binding.guidePrimary

        if (content.title.isNullOrBlank()) {
            title.visibility = GONE
        } else {
            title.visibility = VISIBLE
            title.text = content.title
            content.titleColor?.let(title::setTextColor)
        }
        message.text = content.message
        content.messageColor?.let(message::setTextColor)

        if (content.showIndicator && total > 1) {
            indicator.visibility = VISIBLE
            indicator.text = context.getString(R.string.guide_step_indicator, index + 1, total)
        } else {
            indicator.visibility = GONE
        }

        val skipLabel = content.skipLabel
        if (skipLabel.isNullOrBlank()) {
            skip.visibility = GONE
        } else {
            skip.visibility = VISIBLE
            skip.text = skipLabel
            skip.setOnClickListener { session.skip() }
        }

        primary.text = if (index == total - 1) content.finishLabel else content.primaryLabel
        primary.setOnClickListener {
            if (index == total - 1) session.finish() else session.next()
        }
    }

    /**
     * 挂载锚点跟踪：
     * - attach/detach 监听，处理锚点晚于引导 attach 或中途 detach；
     * - 在锚点所在窗口和引导自身窗口上都挂 pre-draw，覆盖锚点移动与步骤切换。
     */
    private fun installTracking(step: GuideStep) {
        val view = anchorViewOf(step.anchor)
        anchorView = view
        if (view != null) {
            attachListener = object : OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    // 锚点可能晚于引导 attach；重新挂到新的 ViewTreeObserver 上。
                    installAnchorPreDrawTracking(v.rootView)
                    v.post { if (this@GuideOverlayView.step != null) onPreDraw() }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    if (this@GuideOverlayView.step != null) {
                        notifyAnchorLost()
                    }
                }
            }.also { view.addOnAttachStateChangeListener(it) }
        }

        view?.rootView?.let { installAnchorPreDrawTracking(it) }

        // 关键：同时监听引导自己的窗口。步骤切换不会让 Activity 窗口重绘，
        // 只监听锚点所在窗口会导致第二步拿不到锚点、气泡永远不可见。
        ensureOverlayPreDraw()
    }

    /** 把锚点侧的 pre-draw 监听换到新的窗口树上。 */
    private fun installAnchorPreDrawTracking(target: View) {
        anchorPreDrawListener?.let { listener ->
            runCatching {
                anchorPreDrawTarget?.viewTreeObserver?.removeOnPreDrawListener(listener)
            }
        }
        anchorPreDrawTarget = target
        anchorPreDrawListener = ViewTreeObserver.OnPreDrawListener {
            if (this@GuideOverlayView.step != null) {
                onPreDraw()
            }
            true
        }.also { target.viewTreeObserver.addOnPreDrawListener(it) }
    }

    /** 懒挂引导自身窗口的 pre-draw；已挂过则直接返回。 */
    private fun ensureOverlayPreDraw() {
        if (overlayPreDrawListener != null) return
        overlayPreDrawListener = ViewTreeObserver.OnPreDrawListener {
            if (step != null) {
                onPreDraw()
            }
            true
        }.also { runCatching { viewTreeObserver.addOnPreDrawListener(it) } }
    }

    /** 移除引导自身窗口的 pre-draw 监听。 */
    private fun removeOverlayPreDraw() {
        overlayPreDrawListener?.let { listener ->
            runCatching { viewTreeObserver.removeOnPreDrawListener(listener) }
        }
        overlayPreDrawListener = null
    }

    /** 释放所有锚点跟踪监听，可安全重复调用。 */
    private fun releaseTracking() {
        attachListener?.let { listener ->
            anchorView?.removeOnAttachStateChangeListener(listener)
        }
        attachListener = null
        anchorView = null

        anchorPreDrawListener?.let { listener ->
            runCatching {
                anchorPreDrawTarget?.viewTreeObserver?.removeOnPreDrawListener(listener)
            }
        }
        anchorPreDrawListener = null
        anchorPreDrawTarget = null

        removeOverlayPreDraw()
    }

    /**
     * 取出锚点用于挂 attach 监听的 View。
     *
     * View/Id/RecyclerView 锚点都有稳定的宿主 View；RectAnchor 没有，
     * 只依赖窗口的 pre-draw。
     */
    private fun anchorViewOf(anchor: GuideAnchor): View? = when (anchor) {
        is GuideAnchor.ViewAnchor -> anchor.view
        is GuideAnchor.RecyclerViewItemAnchor -> anchor.recyclerView
        is GuideAnchor.RectAnchor -> null
    }

    /**
     * 每次绘制前刷新锚点矩形。
     *
     * 锚点不可用时按 [GuideStep.anchorLostPolicy] 的语义做两帧确认；
     * RecyclerView 滚动等 pending 状态会等待而不计入丢失。
     */
    private fun onPreDraw() {
        val step = step ?: return
        val rect = step.anchor.resolveRect()

        if (rect == null) {
            // RecyclerView 正在滚动到目标 item：等待，不当作锚点丢失。
            if (step.anchor.isPending()) {
                lastAnchorCheckWasPending = true
                missedFrames = 0
                schedulePendingCheck()
                return
            }
            if (lastAnchorCheckWasPending) {
                // 等待结束后再给一次机会，下一次检查就会走丢失逻辑。
                lastAnchorCheckWasPending = false
                missedFrames = 1
            }
            if (!initialCheckDone) {
                missedFrames++
                if (missedFrames >= 2) {
                    initialCheckDone = true
                    // KEEP 策略下仍然需要一个可见的气泡，允许回退到居中位置。
                    fallbackAllowed = true
                    requestLayout()
                    notifyAnchorLost()
                }
            } else {
                notifyAnchorLost()
            }
            return
        }

        lastAnchorCheckWasPending = false
        missedFrames = 0
        initialCheckDone = true
        anchorLostNotified = false

        val follow = step.followAnchor ?: followAnchorDefault
        if (follow || anchorScreenRect == null) {
            if (anchorScreenRect != rect) {
                anchorScreenRect = Rect(rect)
                requestLayout()
            }
        }
    }

    /**
     * 锚点等待布局时定期复查。RecyclerView 滚动完成后即使没有新的绘制，
     * 这里也能继续解析；超时后交给正常的 anchorLostPolicy 处理。
     */
    private fun schedulePendingCheck() {
        if (pendingCheckScheduled) return
        pendingCheckScheduled = true
        postDelayed({
            pendingCheckScheduled = false
            if (step != null) {
                onPreDraw()
            }
        }, PENDING_CHECK_INTERVAL_MS)
    }

    /** 通知宿主锚点已丢失；同一步骤只回调一次。 */
    private fun notifyAnchorLost() {
        if (anchorLostNotified) return
        anchorLostNotified = true
        anchorLostListener?.invoke()
    }

    /**
     * 测量 Overlay：自身始终占满窗口，只负责按 maxWidth 约束气泡宽度。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val step = step
        if (step == null) {
            setMeasuredDimension(widthSize, heightSize)
            return
        }

        val edge = step.tooltip.placement.edgePadding.px(density)
        val maxWidth = min(
            step.tooltip.maxWidth.px(density),
            (widthSize - edge * 2).coerceAtLeast(0)
        ).coerceAtLeast(1)

        bubble.measure(
            MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val measuredWidth =
            if (widthMode == MeasureSpec.UNSPECIFIED) bubble.measuredWidth else widthSize
        val measuredHeight =
            if (heightMode == MeasureSpec.UNSPECIFIED) bubble.measuredHeight else heightSize
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    /**
     * 定位气泡、挖洞和遮罩。
     *
     * 坐标链：锚点是屏幕坐标，先减去 Overlay 窗口左上角换成 Overlay 本地坐标，
     * 再交给 [GuidePositioner] 做纯几何计算。
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val step = step ?: return
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return

        val bubbleWidth = bubble.measuredWidth
        val bubbleHeight = bubble.measuredHeight
        if (bubbleWidth <= 0 || bubbleHeight <= 0) return

        getLocationOnScreen(originLocation)
        val screenAnchor = anchorScreenRect

        if (screenAnchor == null) {
            layoutFallback(width, height, bubbleWidth, bubbleHeight)
            return
        }

        resolveMaskRect(step, width, height, originLocation, screenAnchor)

        localAnchorRect.set(screenAnchor)
        localAnchorRect.offset(-originLocation[0], -originLocation[1])

        resolveHighlightRect(step, screenAnchor, localAnchorRect, originLocation, highlightRect)
        computeAvailableRect(width, height, availableRect)

        val result = GuidePositioner.place(
            anchorRect = localAnchorRect,
            highlightRect = highlightRect,
            tooltipWidth = bubbleWidth,
            tooltipHeight = bubbleHeight,
            availableRect = availableRect,
            placement = step.tooltip.placement,
            density = density,
            layoutDirection = layoutDirection
        )

        val rect = result.tooltipRect
        bubble.layout(rect.left, rect.top, rect.right, rect.bottom)
        tooltipRect.set(rect)
        resolvedSide = result.resolvedSide

        updateHole(step, highlightRect, width, height)
        arrowTip = computeArrowTip(step.tooltip.arrow, tooltipRect, highlightRect)
        reveal()
        notifyLocated(step, screenAnchor, originLocation)
    }

    /**
     * 锚点暂不可用时的兜底布局：气泡居中，遮罩保持整屏。
     * 只有 anchorLostPolicy = KEEP 时才会 reveal。
     */
    private fun layoutFallback(
        width: Int,
        height: Int,
        bubbleWidth: Int,
        bubbleHeight: Int
    ) {
        val x = (width - bubbleWidth) / 2
        val y = (height - bubbleHeight) / 2
        bubble.layout(x, y, x + bubbleWidth, y + bubbleHeight)
        tooltipRect.set(
            x.toFloat(),
            y.toFloat(),
            (x + bubbleWidth).toFloat(),
            (y + bubbleHeight).toFloat()
        )
        holeRect.setEmpty()
        animatedHoleRect.setEmpty()
        maskRect.set(0f, 0f, width.toFloat(), height.toFloat())
        arrowTip = null
        if (fallbackAllowed) {
            reveal()
        }
    }

    /**
     * 计算高亮/挖洞基准矩形（本地坐标）。
     *
     * 配置了 [GuideHighlight.holeRectProvider] 时，回调的入参和返回值都是
     * 屏幕坐标；否则使用“锚点 + padding”。
     */
    private fun resolveHighlightRect(
        step: GuideStep,
        screenAnchor: Rect,
        localAnchor: Rect,
        origin: IntArray,
        out: Rect
    ) {
        val provider = step.highlight.holeRectProvider
        if (provider != null) {
            providerAnchorRect.set(screenAnchor)
            out.set(provider(providerAnchorRect))
            out.offset(-origin[0], -origin[1])
        } else {
            out.set(localAnchor)
            val padding = step.highlight.padding.px(density)
            out.inset(-padding, -padding)
        }
    }

    /**
     * 填充遮罩范围 [maskRect]（本地坐标）。
     *
     * 默认铺满窗口；配置 provider 时使用其返回值并裁剪到窗口内。
     * provider 的入参和返回值都是屏幕坐标。
     */
    private fun resolveMaskRect(
        step: GuideStep,
        width: Int,
        height: Int,
        origin: IntArray,
        screenAnchor: Rect
    ) {
        val provider = step.highlight.maskRectProvider
        if (provider == null) {
            maskRect.set(0f, 0f, width.toFloat(), height.toFloat())
            return
        }
        providerContainerRect.set(
            origin[0],
            origin[1],
            origin[0] + width,
            origin[1] + height
        )
        providerAnchorRect.set(screenAnchor)
        providerMaskRect.set(provider(providerContainerRect, providerAnchorRect))
        providerMaskRect.offset(-origin[0], -origin[1])
        maskRect.set(providerMaskRect)
        if (!maskRect.intersect(0f, 0f, width.toFloat(), height.toFloat())) {
            maskRect.setEmpty()
        }
    }

    /**
     * 计算气泡可用区域（本地坐标），扣除状态栏、导航栏、刘海和 IME。
     * 遮罩仍然铺满整个窗口，所以状态栏会被正常压暗。
     */
    private fun computeAvailableRect(width: Int, height: Int, out: Rect) {
        val insets = ViewCompat.getRootWindowInsets(this)
        if (insets == null) {
            out.set(0, 0, width, height)
            return
        }
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime()
        )
        val left = bars.left.coerceIn(0, width)
        val top = bars.top.coerceIn(0, height)
        val right = (width - bars.right).coerceIn(left, width)
        val bottom = (height - bars.bottom).coerceIn(top, height)
        out.set(left, top, right, bottom)
    }

    /** 首次完成有效定位后再显示气泡；后续步骤切换会重新走一次淡入。 */
    private fun reveal() {
        if (positioned) return
        positioned = true
        if (step?.highlight?.animate == true) {
            bubble.animate()
                .alpha(1f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            bubble.alpha = 1f
        }
    }

    /**
     * 更新目标挖洞矩形；首次有效定位时启动入场动画。
     * 挖洞会被裁剪到 Overlay 窗口范围内。
     */
    private fun updateHole(
        step: GuideStep,
        highlightRect: Rect,
        width: Int,
        height: Int
    ) {
        if (step.highlight.shape == null) {
            holeRect.setEmpty()
            animatedHoleRect.setEmpty()
            return
        }
        holeRectF.set(highlightRect)
        if (!holeRectF.intersect(0f, 0f, width.toFloat(), height.toFloat())) {
            holeRect.setEmpty()
            animatedHoleRect.setEmpty()
            return
        }
        holeRect.set(holeRectF)
        if (!holeAnimationStarted) {
            startHoleAnimation(step.highlight.holeAnimation)
        } else if (holeAnimator == null) {
            // 动画已结束，洞直接跟随最新目标。
            animatedHoleRect.set(holeRect)
        }
    }

    /** 按配置启动挖洞入场动画；每个步骤只调用一次。 */
    private fun startHoleAnimation(animation: HoleAnimation) {
        holeAnimationStarted = true
        when (animation) {
            HoleAnimation.None -> {
                animatedHoleRect.set(holeRect)
                scrimAlphaFactor = 1f
            }

            is HoleAnimation.Scale ->
                animateHole(animation.duration, animation.startScale, animation.fadeScrim)

            is HoleAnimation.Fade ->
                animateHole(animation.duration, 1f, true)
        }
    }

    /**
     * 从 `startScale` 缩放洞矩形到 1，并可选淡入遮罩。
     * 动画结束时把绘制矩形精确复原到目标值，避免浮点误差。
     */
    private fun animateHole(duration: Long, startScale: Float, fadeScrim: Boolean) {
        holeAnimator?.cancel()
        if (duration <= 0L) {
            holeAnimator = null
            animatedHoleRect.set(holeRect)
            scrimAlphaFactor = 1f
            invalidate()
            return
        }

        val scaleFrom = startScale.coerceIn(0f, 1f)
        holeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedFraction
                val scale = scaleFrom + (1f - scaleFrom) * t
                val cx = holeRect.centerX()
                val cy = holeRect.centerY()
                val halfW = holeRect.width() * scale / 2f
                val halfH = holeRect.height() * scale / 2f
                animatedHoleRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
                scrimAlphaFactor = if (fadeScrim) t else 1f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    holeAnimator = null
                    animatedHoleRect.set(holeRect)
                    scrimAlphaFactor = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    /**
     * 几何变化时通知外部；只在矩形真正变化时回调一次。
     * 回调参数使用屏幕坐标，与 [GuideAnchor.resolveRect] 保持一致。
     */
    private fun notifyLocated(step: GuideStep, screenAnchor: Rect, origin: IntArray) {
        val tooltipScreen = Rect(
            tooltipRect.left.roundToInt() + origin[0],
            tooltipRect.top.roundToInt() + origin[1],
            tooltipRect.right.roundToInt() + origin[0],
            tooltipRect.bottom.roundToInt() + origin[1]
        )
        val holeScreen = if (step.highlight.shape == null || holeRect.isEmpty) {
            null
        } else {
            Rect(
                holeRect.left.roundToInt() + origin[0],
                holeRect.top.roundToInt() + origin[1],
                holeRect.right.roundToInt() + origin[0],
                holeRect.bottom.roundToInt() + origin[1]
            )
        }
        if (screenAnchor == lastLocatedAnchor &&
            holeScreen == lastLocatedHole &&
            tooltipScreen == lastLocatedTooltip
        ) {
            return
        }
        lastLocatedAnchor = Rect(screenAnchor)
        lastLocatedHole = holeScreen?.let(::Rect)
        lastLocatedTooltip = tooltipScreen
        stepLocatedListener?.invoke(
            session?.index ?: 0,
            Rect(screenAnchor),
            holeScreen?.let(::Rect),
            tooltipScreen
        )
    }

    /**
     * 先画遮罩和箭头，再画气泡内容。
     * 等待锚点（例如 RecyclerView 滚动中）时不提前压暗整屏。
     */
    override fun dispatchDraw(canvas: Canvas) {
        val step = step
        if (step != null && (positioned || fallbackAllowed)) {
            drawScrim(canvas, step)
            drawHoleOverlay(canvas, step)
            drawArrow(canvas, step)
        }
        super.dispatchDraw(canvas)
    }

    /**
     * 绘制遮罩：整个遮罩区域减去洞形状。
     *
     * 使用 [Path.Op.DIFFERENCE] 做布尔差集，不依赖路径方向和填充规则，
     * 矩形、圆角、椭圆和自定义 Path 都能稳定挖洞。
     */
    private fun drawScrim(canvas: Canvas, step: GuideStep) {
        val maskColor = step.highlight.maskColor
        val baseAlpha = Color.alpha(maskColor)
        if (baseAlpha == 0) return

        val alpha = (baseAlpha * scrimAlphaFactor).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        scrimPaint.color = (maskColor and 0x00FFFFFF) or (alpha shl 24)

        val shape = step.highlight.shape
        val hole = animatedHoleRect
        if (maskRect.isEmpty) return
        if (shape == null || hole.isEmpty) {
            canvas.drawRect(maskRect, scrimPaint)
            return
        }

        maskPath.reset()
        maskPath.addRect(maskRect, Path.Direction.CW)

        holeShapePath.reset()
        buildHoleShapePath(shape, hole)

        holeResultPath.reset()
        val subtracted = holeResultPath.op(maskPath, holeShapePath, Path.Op.DIFFERENCE)
        if (subtracted) {
            canvas.drawPath(holeResultPath, scrimPaint)
        } else {
            // 自定义路径异常时退回整块遮罩，至少不会出现“什么都没有”。
            canvas.drawRect(maskRect, scrimPaint)
        }
    }

    /**
     * 在洞形状上叠加高亮色。
     *
     * 与遮罩颜色独立：即使 maskColor 完全透明，也能单独提亮目标。
     */
    private fun drawHoleOverlay(canvas: Canvas, step: GuideStep) {
        val color = step.highlight.holeOverlayColor
        if (Color.alpha(color) == 0) return
        val shape = step.highlight.shape ?: return
        val hole = animatedHoleRect
        if (hole.isEmpty) return

        holeShapePath.reset()
        buildHoleShapePath(shape, hole)
        holeOverlayPaint.color = color
        canvas.drawPath(holeShapePath, holeOverlayPaint)
    }

    /**
     * 把洞形状追加到 [holeShapePath]。
     *
     * `hole` 是动画中的矩形；固定半径的圆形也按动画比例缩放，
     * 避免缩放动画期间圆洞“突然出现”。
     */
    private fun buildHoleShapePath(shape: HoleShape, hole: RectF) {
        when (shape) {
            HoleShape.Rect -> holeShapePath.addRect(hole, Path.Direction.CW)

            is HoleShape.RoundRect -> {
                val radius = shape.radius.px(density).toFloat()
                    .coerceAtMost(min(hole.width(), hole.height()) / 2f)
                holeShapePath.addRoundRect(hole, radius, radius, Path.Direction.CW)
            }

            is HoleShape.Circle -> {
                val fixedRadius = shape.radius
                val radius = if (fixedRadius != null) {
                    val scale = if (holeRect.width() > 0f) {
                        animatedHoleRect.width() / holeRect.width()
                    } else {
                        1f
                    }
                    fixedRadius.px(density).toFloat() * scale
                } else {
                    min(hole.width(), hole.height()) / 2f
                }
                holeShapePath.addCircle(
                    hole.centerX(),
                    hole.centerY(),
                    radius,
                    Path.Direction.CW
                )
            }

            HoleShape.Oval -> holeShapePath.addOval(hole, Path.Direction.CW)

            is HoleShape.Custom -> {
                customShapeRect.set(hole)
                shape.buildPath(customShapeRect, holeShapePath)
            }
        }
    }

    /**
     * 计算箭头尖端。
     *
     * 箭头是气泡的视觉元素，不参与 [GuidePositioner] 的纯定位计算；
     * 锚点中心落在气泡内时不绘制箭头。
     */
    private fun computeArrowTip(
        arrow: GuideArrow,
        tooltip: RectF,
        highlight: Rect
    ): PointF? {
        if (!arrow.enabled) return null
        val size = arrow.size.px(density)
        if (size <= 0) return null
        if (highlight.isEmpty) return null
        if (tooltip.contains(highlight.centerX().toFloat(), highlight.centerY().toFloat())) {
            return null
        }

        val inset = arrow.edgeInset.px(density)
        val centerX = highlight.centerX().toFloat()
        val centerY = highlight.centerY().toFloat()

        return when (resolvedSide) {
            Side.TOP -> PointF(
                clamp(centerX, tooltip.left + inset, tooltip.right - inset),
                tooltip.bottom + size
            )

            Side.BOTTOM -> PointF(
                clamp(centerX, tooltip.left + inset, tooltip.right - inset),
                tooltip.top - size
            )

            Side.START -> {
                val y = clamp(centerY, tooltip.top + inset, tooltip.bottom - inset)
                if (!isRtl) PointF(tooltip.right + size, y) else PointF(tooltip.left - size, y)
            }

            Side.END -> {
                val y = clamp(centerY, tooltip.top + inset, tooltip.bottom - inset)
                if (!isRtl) PointF(tooltip.left - size, y) else PointF(tooltip.right + size, y)
            }

            Side.AUTO -> null
        }
    }

    /** 把浮点值夹在 [min, max]；区间非法时取中点。 */
    private fun clamp(value: Float, min: Float, max: Float): Float =
        if (max < min) (min + max) / 2f else value.coerceIn(min, max)

    /**
     * 从气泡朝向锚点的一侧画三角箭头。
     * [resolvedSide] 表示气泡相对锚点的方位，因此 TOP 时从气泡底边伸出。
     */
    private fun drawArrow(canvas: Canvas, step: GuideStep) {
        val tip = arrowTip ?: return
        if (tooltipRect.isEmpty) return

        val size = step.tooltip.arrow.size.px(density).toFloat()
        if (size <= 0f) return

        val baseX: Float
        val baseY: Float
        when (resolvedSide) {
            Side.TOP -> {
                baseX = tip.x
                baseY = tooltipRect.bottom
            }

            Side.BOTTOM -> {
                baseX = tip.x
                baseY = tooltipRect.top
            }

            Side.START -> {
                baseX = if (!isRtl) tooltipRect.right else tooltipRect.left
                baseY = tip.y
            }

            Side.END -> {
                baseX = if (!isRtl) tooltipRect.left else tooltipRect.right
                baseY = tip.y
            }

            Side.AUTO -> return
        }

        val dx = tip.x - baseX
        val dy = tip.y - baseY
        val length = hypot(dx, dy)
        if (length <= 0f) return

        val halfWidth = size * 0.6f
        val perpendicularX = -dy / length * halfWidth
        val perpendicularY = dx / length * halfWidth

        arrowPaint.color = bubbleFillColor
        arrowPaint.style = Paint.Style.FILL
        arrowPath.reset()
        arrowPath.moveTo(baseX + perpendicularX, baseY + perpendicularY)
        arrowPath.lineTo(tip.x, tip.y)
        arrowPath.lineTo(baseX - perpendicularX, baseY - perpendicularY)
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
    }

    /**
     * 触摸分流：
     * 1. 气泡内：正常交给子 View（按钮可点）；
     * 2. 洞内且开启 passThroughTargetTouch：转发给宿主 Activity；
     * 3. 遮罩外且开启 passThroughOutsideMask：转发给宿主 Activity；
     * 4. 其余区域：由引导消费，按配置判断点击外部/点击洞。
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val step = step

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (tooltipRect.contains(x, y)) {
                    touchStartedInTooltip = true
                    return super.dispatchTouchEvent(event)
                }
                touchStartedInTooltip = false

                if (step?.passThroughTargetTouch == true &&
                    !holeRect.isEmpty &&
                    holeRect.contains(x, y)
                ) {
                    passThroughActive = true
                    forwardTouchToHost(event)
                    return true
                }

                // 遮罩范围之外（含遮罩为空的情况）不拦截，直接交给宿主页面。
                if (step?.passThroughOutsideMask == true &&
                    (maskRect.isEmpty || !maskRect.contains(x, y))
                ) {
                    passThroughActive = true
                    forwardTouchToHost(event)
                    return true
                }

                downPoint = PointF(x, y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (touchStartedInTooltip) return super.dispatchTouchEvent(event)
                if (passThroughActive) {
                    forwardTouchToHost(event)
                    return true
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (touchStartedInTooltip) {
                    touchStartedInTooltip = false
                    return super.dispatchTouchEvent(event)
                }
                if (passThroughActive) {
                    passThroughActive = false
                    forwardTouchToHost(event)
                    return true
                }
                val down = downPoint
                downPoint = null
                if (down != null && hypot(x - down.x, y - down.y) <= touchSlop) {
                    handleTapOutsideTooltip(x, y)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val wasInTooltip = touchStartedInTooltip
                touchStartedInTooltip = false
                downPoint = null
                if (passThroughActive) {
                    passThroughActive = false
                    forwardTouchToHost(event)
                    return true
                }
                return if (wasInTooltip) super.dispatchTouchEvent(event) else true
            }
        }
        return true
    }

    /**
     * 把触摸转发给宿主 Activity，实现“局部引导”。
     *
     * Dialog 窗口本身会拦截触摸，这里把 MotionEvent 的本地坐标换算成
     * Activity decorView 坐标后直接 dispatch。
     *
     * Overlay 始终消费这次手势，因此该函数没有返回值。
     */
    private fun forwardTouchToHost(event: MotionEvent) {
        val activity = findHostActivity() ?: return
        val decor = activity.window.decorView

        getLocationOnScreen(originLocation)
        decor.getLocationOnScreen(decorLocation)

        val copy = MotionEvent.obtain(event)
        try {
            copy.offsetLocation(
                (originLocation[0] - decorLocation[0]).toFloat(),
                (originLocation[1] - decorLocation[1]).toFloat()
            )
            decor.dispatchTouchEvent(copy)
        } finally {
            copy.recycle()
        }
    }

    /** 从 ContextWrapper 链里找宿主 Activity；找不到时不支持穿透。 */
    private fun findHostActivity(): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /** 点击气泡和洞以外的区域：可按配置前进或关闭引导。 */
    private fun handleTapOutsideTooltip(x: Float, y: Float) {
        val step = step ?: return
        if (step.advanceOnTargetClick && holeRect.contains(x, y)) {
            session?.next()
        } else if (dismissOnTouchOutside) {
            session?.dismiss()
        }
    }

    /** 首帧 attach 后补挂 Overlay 自己的 pre-draw 监听。 */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (step != null) {
            ensureOverlayPreDraw()
        }
    }

    /** detach 时停止监听和动画，避免持有已失效的窗口树。 */
    override fun onDetachedFromWindow() {
        releaseTracking()
        holeAnimator?.cancel()
        holeAnimator = null
        bubble.animate().cancel()
        super.onDetachedFromWindow()
    }

    /** 从主题里解析颜色属性，失败时使用 fallback。 */
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
        /** pending 锚点的复查间隔。 */
        const val PENDING_CHECK_INTERVAL_MS = 200L
    }
}
