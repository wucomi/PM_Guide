package com.pm.guide

import android.view.View

/**
 * 单个引导步骤。
 *
 * ## 组成
 * - [anchor]：引导指向哪里；
 * - [tooltip]：气泡内容和位置；
 * - [highlight]：遮罩和挖洞；
 * - [passThroughTargetTouch] / [passThroughOutsideMask]：局部交互；
 * - [anchorLostPolicy]：锚点不可见时的行为。
 *
 * ## 快捷构造
 * - `GuideStep(view, content, position, highlight)`：View 锚点 + 默认气泡；
 * - `GuideStep(anchor, content, position, highlight)`：任意锚点 + 默认气泡。
 *
 * 需要箭头、宽度或气泡样式时使用主构造。
 *
 * ## 注意
 * 步骤持有 View 引用，不支持配置变更或进程恢复后自动重建。
 */
data class GuideStep(
    /** 引导目标；每次定位和跟随刷新都会重新解析。 */
    val anchor: GuideAnchor,

    /** 跟随锚点的气泡及摆放规则。 */
    val tooltip: GuideTooltip,

    /** 遮罩与高亮洞；默认启用。 */
    val highlight: GuideHighlight = GuideHighlight(),

    /**
     * 是否在锚点几何变化时重新定位。
     *
     * - `null`：使用 [GuideDialogFragment.Builder.followAnchor] 的全局默认值（true）。
     * - `true`：监听锚点及其父容器的布局、滚动和窗口 insets 变化。
     * - `false`：只在步骤展示时定位一次，适合锚点固定的页面。
     */
    val followAnchor: Boolean? = null,

    /**
     * 点击高亮区域是否等同于点击“下一步”。
     *
     * 实现方式是在洞的位置做点击命中，并调用 [GuideSession.next]；
     * 不是事件穿透，因此锚点自身的点击逻辑不会执行。
     * 需要锚点真正可点击时改用 [passThroughTargetTouch]。
     */
    val advanceOnTargetClick: Boolean = false,

    /**
     * 锚点不可见、宽高为 0 或 detach 时的处理方式。
     * 默认 [AnchorLostPolicy.DISMISS]，即锚点 detach 时直接关闭引导。
     */
    val anchorLostPolicy: AnchorLostPolicy = AnchorLostPolicy.DISMISS,

    /** 步骤完成定位并展示后的回调；此时气泡尺寸和位置已确定。 */
    val onShown: ((GuideSession) -> Unit)? = null,

    /**
     * 是否把落在高亮洞内的触摸**转发给宿主 Activity**，实现局部可交互引导。
     *
     * Dialog 窗口本身会拦截触摸，组件通过把 MotionEvent 重新分发到
     * `activity.window.decorView` 来让底部控件正常工作，因此：
     * - 锚点可以是一个按钮、开关、输入框等真实交互控件；
     * - 引导不会因为点击洞而关闭，仍由气泡上的按钮控制流程；
     * - 仅支持宿主是 Activity 的场景；Fragment 需挂在 Activity 上。
     *
     * 与 [advanceOnTargetClick] 互斥，开启本项后不再触发“点击洞进入下一步”。
     */
    val passThroughTargetTouch: Boolean = false,

    /**
     * 遮罩范围之外的触摸是否穿透给宿主 Activity。
     *
     * 只有配合 [GuideHighlight.maskRectProvider] 缩小遮罩范围时才有意义：
     * - `false`（默认）：整个屏幕仍然拦截触摸，保持模态；
     * - `true`：遮罩范围外不拦截，用户可以滚动、点击页面其他控件，
     *   适合“只解释局部区域”的引导。
     *
     * 与 [passThroughTargetTouch] 独立，可以同时开启。
     */
    val passThroughOutsideMask: Boolean = false
) {
    /**
     * 常见步骤的快捷构造：只配置内容、位置和挖洞。
     *
     * 需要箭头、宽度、气泡样式或穿透选项时，使用主构造搭配 [GuideTooltip]。
     */
    constructor(
        anchor: GuideAnchor,
        content: GuideContent,
        position: GuidePosition = GuidePosition.Relative(),
        highlight: GuideHighlight = GuideHighlight()
    ) : this(
        anchor = anchor,
        tooltip = GuideTooltip(content = content, position = position),
        highlight = highlight
    )

    /**
     * 以 View 为锚点的快捷构造，省掉 [GuideAnchor.ViewAnchor] 包装。
     *
     * 只定位一次或需要自定义锚点时，使用主构造。
     */
    constructor(
        view: View,
        content: GuideContent,
        position: GuidePosition = GuidePosition.Relative(),
        highlight: GuideHighlight = GuideHighlight()
    ) : this(
        anchor = GuideAnchor.ViewAnchor(view),
        content = content,
        position = position,
        highlight = highlight
    )
}

/**
 * 锚点失效时的处理策略。
 *
 * ## 说明
 * 组件在锚点不可见、宽高为 0 或 detach 时按这里处理。
 */
enum class AnchorLostPolicy {
    /** 立即结束引导，[GuideListener.onGuideEnded] 收到 [GuideEndReason.ANCHOR_LOST]。 */
    DISMISS,

    /** 跳过当前步骤并前进；已经是最后一步时按 FINISHED 结束。 */
    SKIP_NEXT,

    /** 保持上一次的位置继续显示；适合锚点短暂不可见但会恢复的场景。 */
    KEEP
}
