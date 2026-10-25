package com.pm.guide

import android.graphics.Rect
import android.util.Size

/**
 * 气泡相对锚点的摆放规则。
 *
 * ## 定位顺序
 * 1. 选边（[side] 或 [Side.AUTO] + [autoOrder]）
 * 2. 沿共享边对齐（[align]）
 * 3. 应用 [offsetX] / [offsetY]
 * 4. 按 [overflow] 翻边或平移，并满足 [edgePadding]
 *
 * 这个顺序属于公开契约，调用方可以据此预测结果，也方便单测。
 * 间距从**高亮外框**起算，而不是原始锚点矩形。
 *
 * ## 自定义定位
 * 设置 [customPosition] 后，1~4 步全部跳过，直接使用返回的矩形；
 * [overflow] 仍决定是否把结果夹回可用区域。
 */
data class GuidePlacement(
    /** 气泡位于锚点的哪一侧；[Side.AUTO] 时按 [autoOrder] 选择第一个放得下的方向。 */
    val side: Side = Side.AUTO,

    /**
     * 气泡与锚点共享边上的对齐方式。
     *
     * TOP/BOTTOM 作用在水平轴；START/END 作用在垂直轴。
     * START/END 遵循布局方向，RTL 下自动翻转。
     */
    val align: Alignment = Alignment.CENTER,

    /** 高亮外框到气泡最近边的间距；箭头尖端也落在这段间距内。 */
    val margin: Dp = 8.dp,

    /** 选边和对齐之后追加的偏移；正值分别向右、向下，不随 RTL 翻转。 */
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,

    /**
     * [Side.AUTO] 的候选顺序。
     *
     * 默认“下、上、右、左”，符合从上到下的阅读习惯。
     * 候选方向会去重，[Side.AUTO] 会被忽略。
     */
    val autoOrder: List<Side> = listOf(Side.BOTTOM, Side.TOP, Side.END, Side.START),

    /** 空间不足时的降级策略，见 [Overflow]。 */
    val overflow: Overflow = Overflow.FLIP_THEN_CLAMP,

    /** 气泡距可用区域边缘的最小留白，与 [GuideHighlight.padding] 独立。 */
    val edgePadding: Dp = 16.dp,

    /**
     * 完全自定义气泡位置。
     *
     * 一旦设置，[side]、[align]、[margin]、[offsetX]、[offsetY]、[autoOrder]
     * 都不再参与计算。回调参数与返回值都位于组件内部坐标系（铺满整屏，
     * 左上角为 0,0），必须在布局测量完成后同步返回，不能阻塞。
     *
     * - `anchorRect`：锚点可见矩形（可能因高亮 padding 而外扩前的原始矩形）。
     * - `tooltipSize`：气泡测量后的最终尺寸。
     * - `availableRect`：已扣除系统栏、刘海和键盘的可用区域。
     *
     * 返回的矩形会按 [overflow] 决定是否夹回 `availableRect`；箭头方向由组件
     * 根据气泡与锚点的相对位置自动推断。
     *
     * ```kotlin
     * customPosition = { _, size, available ->
     *     val left = available.centerX() - size.width / 2
     *     val top = available.bottom - size.height - marginPx
     *     Rect(left, top, left + size.width, top + size.height)
     * }
     * ```
     */
    val customPosition: ((anchorRect: Rect, tooltipSize: Size, availableRect: Rect) -> Rect)? = null
)

/**
 * 气泡相对锚点的方位。
 *
 * ## 说明
 * [AUTO] 按 [GuidePlacement.autoOrder] 依次尝试；
 * [START] / [END] 随布局方向自动翻转。
 */
enum class Side {
    /** 自动选边，按 [GuidePlacement.autoOrder] 依次尝试。 */
    AUTO,
    TOP,
    BOTTOM,

    /** 布局方向上的起始侧：LTR 为左，RTL 为右。 */
    START,

    /** 布局方向上的结束侧：LTR 为右，RTL 为左。 */
    END
}

/**
 * 气泡与锚点共享边上的对齐方式。
 *
 * ## 说明
 * TOP/BOTTOM 作用在水平轴；START/END 作用在垂直轴；
 * START/END 遵循布局方向。
 */
enum class Alignment {
    START,
    CENTER,
    END
}

/**
 * 空间不足时的降级策略。
 *
 * ## 说明
 * “放得下”指气泡完整落在可用区域内，并满足 [GuidePlacement.edgePadding]。
 */
enum class Overflow {
    /** 先翻到对侧；对侧也放不下时保持原侧并平移进可用区域。推荐默认值。 */
    FLIP_THEN_CLAMP,

    /** 只翻到对侧；仍放不下则允许气泡溢出可用区域。 */
    FLIP,

    /** 不翻边，直接沿气泡所在轴平移，直到满足 [GuidePlacement.edgePadding]。 */
    CLAMP,

    /** 完全不做约束，按计算结果摆放；仅用于特殊布局或调试。 */
    NONE
}