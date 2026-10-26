package com.pm.guide

import androidx.annotation.ColorInt

/**
 * 气泡承载的内容和样式。
 *
 * ## 组成
 * - [content]：内置内容或自定义布局；
 * - [position]：相对锚点或完全自定义位置；
 * - [overflow] / [edgePadding]：空间不足时的降级策略和边缘留白；
 * - [arrow]：指向锚点的箭头；
 * - [maxWidth]：固定宽度 / 占满屏幕 / 占满遮罩；
 * - 背景色、圆角、描边：控制气泡外观。
 *
 * ## 自定义气泡/箭头
 * 自定义布局用 `@id/guide_bubble` 标记气泡卡片，用 `@id/guide_arrow` 标记箭头。
 * 库以气泡卡片的尺寸应用定位和 overflow，并平移整个自定义布局让卡片落在
 * 计算出的位置；箭头再按 `resolvedSide` 放到卡片对应侧边、沿边缘滑动到挖孔
 * 中心附近并旋转。自定义内容默认不绘制库背景。
 */
data class GuideTooltip(
    /** 气泡内容；默认内容自带下一步/完成/跳过按钮，自定义内容由调用方接管交互。 */
    val content: GuideContent,

    /** 相对锚点的位置，或完全自定义的位置。 */
    val position: GuidePosition = GuidePosition.Relative(),

    /** 空间不足时的降级策略，对 [GuidePosition.Relative] 和 [GuidePosition.Custom] 都生效。 */
    val overflow: Overflow = Overflow.FLIP_THEN_CLAMP,

    /** 气泡距可用区域边缘的最小留白，同时用于 [GuideWidth.FillScreen] / [GuideWidth.FillMask]。 */
    val edgePadding: Dp = 16.dp,

    /** 气泡与锚点之间的箭头。 */
    val arrow: GuideArrow = GuideArrow(),

    /**
     * 气泡宽度模式：
     * - [GuideWidth.Fixed]：固定最大宽度；
     * - [GuideWidth.FillScreen]：占满可用屏幕宽度；
     * - [GuideWidth.FillMask]：占满遮罩区域宽度。
     *
     * Fill 模式会把气泡宽度按 EXACTLY 测量，并预留两侧 [edgePadding]。
     */
    val maxWidth: GuideWidth = GuideWidth.Fixed(320.dp),

    /**
     * 气泡背景色。
     *
     * - 默认内容：`null` 时使用主题的 `colorSurface`；
     * - 自定义内容：`null` 时不绘制库背景，由自定义布局自己实现卡片/描边/箭头。
     *   显式设置颜色时仍会绘制库背景。
     */
    @param:ColorInt val backgroundColor: Int? = null,

    /** 气泡圆角半径。 */
    val cornerRadius: Dp = 16.dp,

    /** 气泡描边颜色；`null` 时使用主题的 `colorOutlineVariant`。 */
    @param:ColorInt val strokeColor: Int? = null,

    /** 气泡描边宽度；为 0 时不描边。 */
    val strokeWidth: Dp = 1.dp
)

/**
 * 气泡箭头配置。
 *
 * ## 行为
 * 默认箭头由 Canvas 绘制：从气泡朝锚点方向伸出；气泡被 clamp 后，
 * 箭头沿气泡边缘滑动，并受 [edgeInset] 限制，避免压到圆角。
 *
 * 自定义布局里提供 `@id/guide_arrow` 时，默认箭头不再绘制，
 * 库会按 `resolvedSide` 重新定位并旋转这个 View；气泡与挖孔的间距
 * 会自动不小于箭头长度，避免箭头伸进挖孔。
 *
 * @param enabled 是否显示箭头；为 `false` 时默认箭头和自定义 `guide_arrow` 都隐藏。
 * @param size 默认箭头从气泡边缘伸出的长度；自定义箭头尺寸由 View 决定。
 * @param edgeInset 箭头中心距气泡圆角的最小距离。
 */
data class GuideArrow(
    val enabled: Boolean = true,
    val size: Dp = 8.dp,
    val edgeInset: Dp = 12.dp
)

/**
 * 气泡宽度模式。
 */
sealed interface GuideWidth {

    /**
     * 固定最大宽度；内容更窄时气泡会收缩到内容宽度。
     *
     * @param max 最大宽度。
     */
    data class Fixed(val max: Dp = 320.dp) : GuideWidth

    /**
     * 占满可用屏幕宽度（减去两侧 [GuideTooltip.edgePadding]）。
     * 气泡宽度会按 EXACTLY 测量。
     */
    data object FillScreen : GuideWidth

    /**
     * 占满遮罩区域宽度（减去两侧 [GuideTooltip.edgePadding]）。
     * 没有配置 [GuideHighlight.maskRectProvider] 时等同 [FillScreen]。
     * 气泡宽度会按 EXACTLY 测量。
     */
    data object FillMask : GuideWidth
}