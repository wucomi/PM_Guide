package com.pm.guide

import androidx.annotation.ColorInt

/**
 * 气泡箭头配置。
 *
 * ## 行为
 * 箭头从气泡朝锚点可见矩形的中心方向伸出；气泡被 clamp 后，
 * 箭头沿气泡边缘滑动，并受 [edgeInset] 限制，避免压到圆角。
 *
 * @param enabled 是否绘制箭头；关闭后 [GuidePlacement.margin] 仍然生效。
 * @param size 箭头从气泡边缘伸出的长度。
 * @param edgeInset 箭头中心距气泡圆角的最小距离。
 */
data class GuideArrow(
    val enabled: Boolean = true,
    val size: Dp = 8.dp,
    val edgeInset: Dp = 12.dp
)

/**
 * 气泡承载的内容和样式。
 *
 * ## 组成
 * - [content]：内置内容或自定义布局；
 * - [placement]：相对锚点的位置；
 * - [arrow]：指向锚点的箭头；
 * - 背景色、圆角、描边：控制气泡外观。
 */
data class GuideTooltip(
    /** 气泡内容；默认内容自带下一步/完成/跳过按钮，自定义内容由调用方接管交互。 */
    val content: GuideContent,

    /** 相对锚点的摆放规则。 */
    val placement: GuidePlacement = GuidePlacement(),

    /** 气泡与锚点之间的箭头。 */
    val arrow: GuideArrow = GuideArrow(),

    /**
     * 气泡最大宽度。
     *
     * 实际宽度还会被“可用区域宽度 - 两侧 [GuidePlacement.edgePadding]”限制，
     * 小屏上不会因为该值过大而溢出。
     */
    val maxWidth: Dp = 320.dp,

    /**
     * 气泡背景色；`null` 时使用主题的 `colorSurface`。
     * 箭头会自动使用同一个颜色。
     */
    @param:ColorInt val backgroundColor: Int? = null,

    /** 气泡圆角半径。 */
    val cornerRadius: Dp = 16.dp,

    /** 气泡描边颜色；`null` 时使用主题的 `colorOutlineVariant`。 */
    @param:ColorInt val strokeColor: Int? = null,

    /** 气泡描边宽度；为 0 时不描边。 */
    val strokeWidth: Dp = 1.dp
)
