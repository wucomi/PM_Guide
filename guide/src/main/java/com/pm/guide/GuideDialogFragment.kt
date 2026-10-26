package com.pm.guide

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.graphics.drawable.toDrawable

/**
 * 引导宿主。
 *
 * ## 实现约定
 * - 全屏透明 Dialog，`setDimAmount(0f)`，遮罩由 [GuideOverlayView] 自己绘制；
 * - 返回键由 [Builder.dismissOnBackPress] 控制；点击外部由
 *   [Builder.dismissOnTouchOutside] 控制，统一映射为 [GuideEndReason.DISMISSED]；
 * - 宿主 Fragment 的 view 销毁时自动结束，避免持有已失效的锚点 View；
 * - 步骤中包含 View 引用，因此不支持配置变更或进程恢复后自动重建。
 *   FragmentManager 重建时拿不到锚点，会直接结束。
 *
 * ## 入口
 * 入口只要求 `Context`，不要求 `FragmentActivity`：
 * - Fragment 中：`GuideDialogFragment.builder(requireContext()).show(this)`；
 * - Adapter 中：`GuideDialogFragment.builder(itemView.context).show(fragmentManager)`，
 *   `fragmentManager` 建议由宿主 Fragment 传入它的 `childFragmentManager`；
 * - 不使用 `showOnce` 时也可以 `builder()` 不传 Context。
 */
class GuideDialogFragment : DialogFragment() {

    /** 由 Builder 注入的配置；FragmentManager 恢复时为 null。 */
    internal var config: GuideConfig? = null

    /** 当前渲染层；onCreateView 创建，结束/销毁时释放。 */
    private var overlay: GuideOverlayView? = null

    /** 当前会话实现，负责步骤切换与结束原因。 */
    private var session: SessionImpl? = null

    /** 是否已经结束，保证 end 只执行一次。 */
    private var ended = false

    /** 恢复场景下配置已经丢失（步骤持有 View 引用，无法序列化），直接关闭。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = config
        if (config == null) {
            dismissAllowingStateLoss()
            return
        }
        isCancelable = config.dismissOnBackPress
    }

    /** 创建透明全屏 Dialog 外壳；遮罩和气泡由 [GuideOverlayView] 绘制。 */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.Theme_Guide_Dialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(false)
        }
    }

    /** 创建渲染层和会话，并立即展示第一步。 */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val config = config ?: return View(requireContext())
        val overlay = GuideOverlayView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            dismissOnTouchOutside = config.dismissOnTouchOutside
            followAnchorDefault = config.followAnchor
            anchorLostListener = { session?.onAnchorLost() }
            stepLocatedListener = { index, anchorRect, holeRect, tooltipRect ->
                config.listener?.onStepLocated(index, anchorRect, holeRect, tooltipRect)
            }
        }
        this.overlay = overlay
        val session = SessionImpl(config, overlay)
        this.session = session
        session.start()
        return overlay
    }

    /** 铺满整屏并保持系统栏/背景透明，让遮罩覆盖状态栏和导航栏。 */
    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return

        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        // 让系统栏背景由本窗口负责绘制，配合透明色才能露出遮罩。
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window.setDimAmount(0f)
        window.decorView.setPadding(0, 0, 0, 0)
    }

    /** 系统或调用方关闭 Dialog 时统一映射为 DISMISSED。 */
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        end(GuideEndReason.DISMISSED, dismiss = false)
    }

    /** 销毁时释放 Overlay；如果还没结束，按 HOST_DESTROYED 收尾。 */
    override fun onDestroy() {
        overlay?.release()
        overlay = null
        session = null
        if (!ended) {
            end(GuideEndReason.HOST_DESTROYED, dismiss = false)
        }
        super.onDestroy()
    }

    /** 宿主 Fragment 的 view 被销毁时由 Builder 的扩展入口调用。 */
    internal fun onHostViewDestroyed() {
        end(GuideEndReason.HOST_DESTROYED, dismiss = true)
    }

    /**
     * 统一结束：释放渲染层、必要时落盘并回调一次监听器。
     * @param dismiss 是否主动关闭 Dialog；系统已关闭时为 false。
     */
    private fun end(reason: GuideEndReason, dismiss: Boolean) {
        if (ended) return
        ended = true

        overlay?.release()
        overlay = null
        session = null

        val config = config
        if (config != null) {
            if (reason == GuideEndReason.FINISHED || reason == GuideEndReason.SKIPPED) {
                val key = config.showOnceKey
                if (key != null) {
                    config.storage?.markShown(key)
                }
            }
            config.listener?.onGuideEnded(reason)
        }

        if (dismiss) {
            dismissAllowingStateLoss()
        }
    }

    /**
     * 会话实现。
     *
     * ## 职责
     * 维护当前步骤，把 UI 操作映射到 [GuideSession] 接口。
     */
    private inner class SessionImpl(
        private val config: GuideConfig,
        private val overlay: GuideOverlayView
    ) : GuideSession {

        /** 本次引导的完整步骤列表。 */
        override val steps: List<GuideStep> get() = config.steps

        /** 当前步骤下标，0 开始。 */
        override var index: Int = 0
            private set

        /** 是否已经真正开始；结束后的调用会被忽略。 */
        private var started = false

        /** 展示第一步；空步骤列表直接按 FINISHED 结束。 */
        fun start() {
            if (steps.isEmpty()) {
                end(GuideEndReason.FINISHED, dismiss = true)
                return
            }
            started = true
            showStep(0)
        }

        /** 前进到下一步；最后一步等价于完成。 */
        override fun next() {
            if (!started) return
            if (index >= steps.lastIndex) finish() else goTo(index + 1)
        }

        /** 回到上一步；第一步时忽略。 */
        override fun previous() {
            if (started && index > 0) goTo(index - 1)
        }

        /** 跳转到指定步骤，越界会收敛到有效范围。 */
        override fun goTo(index: Int) {
            if (!started) return
            val target = index.coerceIn(0, steps.lastIndex)
            if (target == this.index) return
            showStep(target)
        }

        override fun finish() {
            end(GuideEndReason.FINISHED, dismiss = true)
        }

        override fun skip() {
            end(GuideEndReason.SKIPPED, dismiss = true)
        }

        /** 非正常关闭。 */
        override fun dismiss() {
            end(GuideEndReason.DISMISSED, dismiss = true)
        }

        /** 锚点失效时按当前步骤的 anchorLostPolicy 处理。 */
        fun onAnchorLost() {
            if (!started) return
            when (steps[index].anchorLostPolicy) {
                AnchorLostPolicy.DISMISS ->
                    end(GuideEndReason.ANCHOR_LOST, dismiss = true)

                AnchorLostPolicy.SKIP_NEXT ->
                    if (index >= steps.lastIndex) finish() else goTo(index + 1)

                AnchorLostPolicy.KEEP -> Unit
            }
        }

        /** 切换步骤：更新下标、重新绑定 Overlay，并触发回调。 */
        private fun showStep(target: Int) {
            index = target
            val step = steps[target]
            overlay.bind(step, this, target, steps.size)
            config.listener?.onStepShown(target, step)
            step.onShown?.invoke(this)
        }
    }

    /**
     * 引导配置入口。
     *
     * ## 使用约定
     * - 步骤按添加顺序执行，至少需要一个；
     * - 单次使用：调用 [show] 后不要复用同一个 Builder；
     * - 不使用 showOnce 时可以不传 Context。
     */
    class Builder internal constructor(private val context: Context?) {

        /** 已添加的步骤，按添加顺序执行。 */
        private val steps = mutableListOf<GuideStep>()

        /** 点击气泡和洞以外的区域是否关闭引导。 */
        private var dismissOnTouchOutside: Boolean = false

        /** 返回键是否关闭引导。 */
        private var dismissOnBackPress: Boolean = true

        /** 所有步骤的默认跟随开关，可被单个步骤覆盖。 */
        private var followAnchor: Boolean = true

        /** 生命周期回调。 */
        private var listener: GuideListener? = null

        /** 自定义 showOnce 存储。 */
        private var storage: GuideStorage? = null

        /** showOnce key；null 表示每次都展示。 */
        private var showOnceKey: String? = null

        /** 追加一个完整步骤。步骤按添加顺序执行，至少需要一个。 */
        fun addStep(step: GuideStep): Builder = apply {
            steps.add(step)
        }

        /**
         * 追加一个以 View 为锚点的步骤。
         *
         * `anchor` 直接传 View，避免多 Fragment 同名 id 的冲突。
         * 需要箭头、气泡样式或穿透选项时，用 [addStep] 传入完整 [GuideStep]。
         */
        fun addStep(
            anchor: View,
            content: GuideContent,
            position: GuidePosition = GuidePosition.Relative(),
            highlight: GuideHighlight = GuideHighlight()
        ): Builder = addStep(
            GuideStep(
                anchor = GuideAnchor.ViewAnchor(anchor),
                content = content,
                position = position,
                highlight = highlight
            )
        )

        /**
         * 所有步骤的默认跟随开关，可被 [GuideStep.followAnchor] 单独覆盖。默认 `true`。
         */
        fun followAnchor(enable: Boolean): Builder = apply {
            followAnchor = enable
        }

        /**
         * 点击气泡和洞以外的区域是否关闭引导。
         *
         * 默认 `false`：引导通常是模态的，误触外部不应直接跳过。
         */
        fun dismissOnTouchOutside(enable: Boolean): Builder = apply {
            dismissOnTouchOutside = enable
        }

        /** 返回键是否关闭引导，默认 `true`。关闭后按 [GuideEndReason.DISMISSED] 处理。 */
        fun dismissOnBackPress(enable: Boolean): Builder = apply {
            dismissOnBackPress = enable
        }

        /**
         * 只引导一次。
         *
         * - 展示前检查 [GuideStorage.wasShown]，已展示过则 [show] 直接返回 null；
         * - 仅在 [GuideEndReason.FINISHED] 和 [GuideEndReason.SKIPPED] 时标记为已展示；
         * - 返回键关闭、锚点丢失、宿主销毁都不算“看过”，下次仍会再次引导。
         *
         * 默认使用 SharedPreferences。若不传 Context 且未通过 [storage] 注入实现，
         * 调用 [show] 会抛出 [IllegalStateException]。
         */
        fun showOnce(key: String): Builder = apply {
            showOnceKey = key
        }

        /** 自定义“是否已展示”的存储实现。 */
        fun storage(storage: GuideStorage): Builder = apply {
            this.storage = storage
        }

        /** 设置生命周期回调。 */
        fun listener(listener: GuideListener): Builder = apply {
            this.listener = listener
        }

        /** 优先使用注入的存储，否则用 Context 创建默认 SharedPreferences 实现。 */
        private fun resolveStorage(): GuideStorage? =
            storage ?: context?.let { SharedPreferencesGuideStorage(it) }

        /** 构建 Fragment，由调用方自行决定何时、用哪个 FragmentManager 展示。 */
        fun build(): GuideDialogFragment {
            require(steps.isNotEmpty()) { "GuideDialogFragment 至少需要一个步骤" }

            val config = GuideConfig(
                steps = steps.toList(),
                dismissOnTouchOutside = dismissOnTouchOutside,
                dismissOnBackPress = dismissOnBackPress,
                followAnchor = followAnchor,
                listener = listener,
                storage = resolveStorage(),
                showOnceKey = showOnceKey
            )
            return GuideDialogFragment().apply { this.config = config }
        }

        /**
         * 构建并展示。
         *
         * @return 已展示的 Fragment；被 showOnce 拦截、FragmentManager 已保存状态、
         *         或同 tag 已有引导时返回 null。
         */
        @MainThread
        fun show(fm: FragmentManager, tag: String = TAG): GuideDialogFragment? {
            if (fm.isStateSaved) {
                Log.w(TAG, "FragmentManager 已保存状态，忽略本次引导")
                return null
            }

            val key = showOnceKey
            if (key != null) {
                val store = resolveStorage()
                    ?: throw IllegalStateException(
                        "使用 showOnce() 时需要传入 Context，或通过 storage() 提供 GuideStorage"
                    )
                if (store.wasShown(key)) return null
            }

            if (fm.findFragmentByTag(tag) != null) return null

            val fragment = build()
            fragment.show(fm, tag)
            return fragment
        }
    }

    companion object {
        /** 默认 Fragment tag。 */
        const val TAG: String = "GuideDialogFragment"

        /**
         * 任意 Context 均可：Activity、`requireContext()`、`itemView.context`。
         * Context 只用于创建默认的 showOnce 存储，不会长期持有 Activity。
         */
        fun builder(context: Context): Builder =
            Builder(context.applicationContext ?: context)

        /** 不使用 showOnce，或通过 [Builder.storage] 注入存储时可以不传 Context。 */
        fun builder(): Builder = Builder(null)
    }
}

/**
 * 以 Fragment 为宿主展示。
 *
 * 使用宿主的 `childFragmentManager`：宿主 Fragment 被销毁时引导一起结束。
 * 引导指向 Fragment 内部控件时优先使用这个入口。
 */
@MainThread
fun GuideDialogFragment.Builder.show(
    host: Fragment,
    tag: String = GuideDialogFragment.TAG
): GuideDialogFragment? {
    if (host.view == null) return null
    val guide = show(host.childFragmentManager, tag) ?: return null

    val owner = host.viewLifecycleOwner
    val observer = object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_DESTROY) {
                source.lifecycle.removeObserver(this)
                guide.onHostViewDestroyed()
            }
        }
    }
    owner.lifecycle.addObserver(observer)
    return guide
}

/** 以 Activity 为宿主展示，使用 `supportFragmentManager`。 */
@MainThread
fun GuideDialogFragment.Builder.show(
    host: FragmentActivity,
    tag: String = GuideDialogFragment.TAG
): GuideDialogFragment? = show(host.supportFragmentManager, tag)

/**
 * 引导配置集合。
 *
 * 仅在组件内部从 [GuideDialogFragment.Builder] 传给 Fragment。
 */
internal data class GuideConfig(
    /** 步骤列表，按顺序执行。 */
    val steps: List<GuideStep>,
    /** 点击气泡和洞以外的区域是否关闭。 */
    val dismissOnTouchOutside: Boolean,
    /** 返回键是否关闭。 */
    val dismissOnBackPress: Boolean,
    /** 所有步骤的默认跟随开关。 */
    val followAnchor: Boolean,
    /** 生命周期回调。 */
    val listener: GuideListener?,
    /** showOnce 存储；未配置 showOnce 时可为 null。 */
    val storage: GuideStorage?,
    /** showOnce key；null 表示每次都展示。 */
    val showOnceKey: String?
)
