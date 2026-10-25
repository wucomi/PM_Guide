package com.pm.guide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding

/**
 * 气泡内容。
 *
 * ## 实现
 * - [Default]：内置标题 + 正文 + 操作按钮；
 * - [Custom]：inflate 自定义布局，使用 `findViewById`；
 * - [CustomBinding]：使用 ViewBinding / DataBinding，不需要 `findViewById`。
 *
 * 组件只负责把内容放进气泡并测量，交互通过 [GuideSession] 控制。
 */
sealed interface GuideContent {

    /**
     * 组件内置样式：标题 + 正文 + 操作按钮。
     *
     * ## 按钮语义
     * - 主按钮在非最后一步显示 [primaryLabel]，最后一步显示 [finishLabel]；
     * - [skipLabel] 为 null 时不显示跳过按钮，点击跳过触发 [GuideSession.skip]；
     * - [showIndicator] 控制 “1/3” 形式的步骤指示。
     *
     * ## 文字颜色
     * 默认使用主题的 `colorOnSurface` / `colorOnSurfaceVariant`；
     * 自定义深色气泡背景时，通过 [titleColor] / [messageColor] 覆盖。
     */
    data class Default(
        /** 可为空；为空时正文自动上移。 */
        val title: CharSequence? = null,

        /** 正文，必填。 */
        val message: CharSequence,

        /** 非最后一步的主按钮文案。 */
        val primaryLabel: CharSequence = "下一步",

        /** 最后一步的主按钮文案。 */
        val finishLabel: CharSequence = "知道了",

        /** 跳过按钮文案；null 表示不显示。 */
        val skipLabel: CharSequence? = "跳过",

        /** 是否显示 “1/3” 形式的步骤指示。 */
        val showIndicator: Boolean = true,

        /** 标题颜色；`null` 时使用 `TextAppearance.Guide.Title`。 */
        @ColorInt val titleColor: Int? = null,

        /** 正文颜色；`null` 时使用 `TextAppearance.Guide.Body`。 */
        @ColorInt val messageColor: Int? = null
    ) : GuideContent

    /**
     * 使用原始 View 的自定义布局。
     *
     * ## 用法
     * 组件会给布局套一层统一的圆角气泡背景，[layoutRes] 只需要描述内容本身，
     * 不需要再画气泡、箭头或外边距。
     *
     * ```kotlin
     * GuideContent.Custom(R.layout.my_guide) { view, session ->
     *     view.findViewById<Button>(R.id.next).setOnClickListener { session.next() }
     * }
     * ```
     *
     * ## 注意
     * - [bind] 在 inflate 之后、测量与定位之前调用；
     * - [bind] 中拿到的 [GuideSession] 只在本次引导期间有效；
     * - 布局宽度建议 `wrap_content`，最终会被 [GuideTooltip.maxWidth] 和可用区域限制。
     *
     * @param layoutRes 自定义布局资源。
     * @param bind 布局 inflate 后、测量前的绑定回调。
     */
    data class Custom(
        @param:LayoutRes val layoutRes: Int,
        val bind: (View, GuideSession) -> Unit = { _, _ -> }
    ) : GuideContent

    /**
     * 使用 ViewBinding / DataBinding 的自定义布局。
     *
     * ## 特点
     * 相比 [Custom]，这里直接拿到生成的 Binding 对象，不需要再
     * `findViewById`；每次步骤切换都会重新 inflate 并绑定一次。
     * `ViewDataBinding` 也实现了 [androidx.viewbinding.ViewBinding]，
     * 所以两种绑定方式都可以。
     *
     * ## 用法
     * ```kotlin
     * GuideContent.CustomBinding(
     *     create = { inflater, parent ->
     *         MyGuideBinding.inflate(inflater, parent, false)
     *     },
     *     bind = { binding, session ->
     *         binding.message.text = "第 ${session.index + 1} 步"
     *         binding.nextButton.setOnClickListener { session.next() }
     *     }
     * )
     * ```
     *
     * ## 注意
     * [create] 的第二个参数是组件内部的气泡容器，用于正确解析 `layout_width`；
     * 请使用 `inflate(inflater, parent, false)`，不要把 root 直接 attach 到 parent。
     *
     * @param create 创建 Binding；请使用 `inflate(inflater, parent, false)`。
     * @param bind 拿到 Binding 后填充内容并设置交互。
     */
    data class CustomBinding<T : ViewBinding>(
        val create: (LayoutInflater, ViewGroup) -> T,
        val bind: (T, GuideSession) -> Unit = { _, _ -> }
    ) : GuideContent
}
