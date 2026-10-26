package com.pm.guide

import android.graphics.Rect
import android.util.Size
import android.view.View
import kotlin.math.abs

/**
 * 无状态气泡定位器。
 *
 * ## 职责
 * - 只做几何计算，不访问 View/Context，不修改任何 UI；
 * - 实现 [GuidePosition] 的相对定位和自定义定位。
 *
 * ## 可复用性
 * - 调用方可以单测自己的 [GuideTooltip.position] 配置；
 * - 自定义气泡实现可以直接复用，而不是重写一套坐标逻辑。
 *
 * ## 坐标
 * 所有输入输出矩形使用同一个坐标系。组件内部会把屏幕坐标换算成
 * Dialog 内容层坐标后传入；纯函数本身不关心原点是屏幕还是父容器。
 */
object GuidePositioner {

    /**
     * 定位输出。
     *
     * ## 保证
     * [tooltipRect] 已按 [GuideTooltip.overflow] 校正；
     * [resolvedSide] 一定不是 [Side.AUTO]。
     *
     * @param tooltipRect 气泡最终矩形，已按 [GuideTooltip.overflow] 校正。
     * @param resolvedSide 实际生效的方向；请求 [Side.AUTO] 时据此获知最终选了哪一侧。
     */
    data class Result(
        val tooltipRect: Rect,
        val resolvedSide: Side
    )

    /**
     * 计算气泡的最终位置。
     *
     * ## 保证
     * - 空间足够时，返回的气泡矩形完整落在 `availableRect` 内；
     * - 默认 [Overflow.FLIP_THEN_CLAMP] 下优先翻边，仍放不下才平移；
     * - [Result.resolvedSide] 与实际摆放方向一致，不会返回 [Side.AUTO]。
     *
     * ## 线程
     * 纯函数，可在任意线程调用；相同输入得到相同输出。
     * 使用 [GuidePosition.Custom] 时，纯度由调用方提供的回调决定。
     *
     * @param anchorRect 锚点可见矩形。
     * @param highlightRect 高亮/挖洞基准矩形，决定气泡间距的起算位置。
     * @param tooltipWidth 气泡测量后的宽度。
     * @param tooltipHeight 气泡测量后的高度。
     * @param availableRect 气泡允许占用的区域，已扣除系统栏和键盘。
     * @param position 相对位置或完全自定义位置。
     * @param overflow 空间不足时的降级策略。
     * @param edgePadding 气泡距可用区域边缘的最小留白。
     * @param density 当前屏幕密度。
     * @param layoutDirection 布局方向，用于解析 START/END。
     */
    fun place(
        anchorRect: Rect,
        highlightRect: Rect,
        tooltipWidth: Int,
        tooltipHeight: Int,
        availableRect: Rect,
        position: GuidePosition,
        overflow: Overflow,
        edgePadding: Dp,
        density: Float,
        layoutDirection: Int = View.LAYOUT_DIRECTION_LTR
    ): Result {
        val edge = edgePadding.px(density)
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val width = tooltipWidth.coerceAtLeast(0)
        val height = tooltipHeight.coerceAtLeast(0)
        val available = Rect(availableRect)
        val highlight = Rect(highlightRect)

        return when (position) {
            is GuidePosition.Custom -> {
                val tooltipSize = Size(width, height)
                val custom = Rect(position.provider(Rect(anchorRect), tooltipSize, available))
                val finalRect = when (overflow) {
                    Overflow.CLAMP, Overflow.FLIP_THEN_CLAMP ->
                        clampToAvailable(custom, available, edge)

                    Overflow.FLIP, Overflow.NONE -> custom
                }
                Result(
                    tooltipRect = finalRect,
                    resolvedSide = inferSide(finalRect, highlight, rtl)
                )
            }

            is GuidePosition.Relative -> placeRelative(
                position = position,
                highlight = highlight,
                available = available,
                width = width,
                height = height,
                edge = edge,
                overflow = overflow,
                density = density,
                rtl = rtl
            )
        }
    }

    /** 计算相对位置模式：选边、对齐、偏移和溢出处理。 */
    private fun placeRelative(
        position: GuidePosition.Relative,
        highlight: Rect,
        available: Rect,
        width: Int,
        height: Int,
        edge: Int,
        overflow: Overflow,
        density: Float,
        rtl: Boolean
    ): Result {
        val margin = position.margin.px(density)
        val offsetX = position.offsetX.px(density)
        val offsetY = position.offsetY.px(density)

        val candidates: List<Side> =
            if (position.side == Side.AUTO) {
                position.autoOrder
                    .filter { it != Side.AUTO }
                    .distinct()
                    .ifEmpty { listOf(Side.BOTTOM, Side.TOP, Side.END, Side.START) }
            } else {
                listOf(position.side)
            }

        fun raw(side: Side): Rect = rawRect(
            side = side,
            highlight = highlight,
            tooltipWidth = width,
            tooltipHeight = height,
            margin = margin,
            offsetX = offsetX,
            offsetY = offsetY,
            align = position.align,
            rtl = rtl
        )

        fun fits(rect: Rect): Boolean =
            rect.left >= available.left + edge &&
                    rect.top >= available.top + edge &&
                    rect.right <= available.right - edge &&
                    rect.bottom <= available.bottom - edge

        /**
         * 只检查翻边方向所在轴的空间。
         *
         * 横向放不下不应该阻止纵向翻边，反之亦然；另一轴交给
         * [clampToAvailable] 平移处理。
         */
        fun fitsAlongSide(side: Side): Boolean {
            if (side == Side.AUTO) return false
            val rect = raw(side)
            return when (side) {
                Side.TOP, Side.BOTTOM ->
                    rect.top >= available.top + edge && rect.bottom <= available.bottom - edge

                Side.START, Side.END ->
                    rect.left >= available.left + edge && rect.right <= available.right - edge

                Side.AUTO -> false
            }
        }

        val resolvedSide: Side =
            if (position.side == Side.AUTO) {
                candidates.firstOrNull { fits(raw(it)) }
                    ?: candidates.firstOrNull { fitsAlongSide(it) }
                    ?: candidates.maxByOrNull {
                        freeSpace(it, highlight, available, margin, rtl)
                    }
                    ?: Side.BOTTOM
            } else {
                val preferred = position.side
                val opposite = preferred.opposite()
                when (overflow) {
                    Overflow.FLIP, Overflow.FLIP_THEN_CLAMP ->
                        if (!fitsAlongSide(preferred) &&
                            opposite != null &&
                            fitsAlongSide(opposite)
                        ) {
                            opposite
                        } else {
                            preferred
                        }

                    Overflow.CLAMP, Overflow.NONE -> preferred
                }
            }

        var tooltipRect = raw(resolvedSide)
        if (overflow == Overflow.CLAMP || overflow == Overflow.FLIP_THEN_CLAMP) {
            tooltipRect = clampToAvailable(tooltipRect, available, edge)
        }

        return Result(
            tooltipRect = tooltipRect,
            resolvedSide = resolvedSide
        )
    }

    /**
     * 按 side/align/offset 计算未做溢出约束的原始位置。
     * 坐标空间与传入的 highlight 一致。
     */
    private fun rawRect(
        side: Side,
        highlight: Rect,
        tooltipWidth: Int,
        tooltipHeight: Int,
        margin: Int,
        offsetX: Int,
        offsetY: Int,
        align: Alignment,
        rtl: Boolean
    ): Rect {
        val rect = Rect()
        when (side) {
            Side.TOP, Side.AUTO -> {
                rect.top = highlight.top - margin - tooltipHeight
                rect.bottom = rect.top + tooltipHeight
                val left = horizontalLeft(highlight, tooltipWidth, align, rtl)
                rect.left = left
                rect.right = left + tooltipWidth
            }

            Side.BOTTOM -> {
                rect.top = highlight.bottom + margin
                rect.bottom = rect.top + tooltipHeight
                val left = horizontalLeft(highlight, tooltipWidth, align, rtl)
                rect.left = left
                rect.right = left + tooltipWidth
            }

            Side.START -> {
                val left = if (!rtl) {
                    highlight.left - margin - tooltipWidth
                } else {
                    highlight.right + margin
                }
                rect.left = left
                rect.right = left + tooltipWidth
                val top = verticalTop(highlight, tooltipHeight, align)
                rect.top = top
                rect.bottom = top + tooltipHeight
            }

            Side.END -> {
                val left = if (!rtl) {
                    highlight.right + margin
                } else {
                    highlight.left - margin - tooltipWidth
                }
                rect.left = left
                rect.right = left + tooltipWidth
                val top = verticalTop(highlight, tooltipHeight, align)
                rect.top = top
                rect.bottom = top + tooltipHeight
            }
        }
        rect.offset(offsetX, offsetY)
        return rect
    }

    /** 水平轴对齐：START/END 在 RTL 下取反。 */
    private fun horizontalLeft(
        highlight: Rect,
        tooltipWidth: Int,
        align: Alignment,
        rtl: Boolean
    ): Int = when (align) {
        Alignment.START -> if (!rtl) highlight.left else highlight.right - tooltipWidth
        Alignment.END -> if (!rtl) highlight.right - tooltipWidth else highlight.left
        Alignment.CENTER -> highlight.centerX() - tooltipWidth / 2
    }

    /** 垂直轴对齐。 */
    private fun verticalTop(
        highlight: Rect,
        tooltipHeight: Int,
        align: Alignment
    ): Int = when (align) {
        Alignment.START -> highlight.top
        Alignment.END -> highlight.bottom - tooltipHeight
        Alignment.CENTER -> highlight.centerY() - tooltipHeight / 2
    }

    /** 指定方向上，锚点与可用区域边缘之间的剩余空间。 */
    private fun freeSpace(
        side: Side,
        highlight: Rect,
        available: Rect,
        margin: Int,
        rtl: Boolean
    ): Int = when (side) {
        Side.TOP -> highlight.top - margin - available.top
        Side.BOTTOM -> available.bottom - highlight.bottom - margin
        Side.START ->
            if (!rtl) {
                highlight.left - margin - available.left
            } else {
                available.right - highlight.right - margin
            }

        Side.END ->
            if (!rtl) {
                available.right - highlight.right - margin
            } else {
                highlight.left - margin - available.left
            }

        Side.AUTO -> 0
    }

    /**
     * 自定义布局没有显式方位，按气泡与高亮区域的相对位置推断箭头方向。
     * 返回的是语义方向，因此 RTL 下物理左右会映射到 END / START。
     */
    private fun inferSide(tooltip: Rect, highlight: Rect, rtl: Boolean): Side {
        val dx = tooltip.centerX() - highlight.centerX()
        val dy = tooltip.centerY() - highlight.centerY()
        return if (abs(dx) > abs(dy)) {
            val tooltipOnRight = dx >= 0
            when {
                rtl && tooltipOnRight -> Side.START
                rtl -> Side.END
                tooltipOnRight -> Side.END
                else -> Side.START
            }
        } else {
            if (dy >= 0) Side.BOTTOM else Side.TOP
        }
    }

    /**
     * 把矩形平移进可用区域，并保证距边缘不小于 `edge`。
     * 如果矩形本身比可用区域还大，则从左上角开始放置。
     */
    private fun clampToAvailable(rect: Rect, available: Rect, edge: Int): Rect {
        val out = Rect(rect)
        val minLeft = available.left + edge
        val maxRight = available.right - edge
        if (out.width() > maxRight - minLeft) {
            out.left = minLeft
            out.right = out.left + rect.width()
        } else {
            if (out.left < minLeft) out.offset(minLeft - out.left, 0)
            if (out.right > maxRight) out.offset(maxRight - out.right, 0)
        }

        val minTop = available.top + edge
        val maxBottom = available.bottom - edge
        if (out.height() > maxBottom - minTop) {
            out.top = minTop
            out.bottom = out.top + rect.height()
        } else {
            if (out.top < minTop) out.offset(0, minTop - out.top)
            if (out.bottom > maxBottom) out.offset(0, maxBottom - out.bottom)
        }
        return out
    }

    /** 取对侧方位；AUTO 没有对侧。 */
    private fun Side.opposite(): Side? = when (this) {
        Side.TOP -> Side.BOTTOM
        Side.BOTTOM -> Side.TOP
        Side.START -> Side.END
        Side.END -> Side.START
        Side.AUTO -> null
    }
}
