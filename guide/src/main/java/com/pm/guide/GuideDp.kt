package com.pm.guide

import kotlin.math.roundToInt

/**
 * 密度无关尺寸。
 *
 * ## 说明
 * 组件内部按 `density` 转为像素，API 层不直接暴露 px，
 * 避免同一套引导参数在不同密度设备上出现位置偏差。
 */
@JvmInline
value class Dp(val value: Float)

/** 整数的 dp 简写。 */
val Int.dp: Dp get() = Dp(this.toFloat())

/** Float 的 dp 简写。 */
val Float.dp: Dp get() = Dp(this)

/** 转为像素。组件内部使用，调用方不需要关心。 */
internal fun Dp.px(density: Float): Int = (value * density).roundToInt()
