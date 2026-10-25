package com.pm.guide

import android.graphics.Rect
import androidx.annotation.MainThread

/**
 * 引导会话，用于在自定义内容和回调中控制流程。
 *
 * ## 线程
 * 所有方法都必须在主线程调用。
 *
 * ## 失效
 * 引导结束后会话失效，继续调用不会产生效果。
 */
interface GuideSession {
    /** 本次会话的完整步骤列表，只读。 */
    val steps: List<GuideStep>

    /** 当前步骤下标，从 0 开始。 */
    val index: Int

    /** 前进到下一步；最后一步调用等价于 [finish]。 */
    @MainThread
    fun next()

    /** 回到上一步；第一步调用无效果。 */
    @MainThread
    fun previous()

    /** 跳转到指定步骤，越界时收敛到有效范围。 */
    @MainThread
    fun goTo(index: Int)

    /** 正常完成引导，触发 [GuideEndReason.FINISHED]。 */
    @MainThread
    fun finish()

    /** 用户主动跳过，触发 [GuideEndReason.SKIPPED]。 */
    @MainThread
    fun skip()

    /** 非正常关闭，触发 [GuideEndReason.DISMISSED]。 */
    @MainThread
    fun dismiss()
}

/**
 * 引导生命周期回调。
 *
 * ## 回调时机
 * - [onStepShown]：步骤切换后触发，回退/跳转也会再次触发；
 * - [onStepLocated]：矩形真正变化时触发；
 * - [onGuideEnded]：每次会话最多回调一次。
 */
interface GuideListener {
    /** 某一步完成定位并展示；此时 [GuideSession.index] 已更新为新步骤。 */
    fun onStepShown(index: Int, step: GuideStep) {}

    /**
     * 某一步的几何信息发生变化（首次定位、锚点滚动、动画结束等）。
     *
     * 所有矩形均为**屏幕坐标**，可直接与 `GuideAnchor.resolveRect()` 的结果比较。
     * 只有矩形真正变化时才会回调。
     *
     * 回调发生在布局阶段。如果需要在回调里调用 [GuideSession.next] /
     * [GuideSession.goTo] 等方法，请先 `view.post { ... }` 到下一帧，避免在
     * 布局过程中重新绑定步骤。
     *
     * @param anchorRect 锚点当前的可见矩形。
     * @param holeRect 实际挖洞矩形；没有挖洞（[GuideHighlight.shape] 为 null）时为 null。
     * @param tooltipRect 气泡最终所在矩形。
     */
    fun onStepLocated(
        index: Int,
        anchorRect: Rect,
        holeRect: Rect?,
        tooltipRect: Rect
    ) {
    }

    /** 引导结束，`reason` 表示结束原因。 */
    fun onGuideEnded(reason: GuideEndReason) {}
}

/**
 * 引导结束原因。
 *
 * ## 落盘规则
 * 只有 [FINISHED] 和 [SKIPPED] 会被 showOnce 视为“已看过”；
 * 其余原因下次仍会重新引导。
 */
enum class GuideEndReason {
    /** 走完最后一步或调用 [GuideSession.finish]。 */
    FINISHED,

    /** 用户点击跳过或调用 [GuideSession.skip]。 */
    SKIPPED,

    /** 返回键、点击外部、外部调用 dismiss 等。 */
    DISMISSED,

    /** 锚点不可见 / detach，且策略为 [AnchorLostPolicy.DISMISS]。 */
    ANCHOR_LOST,

    /** 宿主 Fragment 的 view 被销毁，或 Activity 正在结束。 */
    HOST_DESTROYED
}
