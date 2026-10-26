package com.pm.guide

import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.ColorInt

/**
 * 高亮（挖洞 + 遮罩）配置。
 *
 * ## 绘制方式
 * 遮罩由组件自己在 Dialog 内容层绘制，不使用 Dialog 的 dim，
 * 因此可以精确控制透明度，也能保证挖洞区域不会被二次压暗。
 *
 * ## 可配置项
 * - 洞形状：[shape]；
 * - 洞大小/位置：[holeRectProvider]；
 * - 遮罩范围：[maskRectProvider]；
 * - 遮罩颜色：[maskColor]；
 * - 挖洞高亮色：[holeOverlayColor]；
 * - 入场动画：[holeAnimation]。
 */
data class GuideHighlight(
    /**
     * 洞的形状；传 `null` 表示不挖洞，只显示整层遮罩。
     * 洞的位置由锚点可见矩形、[padding] 或 [holeRectProvider] 决定。
     */
    val shape: HoleShape? = HoleShape.RoundRect(12.dp),

    /**
     * 锚点矩形向外扩展的距离，避免高亮圈紧贴控件边缘。
     * 该值同时参与 [GuidePosition.Relative.margin] 的起算位置。
     *
     * 使用 [holeRectProvider] 时，[padding] 不再生效，由回调自行决定外扩距离。
     */
    val padding: Dp = 4.dp,

    /**
     * 遮罩颜色，包含 alpha。alpha 为 0 时不绘制遮罩。
     * 默认约 70% 黑，可通过该参数实现任意背景色。
     */
    @param:ColorInt val maskColor: Int = 0xB3000000.toInt(),

    /**
     * 步骤切换时是否对气泡做淡入动画。
     * 锚点移动时洞和气泡即时跟随，不做逐帧动画，避免拖动/滚动时产生拖影。
     * 洞自身的入场动画见 [holeAnimation]。
     */
    val animate: Boolean = true,

    /**
     * 挖洞的入场动画，只在步骤切换后的第一次有效定位时播放。
     * 默认 [HoleAnimation.None]；锚点移动时不重复播放。
     */
    val holeAnimation: HoleAnimation = HoleAnimation.None,

    /**
     * 自定义挖洞矩形，用于“洞的大小与锚点不完全一致”的场景。
     *
     * 回调参数是锚点当前的**可见屏幕矩形**，返回值同样要求是**屏幕坐标**。
     * 返回值可以比锚点大、小、偏移，甚至与锚点无关。
     * 返回的矩形同时作为气泡定位的基准（[GuidePosition.Relative.margin] 从它起算）。
     *
     * 为 `null` 时使用“锚点矩形 + [padding]”。
     *
     * ```kotlin
     * // 以锚点中心为基准，做一个 160dp x 80dp 的固定大小洞
     * holeRectProvider = { anchor ->
     *     Rect(
     *         anchor.centerX() - halfWidthPx,
     *         anchor.centerY() - halfHeightPx,
     *         anchor.centerX() + halfWidthPx,
     *         anchor.centerY() + halfHeightPx
     *     )
     * }
     * ```
     */
    val holeRectProvider: ((anchorRect: Rect) -> Rect)? = null,

    /**
     * 自定义**遮罩范围**。为 `null` 时遮罩铺满整个窗口（默认）。
     *
     * 与 [holeRectProvider] 不同，这个回调决定“压暗哪一块”，而不是“哪里挖洞”。
     * 适合做局部引导：只压暗目标附近，页面其他区域保持正常亮度。
     *
     * 回调参数：
     * - `containerRect`：当前引导窗口的完整屏幕矩形；
     * - `anchorRect`：锚点当前的可见矩形。
     *
     * 两者都是**屏幕坐标**，返回值也必须是屏幕坐标；返回值会被裁剪到窗口范围内。
     * 返回空矩形表示不绘制遮罩。
     *
     * 遮罩范围之外默认仍然会拦截触摸；需要让页面继续可交互时，同时设置
     * [GuideStep.passThroughOutsideMask]。
     *
     * ```kotlin
     * // 只压暗锚点周围的一块区域
     * maskRectProvider = { _, anchor ->
     *     Rect(anchor).apply { inset(-halfWidthPx, -halfHeightPx) }
     * }
     * ```
     */
    val maskRectProvider: ((containerRect: Rect, anchorRect: Rect) -> Rect)? = null,

    /**
     * 挖洞区域的高亮色，包含 alpha；默认透明。
     *
     * 会用该颜色在洞的形状上再叠加一层，适合：
     * - 半透明白色提亮目标（如 `0x33FFFFFF`）；
     * - 品牌色/警告色强调目标（如 `0x2200E5FF`）。
     *
     * 与 [maskColor] 独立：即使遮罩完全透明，这里依然会绘制。
     */
    @param:ColorInt val holeOverlayColor: Int = Color.TRANSPARENT
)

/**
 * 高亮洞的形状。
 *
 * ## 尺寸
 * 所有形状都基于“洞矩形”计算；[GuideHighlight.holeRectProvider] 可以改变这个矩形。
 *
 * ## 动画
 * 所有形状都会随 [HoleAnimation] 的缩放一起变化。
 */
sealed interface HoleShape {
    /** 贴合洞矩形的直角矩形。 */
    data object Rect : HoleShape

    /**
     * 圆角矩形。
     *
     * @param radius 圆角半径；超过短边一半时按短边内切处理。
     */
    data class RoundRect(val radius: Dp) : HoleShape

    /**
     * 圆形。
     *
     * @param radius 圆半径；`null` 时取洞矩形的内切圆。
     */
    data class Circle(val radius: Dp? = null) : HoleShape

    /** 椭圆，始终填满整个洞矩形。 */
    data object Oval : HoleShape

    /**
     * 自定义形状。
     *
     * 回调只需要把“洞”的路径追加到 `path` 上，组件已经追加了整屏矩形并使用
     * `EVEN_ODD` 填充规则，因此不需要（也不应该）绘制整屏。
     *
     * `rect` 是洞矩形，可直接用于构造路径；它可能因为动画而小于最终尺寸。
     *
     * @param buildPath 把洞形状追加到传入的 Path。
     */
    data class Custom(val buildPath: (rect: RectF, path: Path) -> Unit) : HoleShape
}

/**
 * 挖洞的入场动画。
 *
 * ## 播放时机
 * 只在步骤首次定位时播放；锚点移动（滚动、键盘弹出）不会重播。
 */
sealed interface HoleAnimation {

    /** 不做动画，直接显示最终大小。 */
    data object None : HoleAnimation

    /**
     * 从 `startScale` 缩放到 1。
     *
     * 以洞的中心为原点缩放，因此圆形会表现为扩散，矩形会表现为放大。
     *
     * @param duration 动画时长（毫秒）。
     * @param startScale 起始缩放，取值 0..1；0 表示从中心点长出来。
     * @param fadeScrim 是否同时把遮罩从透明淡入到 [GuideHighlight.maskColor]。
     */
    data class Scale(
        val duration: Long = 240L,
        val startScale: Float = 0.6f,
        val fadeScrim: Boolean = true
    ) : HoleAnimation

    /**
     * 只淡入遮罩，洞的大小保持不变。
     *
     * @param duration 动画时长（毫秒）。
     */
    data class Fade(val duration: Long = 240L) : HoleAnimation
}
