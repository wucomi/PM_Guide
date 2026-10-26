package com.pm.guide

import android.graphics.Rect
import android.util.Size

/**
 * 气泡位置的两种模式。
 */
sealed interface GuidePosition {

    /**
     * 相对锚点的位置。
     *
     * ## 定位顺序
     * 1. 选边（[side] 或 [Side.AUTO] + [autoOrder]）
     * 2. 沿共享边对齐（[align]）
     * 3. 应用 [offsetX] / [offsetY]
     *
     * 间距从**高亮外框**起算，而不是原始锚点矩形。
     *
     * @param side 气泡位于锚点的哪一侧；[Side.AUTO] 时按 [autoOrder] 选择。
     * @param align 共享边上的对齐方式；START/END 遵循布局方向。
     * @param margin 高亮外框到气泡最近边的间距。
     * @param offsetX 选边和对齐后的额外横向偏移。
     * @param offsetY 选边和对齐后的额外纵向偏移。
     * @param autoOrder [Side.AUTO] 的候选顺序。
     */
    data class Relative(
        val side: Side = Side.AUTO,
        val align: Alignment = Alignment.CENTER,
        val margin: Dp = 8.dp,
        val offsetX: Dp = 0.dp,
        val offsetY: Dp = 0.dp,
        val autoOrder: List<Side> = listOf(Side.BOTTOM, Side.TOP, Side.END, Side.START)
    ) : GuidePosition

    /**
     * 完全自定义位置。
     *
     * provider 的参数和返回值都位于 Overlay 内部坐标系（铺满整屏，左上角 0,0），
     * 必须在布局测量完成后同步返回，不能阻塞。
     *
     * - `anchorRect`：锚点可见矩形（高亮 padding 外扩前的原始矩形）；
     * - `tooltipSize`：气泡测量后的最终尺寸；
     * - `availableRect`：已扣除系统栏、刘海和键盘的可用区域。
     *
     * 返回的矩形会按 [GuideTooltip.overflow] 决定是否夹回可用区域；
     * 箭头方向由组件根据气泡与锚点的相对位置自动推断。
     *
     * @param provider 返回气泡最终矩形。
     */
    data class Custom(
        val provider: (anchorRect: Rect, tooltipSize: Size, availableRect: Rect) -> Rect
    ) : GuidePosition

    companion object {
        /** 气泡在锚点上方，等价于 [of]`(Side.TOP, ...)`。 */
        fun above(
            margin: Dp = 8.dp,
            align: Alignment = Alignment.CENTER,
            offsetX: Dp = 0.dp,
            offsetY: Dp = 0.dp
        ): Relative = of(Side.TOP, margin, align, offsetX, offsetY)

        /** 气泡在锚点下方，等价于 [of]`(Side.BOTTOM, ...)`。 */
        fun below(
            margin: Dp = 8.dp,
            align: Alignment = Alignment.CENTER,
            offsetX: Dp = 0.dp,
            offsetY: Dp = 0.dp
        ): Relative = of(Side.BOTTOM, margin, align, offsetX, offsetY)

        /** 气泡在锚点起始侧（LTR 为左，RTL 为右）。 */
        fun startOf(
            margin: Dp = 8.dp,
            align: Alignment = Alignment.CENTER,
            offsetX: Dp = 0.dp,
            offsetY: Dp = 0.dp
        ): Relative = of(Side.START, margin, align, offsetX, offsetY)

        /** 气泡在锚点结束侧（LTR 为右，RTL 为左）。 */
        fun endOf(
            margin: Dp = 8.dp,
            align: Alignment = Alignment.CENTER,
            offsetX: Dp = 0.dp,
            offsetY: Dp = 0.dp
        ): Relative = of(Side.END, margin, align, offsetX, offsetY)

        /**
         * 按方位创建相对位置；[Side.AUTO] 时按默认顺序选边。
         */
        fun of(
            side: Side,
            margin: Dp = 8.dp,
            align: Alignment = Alignment.CENTER,
            offsetX: Dp = 0.dp,
            offsetY: Dp = 0.dp,
            autoOrder: List<Side> = listOf(Side.BOTTOM, Side.TOP, Side.END, Side.START)
        ): Relative = Relative(side, align, margin, offsetX, offsetY, autoOrder)

        /** 用回调返回气泡最终矩形；参数和返回值都在 Overlay 内部坐标系。 */
        fun custom(
            provider: (anchorRect: Rect, tooltipSize: Size, availableRect: Rect) -> Rect
        ): Custom = Custom(provider)
    }
}

/**
 * 气泡相对锚点的方位。
 *
 * ## 说明
 * [AUTO] 按 [GuidePosition.Relative.autoOrder] 依次尝试；
 * [START] / [END] 随布局方向自动翻转。
 */
enum class Side {
    /** 自动选边，按 [GuidePosition.Relative.autoOrder] 依次尝试。 */
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
 * “放得下”指气泡完整落在可用区域内，并满足 [GuideTooltip.edgePadding]。
 */
enum class Overflow {
    /** 先翻到对侧；对侧也放不下时保持原侧并平移进可用区域。推荐默认值。 */
    FLIP_THEN_CLAMP,

    /** 只翻到对侧；仍放不下则允许气泡溢出可用区域。 */
    FLIP,

    /** 不翻边，直接沿气泡所在轴平移，直到满足 [GuideTooltip.edgePadding]。 */
    CLAMP,

    /** 完全不做约束，按计算结果摆放；仅用于特殊布局或调试。 */
    NONE
}
