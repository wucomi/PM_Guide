package com.pm.guide

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView

/**
 * 引导锚点：描述“引导指向哪里”。
 *
 * ## 职责
 * - 每次定位都重新读取真实几何，不缓存结果；
 * - 支持 View、自定义矩形和 RecyclerView item。
 *
 * ## 坐标约定
 * 返回值一律是**屏幕坐标**。DialogFragment 位于独立 Window，
 * Activity 中 View 的 `getLocationInWindow` 与 Dialog 的坐标不可直接比较，
 * 只有屏幕坐标可以跨窗口运算。
 *
 * ## 失效
 * 返回 `null` 表示锚点当前不可见，组件按 [GuideStep.anchorLostPolicy] 处理。
 */
sealed interface GuideAnchor {

    /**
     * 返回锚点当前在屏幕上的**可见**矩形。
     *
     * - 返回 `null`：View 未 attach、宽高为 0、完全滚出可视区，或已 detach。
     *   组件会按 [GuideStep.anchorLostPolicy] 处理。
     * - 返回部分矩形：View 被父容器裁剪，只返回可见部分；
     *   高亮和气泡都应基于这个矩形，而不是 View 的完整 bounds。
     * - 实现方不要缓存结果。
     */
    fun resolveRect(): Rect?

    /**
     * 锚点是否正在等待布局/滚动完成，暂时无法定位。
     *
     * 组件在等待期间不会把锚点当作“已丢失”，也不会触发
     * [GuideStep.anchorLostPolicy]。默认 `false`；
     * [RecyclerViewItemAnchor] 在滚动到目标 item 时会返回 `true`。
     */
    fun isPending(): Boolean = false

    /**
     * 直接持有 View 的锚点。
     *
     * ## 适用场景
     * - 页面内生命周期稳定的控件；
     * - 不涉及 RecyclerView item 复用。
     *
     * ## 注意
     * 引导显示期间 View 必须保持 attached；detach 时默认关闭引导。
     * 动态列表请使用 [RecyclerViewItemAnchor]。
     *
     * @param view 必须保持 attached 的锚点 View。
     */
    data class ViewAnchor(val view: View) : GuideAnchor {
        override fun resolveRect(): Rect? {
            if (!view.isAttachedToWindow) return null
            val rect = Rect()
            return if (view.getGlobalVisibleRect(rect) && !rect.isEmpty) rect else null
        }
    }

    /**
     * 自定义矩形锚点。
     *
     * ## 适用场景
     * 测试、Canvas 内容，或没有对应 View 的目标。
     *
     * ## 注意
     * provider 每次跟随刷新都会调用，必须主线程、低开销，不要做布局测量。
     * 返回 `null` 的语义与 [ViewAnchor] 不可见一致。
     *
     * @param provider 每次刷新时返回矩形；返回 `null` 视为不可见。
     */
    data class RectAnchor(val provider: () -> Rect?) : GuideAnchor {
        override fun resolveRect(): Rect? = provider()
    }

    /**
     * RecyclerView item 锚点。
     *
     * ## 特点
     * - 按 **adapter position** 解析，不持有 itemView；
     * - item 被回收、复用、重新绑定时仍然指向同一个 position；
     * - item 不可见时可通过 [scrollToItem] 自动滚动，并通过 [isPending]
     *   让引导等待，不会立即按“锚点丢失”关闭；
     * - 适合引导列表第 N 项、动态列表、横向/纵向 RecyclerView。
     *
     * ## 用法
     * ```kotlin
     * GuideStep(
     *     anchor = GuideAnchor.RecyclerViewItemAnchor(
     *         recyclerView = binding.itemRecycler,
     *         position = 2,
     *         childId = R.id.itemAction
     *     )
     * )
     * ```
     *
     * ## 注意
     * - 必须在主线程解析；
     * - [position] 是 adapter position，不是 layout position；
     * - [childId] 找不到时回退到整个 itemView；
     * - 引导展示期间 adapter 的 position 语义应保持稳定；
     * - item 已可见且不需要自动滚动时，可把 [scrollToItem] 设为 `false`。
     */
    class RecyclerViewItemAnchor @JvmOverloads constructor(
        /** 目标 RecyclerView。 */
        val recyclerView: RecyclerView,
        /** 目标 item 的 adapter position。 */
        val position: Int,
        /** item 内部控件 id；[View.NO_ID] 表示整个 itemView。 */
        @param:IdRes val childId: Int = View.NO_ID,
        /** 目标不可见时是否自动滚动到它。 */
        val scrollToItem: Boolean = true
    ) : GuideAnchor {

        /** 首次发起自动滚动的时间；0 表示当前不在等待。 */
        private var waitingSince: Long = 0L

        /**
         * 解析当前 item 内目标控件的可见矩形。
         *
         * holder 不存在或目标不可见时，会发起一次 scrollToPosition 并返回 null，
         * 由 [isPending] 标记为“等待中”。
         */
        override fun resolveRect(): Rect? {
            val adapter = recyclerView.adapter ?: return null
            if (position !in 0 until adapter.itemCount) {
                waitingSince = 0L
                return null
            }

            val holder = recyclerView.findViewHolderForAdapterPosition(position)
            val target = holder?.let { itemHolder ->
                if (childId != View.NO_ID) {
                    itemHolder.itemView.findViewById<View>(childId) ?: itemHolder.itemView
                } else {
                    itemHolder.itemView
                }
            }

            if (target != null && target.isAttachedToWindow) {
                val rect = Rect()
                if (target.getGlobalVisibleRect(rect) && !rect.isEmpty) {
                    waitingSince = 0L
                    return rect
                }
            }

            if (!scrollToItem) {
                waitingSince = 0L
                return null
            }

            // 只发起一次滚动，之后等待 RecyclerView 完成布局。
            if (waitingSince == 0L) {
                waitingSince = SystemClock.uptimeMillis()
                recyclerView.scrollToPosition(position)
            }
            return null
        }

        /** 自动滚动后的等待窗口；超时后由 anchorLostPolicy 接管。 */
        override fun isPending(): Boolean {
            val since = waitingSince
            return scrollToItem &&
                since != 0L &&
                SystemClock.uptimeMillis() - since < PENDING_TIMEOUT_MS
        }

        private companion object {
            /** 超时后交给 anchorLostPolicy 处理，避免异常数据导致引导一直等待。 */
            const val PENDING_TIMEOUT_MS = 1500L
        }
    }
}
