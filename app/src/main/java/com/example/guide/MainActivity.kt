package com.example.guide

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.guide.databinding.ActivityMainBinding
import com.example.guide.databinding.ViewGuideCustomDemoBinding
import com.pm.guide.Alignment
import com.pm.guide.Dp
import com.pm.guide.GuideAnchor
import com.pm.guide.GuideArrow
import com.pm.guide.GuideContent
import com.pm.guide.GuideDialogFragment
import com.pm.guide.GuideEndReason
import com.pm.guide.GuideHighlight
import com.pm.guide.GuideListener
import com.pm.guide.GuidePosition
import com.pm.guide.GuideStep
import com.pm.guide.GuideStorage
import com.pm.guide.GuideTooltip
import com.pm.guide.GuideWidth
import com.pm.guide.HoleAnimation
import com.pm.guide.HoleShape
import com.pm.guide.Side
import com.pm.guide.dp
import kotlin.math.roundToInt

/**
 * PM Guide API 使用手册。
 *
 * ## 页面结构
 * - 顶部 hero 卡片播放完整三步引导；
 * - 下方 14 张能力卡片，覆盖方位、挖洞、样式、穿透、RecyclerView 等能力；
 * - 页面结构、按钮和代码片段全部声明在 `activity_main.xml`。
 *
 * ## 职责
 * - 负责按钮点击绑定、引导参数配置和 RecyclerView 初始化；
 * - 每张卡片的按钮本身就是锚点，不需要固定的“靶子”区域。
 */
class MainActivity : AppCompatActivity() {

    /** 由 XML 生成的主页面绑定。 */
    private lateinit var binding: ActivityMainBinding

    /** 穿透示例累计点击次数，用于证明洞内事件真的作用于底部按钮。 */
    private var tapCount = 0

    /** showOnce 示例使用的持久化实现。 */
    private val demoStorage by lazy { DemoStorage() }

    /** 所有示例共用的引导生命周期日志，写到 hero 卡片底部。 */
    private val guideListener = object : GuideListener {
        override fun onStepShown(index: Int, step: GuideStep) {
            log("onStepShown($index) · anchor=${step.anchor.javaClass.simpleName}")
        }

        override fun onStepLocated(
            index: Int,
            anchorRect: Rect,
            holeRect: Rect?,
            tooltipRect: Rect
        ) {
            log("onStepLocated($index) · anchor=$anchorRect · hole=$holeRect")
        }

        override fun onGuideEnded(reason: GuideEndReason) {
            log("onGuideEnded · $reason")
        }
    }

    /**
     * 初始化页面：绑定视图、配置 RecyclerView、绑定所有示例按钮，
     * 首帧布局完成后播放一次欢迎引导。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        bindSections()

        binding.root.post { showWelcomeOnce() }
    }

    /** 配置顶栏，并把右上角菜单的“重置引导状态”接到存储清理。 */
    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_handbook)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reset -> {
                    demoStorage.clear()
                    log("已重置 showOnce / 欢迎引导状态")
                    true
                }

                else -> false
            }
        }
    }

    /**
     * 配置 RecyclerView item 引导示例。
     *
     * item 操作按钮的点击只写日志；真正的引导由下面的
     * “引导第 N 个”按钮触发，锚点使用 adapter position。
     */
    private fun setupRecyclerView() {
        binding.itemRecycler.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.itemRecycler.adapter = DemoItemAdapter { position ->
            log("点击了 Item ${position + 1}")
        }
    }

    /** 把 XML 中声明的每个按钮接到对应的引导实现。 */
    private fun bindSections() {
        binding.heroStart.setOnClickListener { showHeroGuide() }

        binding.stepA.setOnClickListener { showStepGuide(binding.stepA, binding.stepB, binding.stepC) }
        binding.stepB.setOnClickListener { showStepGuide(binding.stepA, binding.stepB, binding.stepC) }
        binding.stepC.setOnClickListener { showStepGuide(binding.stepA, binding.stepB, binding.stepC) }
        binding.stepPlay.setOnClickListener { showStepGuide(binding.stepA, binding.stepB, binding.stepC) }

        binding.placeEnd.setOnClickListener { showPlacement(Side.END, it) }
        binding.placeTop.setOnClickListener { showPlacement(Side.TOP, it) }
        binding.placeBottom.setOnClickListener { showPlacement(Side.BOTTOM, it) }
        binding.placeAuto.setOnClickListener { showPlacement(Side.AUTO, it) }
        binding.placeStart.setOnClickListener { showPlacement(Side.START, it) }
        binding.placeAlign.setOnClickListener {
            showPlacement(Side.BOTTOM, it, alignMode = Alignment.END, offsetY = 12.dp)
        }

        binding.maskDark.setOnClickListener { showMask(it, 0xE6000000.toInt(), "深色 90%") }
        binding.maskBlue.setOnClickListener { showMask(it, 0xCC1565C0.toInt(), "蓝色 80%") }
        binding.maskPurple.setOnClickListener { showMask(it, 0xCC6A1B9A.toInt(), "紫色 80%") }
        binding.maskNone.setOnClickListener { showMask(it, Color.TRANSPARENT, "无遮罩") }
        binding.maskHoleGlow.setOnClickListener { showHoleGlow(it) }

        binding.bubbleStyle.setOnClickListener { showBubbleStyle(it) }

        binding.shapeRect.setOnClickListener {
            showHoleShape(it, HoleShape.Rect, "Rect", paddingDp = 10)
        }
        binding.shapeRound.setOnClickListener {
            showHoleShape(it, HoleShape.RoundRect(14.dp), "RoundRect", paddingDp = 10)
        }
        binding.shapeCircle.setOnClickListener {
            showHoleShape(it, HoleShape.Circle(), "Circle", paddingDp = 10)
        }
        binding.shapeOval.setOnClickListener {
            showHoleShape(it, HoleShape.Oval, "Oval", paddingDp = 10)
        }
        binding.shapeStar.setOnClickListener {
            showHoleShape(it, starShape(), "Custom", paddingDp = 10)
        }

        binding.sizeDefault.setOnClickListener { showHoleSizeDefault(it) }
        binding.sizeLarge.setOnClickListener { showHoleSizeLarge(it) }
        binding.sizeFixed.setOnClickListener { showHoleSizeFixed(it) }
        binding.sizeAnchor.setOnClickListener { showHoleSizeAnchorBased(it) }

        binding.animNone.setOnClickListener {
            showHoleAnimation(it, HoleAnimation.None, "None")
        }
        binding.animScale.setOnClickListener {
            showHoleAnimation(
                it,
                HoleAnimation.Scale(duration = 420L, startScale = 0.2f, fadeScrim = true),
                "Scale"
            )
        }
        binding.animFade.setOnClickListener {
            showHoleAnimation(it, HoleAnimation.Fade(420L), "Fade")
        }

        binding.passCounter.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(GuideDialogFragment.TAG) == null) {
                showPassThrough(binding.passCounter)
            } else {
                // 引导打开时，洞内的点击会被转发到这里
                tapCount++
                binding.passCounter.text = getString(R.string.demo_counter_value, tapCount)
                log("穿透点击 +1 · 总计 $tapCount")
            }
        }
        binding.passAdvance.setOnClickListener { showAdvanceOnTargetClick(it) }

        binding.localMask.setOnClickListener { showLocalMask(it) }

        binding.customA.setOnClickListener { showCustomContent(binding.customA, binding.customB) }
        binding.customB.setOnClickListener { showCustomContent(binding.customA, binding.customB) }

        binding.customPosition.setOnClickListener { showCustomPosition(it) }
        binding.fillScreenWidth.setOnClickListener { showFillScreenWidth(it) }
        binding.fillMaskWidth.setOnClickListener { showFillMaskWidth(it) }
        binding.rectAnchor.setOnClickListener { showRectAnchor(it) }

        binding.showOnce.setOnClickListener { showOnceDemo(it) }
        binding.showOnceReset.setOnClickListener {
            demoStorage.reset(ONCE_KEY)
            log("showOnce: 状态已重置，可以再次展示")
        }

        binding.guideItemThird.setOnClickListener { showRecyclerItemGuide(position = 2) }
        binding.guideItemLast.setOnClickListener { showRecyclerItemGuide(position = 7) }
    }

    // region 引导实现

    /** 创建一个带统一日志监听器的 Builder。 */
    private fun newGuide(): GuideDialogFragment.Builder =
        GuideDialogFragment.builder(this).listener(guideListener)

    /**
     * 展示引导。若已有引导在显示，先关闭旧的，避免同一个 tag 被占用。
     */
    private fun present(builder: GuideDialogFragment.Builder) {
        val fm = supportFragmentManager
        (fm.findFragmentByTag(GuideDialogFragment.TAG) as? DialogFragment)?.let {
            it.dismissAllowingStateLoss()
            runCatching { fm.executePendingTransactions() }
        }
        builder.show(fm)
    }

    /** 把最新事件写到 hero 卡片底部（固定高度，不会影响布局）。 */
    private fun log(message: String) {
        binding.guideLog.text = message
    }

    /** 单步演示统一使用“完成”文案，避免每个方法重复 5 个默认参数。 */
    private fun doneContent(title: CharSequence, message: CharSequence): GuideContent.Default =
        GuideContent.Default(
            title = title,
            message = message,
            primaryLabel = "完成",
            finishLabel = "完成",
            skipLabel = null,
            showIndicator = false
        )

    /** 展示单个步骤，省掉重复的 `present(newGuide().addStep(...))` 包裹。 */
    private fun presentStep(step: GuideStep) {
        present(newGuide().addStep(step))
    }

    /**
     * 单步演示：气泡默认在锚点上方。
     * `configure` 用于追加 passThroughTargetTouch / advanceOnTargetClick 等开关。
     */
    private fun showTopStep(
        anchor: View,
        title: CharSequence,
        message: CharSequence,
        highlight: GuideHighlight,
        configure: (GuideStep) -> GuideStep = { it }
    ) {
        presentStep(
            configure(
                GuideStep(
                    view = anchor,
                    content = doneContent(title, message),
                    position = GuidePosition.Relative(side = Side.TOP),
                    highlight = highlight
                )
            )
        )
    }

    /** hero 卡片的完整三步引导：标题 → 说明 → 按钮。 */
    private fun showHeroGuide() {
        present(
            newGuide()
                .addStep(
                    binding.heroTitle,
                    GuideContent.Default(
                        title = "标题锚点",
                        message = "气泡会围绕这个标题显示。"
                    ),
                    GuidePosition.Relative(side = Side.BOTTOM, align = Alignment.CENTER)
                )
                .addStep(
                    binding.heroSubtitle,
                    GuideContent.Default(
                        title = "说明锚点",
                        message = "点击下一步会重新解析锚点并重新定位。"
                    ),
                    GuidePosition.Relative(side = Side.BOTTOM)
                )
                .addStep(
                    binding.heroStart,
                    GuideContent.Default(
                        title = "按钮锚点",
                        message = "最后一步指向按钮本身。",
                        primaryLabel = "完成",
                        finishLabel = "完成"
                    ),
                    GuidePosition.Relative(side = Side.TOP)
                )
        )
    }

    /** 首次进入自动播放一次，只指向 hero 内部始终可见的元素。 */
    private fun showWelcomeOnce() {
        GuideDialogFragment.builder(this)
            .addStep(
                binding.heroTitle,
                GuideContent.Default(
                    title = "欢迎使用 PM Guide",
                    message = "这是首次进入自动播放的引导，只会出现一次。"
                ),
                GuidePosition.Relative(side = Side.BOTTOM)
            )
            .addStep(
                binding.heroSubtitle,
                GuideContent.Default(
                    title = "向下就是 API 手册",
                    message = "每张卡片里的按钮本身就是锚点，点一下就能看到对应能力。",
                    primaryLabel = "完成",
                    finishLabel = "完成"
                ),
                GuidePosition.Relative(side = Side.BOTTOM)
            )
            .listener(guideListener)
            .storage(demoStorage)
            .showOnce(WELCOME_KEY)
            .show(supportFragmentManager)
    }

    /**
     * 三步引导，分别指向同一行的 A/B/C 三个芯片。
     * 用于验证“点下一步后重新定位”以及 [GuideListener] 的步骤回调。
     */
    private fun showStepGuide(a: View, b: View, c: View) {
        present(
            newGuide()
                .addStep(
                    GuideStep(
                        view = a,
                        content = GuideContent.Default(
                            title = "第 1 步 · 锚点 A",
                            message = "点击下一步切换到锚点 B。"
                        ),
                        position = GuidePosition.Relative(side = Side.BOTTOM),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(10.dp),
                            padding = 6.dp
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = b,
                        content = GuideContent.Default(
                            title = "第 2 步 · 锚点 B",
                            message = "新锚点会被重新解析，气泡不会停在 alpha = 0。"
                        ),
                        position = GuidePosition.Relative(side = Side.TOP),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(10.dp),
                            padding = 6.dp
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = c,
                        content = GuideContent.Default(
                            title = "第 3 步 · 锚点 C",
                            message = "最后一步，主按钮会触发 session.finish()。",
                            primaryLabel = "完成",
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.Relative(side = Side.BOTTOM),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(10.dp),
                            padding = 6.dp
                        )
                    )
                )
        )
    }

    /**
     * 指向 RecyclerView 指定 adapter position 的 item 操作按钮。
     *
     * 目标不在屏幕上时会自动滚动；[GuideAnchor.RecyclerViewItemAnchor]
     * 在等待布局期间返回 pending，引导不会误判为锚点丢失。
     */
    private fun showRecyclerItemGuide(position: Int) {
        presentStep(
            GuideStep(
                anchor = GuideAnchor.RecyclerViewItemAnchor(
                    recyclerView = binding.itemRecycler,
                    position = position,
                    childId = R.id.itemAction
                ),
                content = doneContent(
                    "RecyclerView Item ${position + 1}",
                    "锚点按 adapter position 解析，item 滚动或复用后仍然有效。"
                ),
                position = GuidePosition.Relative(side = Side.TOP),
                highlight = GuideHighlight(
                    shape = HoleShape.RoundRect(12.dp),
                    padding = 8.dp
                )
            )
        )
    }

    /**
     * 单步方位演示。默认按点击的按钮作为锚点；
     * START / END 使用更窄的气泡，方便在屏幕左右两侧放下。
     */
    private fun showPlacement(
        side: Side,
        anchor: View,
        alignMode: Alignment = Alignment.CENTER,
        offsetX: Dp = 0.dp,
        offsetY: Dp = 0.dp
    ) {
        val sidePlacement = side == Side.START || side == Side.END
        presentStep(
            GuideStep(
                anchor = GuideAnchor.ViewAnchor(anchor),
                tooltip = GuideTooltip(
                    content = doneContent("Side.$side", sideLabel(side)),
                    position = GuidePosition.Relative(
                        side = side,
                        align = alignMode,
                        offsetX = offsetX,
                        offsetY = offsetY
                    ),
                    maxWidth = GuideWidth.Fixed(if (sidePlacement) 130.dp else 220.dp)
                ),
                highlight = GuideHighlight(
                    shape = HoleShape.RoundRect(10.dp),
                    padding = 6.dp
                )
            )
        )
    }

    /** 方位对应的说明文案。 */
    private fun sideLabel(side: Side): String = when (side) {
        Side.TOP -> "气泡在锚点上方。"
        Side.BOTTOM -> "气泡在锚点下方。"
        Side.START -> "气泡在锚点左侧（RTL 下自动翻转）。"
        Side.END -> "气泡在锚点右侧（RTL 下自动翻转）。"
        Side.AUTO -> "自动选择第一个放得下的方向。"
    }

    /** 示范自定义遮罩颜色；alpha = 0 时不绘制遮罩。 */
    private fun showMask(anchor: View, color: Int, label: String) {
        presentStep(
            GuideStep(
                view = anchor,
                content = doneContent(
                    "遮罩：$label",
                    "maskColor = #${Integer.toHexString(color)}"
                ),
                position = GuidePosition.Relative(side = Side.BOTTOM),
                highlight = GuideHighlight(
                    shape = HoleShape.RoundRect(12.dp),
                    padding = 8.dp,
                    maskColor = color
                )
            )
        )
    }

    /** 示范用半透明白色提亮挖洞区域；与遮罩颜色相互独立。 */
    private fun showHoleGlow(anchor: View) {
        presentStep(
            GuideStep(
                view = anchor,
                content = doneContent(
                    "挖洞高亮",
                    "holeOverlayColor = 0x33FFFFFF，给洞内区域叠加一层半透明白色。"
                ),
                position = GuidePosition.Relative(side = Side.BOTTOM),
                highlight = GuideHighlight(
                    shape = HoleShape.RoundRect(12.dp),
                    padding = 8.dp,
                    holeOverlayColor = 0x33FFFFFF
                )
            )
        )
    }

    /** 示范气泡背景色、圆角、描边和文字颜色。 */
    private fun showBubbleStyle(anchor: View) {
        presentStep(
            GuideStep(
                anchor = GuideAnchor.ViewAnchor(anchor),
                tooltip = GuideTooltip(
                    content = doneContent(
                        "自定义气泡",
                        "背景色、圆角、描边和文字颜色都可以单独设置。"
                    ).copy(
                        titleColor = 0xFF4E342E.toInt(),
                        messageColor = 0xFF6D4C41.toInt()
                    ),
                    position = GuidePosition.Relative(side = Side.BOTTOM),
                    backgroundColor = 0xFFFFF8E1.toInt(),
                    cornerRadius = 22.dp,
                    strokeColor = 0xFFFFB300.toInt(),
                    strokeWidth = 2.dp
                ),
                highlight = GuideHighlight(
                    shape = HoleShape.RoundRect(14.dp),
                    padding = 10.dp
                )
            )
        )
    }

    /**
     * 挖洞形状演示：锚点就是对应形状的预览按钮，
     * 洞使用同一个 [HoleShape]，所以两者形状一致。
     */
    private fun showHoleShape(anchor: View, shape: HoleShape, label: String, paddingDp: Int) {
        showTopStep(
            anchor = anchor,
            title = "HoleShape.$label",
            message = "按钮和挖洞使用同一个形状。",
            highlight = GuideHighlight(shape = shape, padding = paddingDp.dp)
        )
    }

    /** 构造自定义星形 Path。 */
    private fun starShape(): HoleShape = HoleShape.Custom { rect, path ->
        buildStarPath(rect, path)
    }

    /** 洞 = 锚点矩形 + padding，默认大小。 */
    private fun showHoleSizeDefault(anchor: View) {
        showTopStep(
            anchor = anchor,
            title = "默认大小",
            message = "洞 = 锚点矩形 + 四周 padding。",
            highlight = GuideHighlight(
                shape = HoleShape.RoundRect(12.dp),
                padding = 4.dp
            )
        )
    }

    /** 用较大的 padding 把洞向四周放大。 */
    private fun showHoleSizeLarge(anchor: View) {
        showTopStep(
            anchor = anchor,
            title = "放大 22dp",
            message = "padding 同时是气泡定位的起算距离。",
            highlight = GuideHighlight(
                shape = HoleShape.RoundRect(18.dp),
                padding = 22.dp
            )
        )
    }

    /** 用 holeRectProvider 返回固定的 180×88dp 洞。 */
    private fun showHoleSizeFixed(anchor: View) {
        showTopStep(
            anchor = anchor,
            title = "固定 180×88dp",
            message = "holeRectProvider 忽略锚点尺寸，只在中心对齐。",
            highlight = GuideHighlight(
                shape = HoleShape.RoundRect(20.dp),
                holeRectProvider = { anchorRect ->
                    Rect(
                        anchorRect.centerX() - dp(90),
                        anchorRect.centerY() - dp(44),
                        anchorRect.centerX() + dp(90),
                        anchorRect.centerY() + dp(44)
                    )
                }
            )
        )
    }

    /** 读取锚点 rect，并保证洞至少 160×72dp。 */
    private fun showHoleSizeAnchorBased(anchor: View) {
        showTopStep(
            anchor = anchor,
            title = "锚点 + 最小尺寸",
            message = "读取回调里的 anchor，保证洞至少 160×72dp。",
            highlight = GuideHighlight(
                shape = HoleShape.RoundRect(16.dp),
                holeRectProvider = { anchorRect ->
                    val width = maxOf(anchorRect.width(), dp(160))
                    val height = maxOf(anchorRect.height(), dp(72))
                    Rect(
                        anchorRect.centerX() - width / 2,
                        anchorRect.centerY() - height / 2,
                        anchorRect.centerX() + width / 2,
                        anchorRect.centerY() + height / 2
                    )
                }
            )
        )
    }

    /** 挖洞入场动画演示：None / Scale / Fade。 */
    private fun showHoleAnimation(anchor: View, animation: HoleAnimation, label: String) {
        showTopStep(
            anchor = anchor,
            title = "HoleAnimation.$label",
            message = "动画只在步骤首次定位时播放。",
            highlight = GuideHighlight(
                shape = HoleShape.RoundRect(14.dp),
                padding = 12.dp,
                holeAnimation = animation
            )
        )
    }

    /**
     * 锚点穿透：洞内的触摸会转发给 Activity。
     * 计数按钮同时是触发器和锚点，第一次点击打开引导，之后透传点击才会 +1。
     */
    private fun showPassThrough(anchor: View) {
        showTopStep(
            anchor = anchor,
            title = "锚点穿透",
            message = "洞内的触摸会转发给宿主 Activity。直接点计数按钮，引导不会关闭。",
            highlight = GuideHighlight(
                shape = HoleShape.RoundRect(12.dp),
                padding = 10.dp
            )
        ) { it.copy(passThroughTargetTouch = true) }
    }

    /** 点击洞直接完成：只触发 session.next()，不执行按钮自身逻辑。 */
    private fun showAdvanceOnTargetClick(anchor: View) {
        showTopStep(
            anchor = anchor,
            title = "点击洞 = 完成",
            message = "advanceOnTargetClick 只把点击洞映射成 next()，不会执行按钮自身逻辑。",
            highlight = GuideHighlight(
                shape = HoleShape.RoundRect(12.dp),
                padding = 10.dp
            )
        ) { it.copy(advanceOnTargetClick = true) }
    }

    /**
     * 局部遮罩：只压暗按钮周围，范围之外的触摸继续交给页面。
     * 弹引导后可以上下滚动列表，观察遮罩和气泡跟随锚点。
     */
    private fun showLocalMask(anchor: View) {
        showTopStep(
            anchor = anchor,
            title = "局部遮罩",
            message = "只有按钮周围被压暗，其他区域仍然可以滚动和点击。试试上下滑动列表。",
            highlight = GuideHighlight(
                shape = HoleShape.RoundRect(12.dp),
                padding = 8.dp,
                maskRectProvider = { _, anchorRect ->
                    val padX = dp(80)
                    val padY = dp(100)
                    Rect(
                        anchorRect.left - padX,
                        anchorRect.top - padY,
                        anchorRect.right + padX,
                        anchorRect.bottom + padY
                    )
                }
            )
        ) { it.copy(passThroughOutsideMask = true) }
    }

    /**
     * 自定义气泡内容：使用 ViewBinding 直接访问控件，不再 findViewById。
     * 分两步演示 A/B 两个锚点上的自定义布局。
     */
    private fun showCustomContent(anchorA: View, anchorB: View) {
        val custom = GuideContent.CustomBinding(
            create = { inflater, parent ->
                ViewGuideCustomDemoBinding.inflate(inflater, parent, false)
            },
            bind = { viewBinding, session ->
                val step = session.index + 1
                viewBinding.customGuideMessage.text =
                    "当前第 $step/${session.steps.size} 步。按钮直接调用 session.previous() / next()。"
                viewBinding.customGuidePrevious.setOnClickListener { session.previous() }
                viewBinding.customGuideNext.setOnClickListener { session.next() }
            }
        )

        present(
            newGuide()
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(anchorA),
                        tooltip = GuideTooltip(
                            content = custom,
                            position = GuidePosition.below()
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(anchorB),
                        tooltip = GuideTooltip(
                            content = custom,
                            position = GuidePosition.below()
                        )
                    )
                )
        )
    }

    /** 用 [GuidePosition.Custom] 把气泡固定在可用区域底部居中。 */
    private fun showCustomPosition(anchor: View) {
        presentStep(
            GuideStep(
                anchor = GuideAnchor.ViewAnchor(anchor),
                tooltip = GuideTooltip(
                    content = doneContent(
                        "自定义定位",
                        "GuidePosition.Custom 返回任意矩形；这里固定在可用区域底部居中，并关闭箭头。"
                    ),
                    position = GuidePosition.Custom { _, size, available ->
                        val left = available.centerX() - size.width / 2
                        val top = available.bottom - size.height - dp(24)
                        Rect(left, top, left + size.width, top + size.height)
                    },
                    arrow = GuideArrow(enabled = false),
                    maxWidth = GuideWidth.Fixed(300.dp)
                ),
                highlight = GuideHighlight(
                    shape = HoleShape.RoundRect(12.dp),
                    padding = 8.dp
                )
            )
        )
    }

    /** 气泡宽度占满可用屏幕宽度，两侧保留 [GuideTooltip.edgePadding]。 */
    private fun showFillScreenWidth(anchor: View) {
        presentStep(
            GuideStep(
                anchor = GuideAnchor.ViewAnchor(anchor),
                tooltip = GuideTooltip(
                    content = doneContent(
                        "GuideWidth.FillScreen",
                        "气泡宽度 = 可用屏幕宽度 - 两侧 edgePadding。"
                    ),
                    position = GuidePosition.Relative(side = Side.BOTTOM),
                    maxWidth = GuideWidth.FillScreen
                ),
                highlight = GuideHighlight(
                    shape = HoleShape.RoundRect(10.dp),
                    padding = 6.dp
                )
            )
        )
    }

    /** 气泡宽度占满遮罩宽度；遮罩由 maskRectProvider 缩小到按钮附近。 */
    private fun showFillMaskWidth(anchor: View) {
        presentStep(
            GuideStep(
                anchor = GuideAnchor.ViewAnchor(anchor),
                tooltip = GuideTooltip(
                    content = doneContent(
                        "GuideWidth.FillMask",
                        "气泡宽度 = 遮罩宽度 - 两侧 edgePadding；没有 maskRectProvider 时等同 FillScreen。"
                    ),
                    position = GuidePosition.Relative(side = Side.BOTTOM),
                    maxWidth = GuideWidth.FillMask
                ),
                highlight = GuideHighlight(
                    shape = HoleShape.RoundRect(10.dp),
                    padding = 6.dp,
                    maskRectProvider = { _, anchorRect ->
                        val padX = dp(60)
                        val padY = dp(140)
                        Rect(
                            anchorRect.left - padX,
                            anchorRect.top - padY,
                            anchorRect.right + padX,
                            anchorRect.bottom + padY
                        )
                    }
                )
            )
        )
    }

    /**
     * RectAnchor 演示：点击时把按钮位置换算成一个固定屏幕矩形，
     * 之后不再跟随按钮移动（followAnchor = false）。
     */
    private fun showRectAnchor(trigger: View) {
        val location = IntArray(2)
        trigger.getLocationOnScreen(location)
        val halfWidth = trigger.width / 2 + dp(16)
        val halfHeight = trigger.height / 2 + dp(16)
        val cx = location[0] + trigger.width / 2
        val cy = location[1] + trigger.height / 2
        val fixedScreenRect = Rect(
            cx - halfWidth,
            cy - halfHeight,
            cx + halfWidth,
            cy + halfHeight
        )

        presentStep(
            GuideStep(
                anchor = GuideAnchor.RectAnchor { fixedScreenRect },
                content = doneContent(
                    "RectAnchor",
                    "不依赖任何 View，按屏幕矩形定位与挖洞。"
                ),
                position = GuidePosition.Relative(side = Side.BOTTOM),
                highlight = GuideHighlight(
                    shape = HoleShape.RoundRect(14.dp),
                    padding = 0.dp
                )
            ).copy(followAnchor = false)
        )
    }

    /** showOnce 演示；状态可以由本卡片的“重置状态”或右上角菜单清理。 */
    private fun showOnceDemo(anchor: View) {
        val fragment = newGuide()
            .addStep(
                anchor,
                doneContent(
                    "showOnce",
                    "完成或跳过后写入 GuideStorage，下次点击会直接跳过。"
                ).copy(skipLabel = "跳过")
            )
            .storage(demoStorage)
            .showOnce(ONCE_KEY)
            .show(supportFragmentManager)

        log(
            if (fragment == null) {
                "showOnce: 已看过，本次跳过"
            } else {
                "showOnce: 展示中（完成或跳过才落盘）"
            }
        )
    }

    // endregion

    /** dp 转 px，用于示例里的手工间距。 */
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    /**
     * 基于 SharedPreferences 的演示存储。
     *
     * ## 说明
     * 支持单个 key 重置和全部清空，供 showOnce 示例和工具栏菜单使用。
     */
    private inner class DemoStorage : GuideStorage {
        private val prefs: SharedPreferences =
            getSharedPreferences("pm_guide_handbook", MODE_PRIVATE)

        /** 该 key 是否已经完成/跳过过。 */
        override fun wasShown(key: String): Boolean = prefs.getBoolean(key, false)

        /** 完成后写入标记。 */
        override fun markShown(key: String) {
            prefs.edit().putBoolean(key, true).apply()
        }

        /** 清除单个 key 的标记。 */
        override fun reset(key: String) {
            prefs.edit().remove(key).apply()
        }

        /** 清空所有引导状态，供工具栏菜单使用。 */
        fun clear() {
            prefs.edit().clear().apply()
        }
    }

    private companion object {
        /** 首次进入自动播放的欢迎引导 key。 */
        const val WELCOME_KEY = "handbook_welcome_v1"

        /** showOnce 示例使用的 key。 */
        const val ONCE_KEY = "handbook_once_v1"
    }
}
