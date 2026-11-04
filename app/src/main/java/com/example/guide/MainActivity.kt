package com.example.guide

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.guide.databinding.ActivityMainBinding
import com.example.guide.databinding.ViewGuideCustomDemoBinding
import com.example.guide.databinding.ViewGuideSessionDemoBinding
import com.pm.guide.Alignment
import com.pm.guide.AnchorLostPolicy
import com.pm.guide.BubbleAnimation
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
import com.pm.guide.Overflow
import com.pm.guide.Side
import com.pm.guide.dp
import com.pm.guide.show
import kotlin.math.roundToInt

/** Demo 入口：每个 API 分组一个多步引导，点“下一步”连续看完该组所有变体。 */
class MainActivity : AppCompatActivity() {

    /** 由 XML 生成的主页面绑定。 */
    private lateinit var binding: ActivityMainBinding

    /** 穿透示例的点击计数。 */
    private var tapCount = 0

    /** showOnce 示例使用的持久化实现。 */
    private val demoStorage by lazy { DemoStorage() }

    /** 自定义气泡内容：内容卡片和 6 个位置卡片共用。 */
    private val customGuideContent by lazy {
        GuideContent.CustomBinding(
            create = { inflater, parent ->
                ViewGuideCustomDemoBinding.inflate(inflater, parent, false)
            },
            bind = { customBinding, session ->
                customBinding.customGuideMessage.text =
                    "第 ${session.index + 1}/${session.steps.size} 步；上一步/下一步直接调用 GuideSession。"
                customBinding.customGuideNext.text =
                    if (session.index == session.steps.lastIndex) "完成" else "下一步"
                customBinding.customGuidePrevious.setOnClickListener { session.previous() }
                customBinding.customGuideNext.setOnClickListener { session.next() }
            }
        )
    }

    /** 自定义气泡箭头演示：四步都用 guide_bubble / guide_arrow 的说明。 */
    private val customArrowGuideContent by lazy {
        GuideContent.CustomBinding(
            create = { inflater, parent ->
                ViewGuideCustomDemoBinding.inflate(inflater, parent, false)
            },
            bind = { customBinding, session ->
                customBinding.customGuideTitle.text = "guide_bubble / guide_arrow"
                customBinding.customGuideMessage.text = when (session.index) {
                    0 -> "guide_arrow：气泡在锚点上方，箭头会旋转 180° 朝下；guide_bubble 是库定位用的气泡卡片。"
                    1 -> "guide_arrow：气泡在锚点下方，箭头 0° 朝上；guide_bubble 是库定位用的气泡卡片。"
                    2 -> "guide_arrow：气泡在锚点左侧，箭头旋转 90°；空间不足时库按 resolvedSide 翻边。"
                    else -> "guide_arrow：气泡在锚点右侧，箭头旋转 -90°；空间不足时库按 resolvedSide 翻边。"
                }
                customBinding.customGuideNext.text =
                    if (session.index == session.steps.lastIndex) "完成" else "下一步"
                customBinding.customGuidePrevious.setOnClickListener { session.previous() }
                customBinding.customGuideNext.setOnClickListener { session.next() }
            }
        )
    }

    /** GuideSession 演示用的自定义内容。 */
    private val sessionGuideContent by lazy {
        GuideContent.CustomBinding(
            create = { inflater, parent ->
                ViewGuideSessionDemoBinding.inflate(inflater, parent, false)
            },
            bind = { sessionBinding, session ->
                sessionBinding.sessionMessage.text =
                    "当前第 ${session.index + 1}/${session.steps.size} 步"
                sessionBinding.sessionNext.text =
                    if (session.index == session.steps.lastIndex) "完成" else "下一步"
                sessionBinding.sessionPrevious.setOnClickListener { session.previous() }
                sessionBinding.sessionNext.setOnClickListener { session.next() }
                sessionBinding.sessionGoTo.setOnClickListener { session.goTo(2) }
                sessionBinding.sessionFinish.setOnClickListener { session.finish() }
                sessionBinding.sessionSkip.setOnClickListener { session.skip() }
                sessionBinding.sessionDismiss.setOnClickListener { session.dismiss() }
            }
        )
    }

    /** 所有示例共用的生命周期日志。 */
    private val guideListener = object : GuideListener {

        /** 步骤展示后写日志。 */
        override fun onStepShown(index: Int, step: GuideStep) {
            log("onStepShown($index) · anchor=${step.anchor.javaClass.simpleName}")
        }

        /** 定位完成后写日志。 */
        override fun onStepLocated(
            index: Int,
            anchorRect: Rect,
            holeRect: Rect?,
            tooltipRect: Rect
        ) {
            log("onStepLocated($index) · anchor=$anchorRect · hole=$holeRect")
        }

        /** 引导结束时写日志。 */
        override fun onGuideEnded(reason: GuideEndReason) {
            log("onGuideEnded · $reason")
        }
    }

    /** 初始化页面并播放一次欢迎引导。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        bindSections()
        setupBottomBar()
        fitContentAboveBottomBar()

        binding.root.post { showWelcomeOnce() }
    }

    /** 底栏作为页面内导航：切到对应 API 分组。 */
    private fun setupBottomBar() {
        binding.bottomBar.setOnItemSelectedListener { item ->
            scrollToTab(item.itemId)
            true
        }
        // 已选中的 tab 再次点击时也回到对应分组。
        binding.bottomBar.setOnItemReselectedListener { item ->
            scrollToTab(item.itemId)
        }
    }

    /** 把滚动容器定位到 tab 对应的分组顶部。 */
    private fun scrollToTab(itemId: Int) {
        val scrollY = when (itemId) {
            R.id.tabHome -> 0
            R.id.tabBasics -> binding.sectionBasics.top
            R.id.tabBubble -> binding.sectionBubble.top
            R.id.tabAnchor -> binding.sectionAnchor.top
            else -> return
        }
        binding.demoScroll.smoothScrollTo(0, scrollY)
    }

    /**
     * 底栏覆盖在滚动内容上方，内容可以滑到栏下方；通过底部 padding 保证滚动到底时
     * 最后一段内容仍停在底栏上方，不会被遮挡。
     */
    private fun fitContentAboveBottomBar() {
        binding.bottomBarBlur.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            binding.demoScroll.updatePadding(bottom = binding.bottomBarBlur.height + dp(24))
        }
    }

    /** 配置顶栏菜单和“重置引导状态”。 */
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

    /** 初始化横向 RecyclerView。 */
    private fun setupRecyclerView() {
        binding.itemRecycler.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.itemRecycler.adapter = DemoItemAdapter { position ->
            log("点击了 Item ${position + 1}")
        }
        binding.waitRecycler.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.waitRecycler.adapter = DemoItemAdapter { position ->
            log("点击了等待列表 Item ${position + 1}")
        }
    }

    /** 按 API 分组绑定所有卡片。 */
    private fun bindSections() {
        bindBuilderDemos()
        bindStepDemos()
        bindListenerDemo()
        bindSessionDemo()
        bindShowOnceDemo()
        bindSideDemos()
        bindAlignDemos()
        bindCustomPositionDemos()
        bindOverflowDemo()
        bindDefaultBubbleDemo()
        bindCustomContentDemo()
        bindCustomContentTour()
        bindWidthDemos()
        bindBubbleStyleDemo()
        bindArrowStyleDemo()
        bindBubbleAnimationDemo()
        bindAnchorDemos()
        bindShapeDemos()
        bindSizeDemos()
        bindNoHoleDemo()
        bindHoleGlowDemo()
        bindAnimationDemos()
        bindHoleClickDemo()
        bindMaskColorDemo()
        bindLocalMaskDemo()
        bindRecyclerItemDemo()
        bindWaitVisibleDemo()
    }

    /** 绑定 Builder / 最小示例。 */
    private fun bindBuilderDemos() {
        binding.heroStart.setOnClickListener { showMinimalGuide() }
        binding.builderPlay.setOnClickListener { showBuilderGuide() }

        binding.builderBuild.setOnClickListener {
            val guide = GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.builderBuild,
                        content = GuideContent.Default(
                            title = "build() + show()",
                            message = "builder.build() 返回 GuideDialogFragment，再手动调用 show()。"
                        ),
                        position = GuidePosition.above()
                    )
                )
                .listener(guideListener)
                .build()
            guide.show(supportFragmentManager, GuideDialogFragment.TAG)
        }

        binding.builderOutside.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.builderOutside,
                        content = GuideContent.Default(
                            title = "dismissOnTouchOutside(true)",
                            message = "点击气泡和高亮区外会关闭引导。",
                            showIndicator = false,
                            skipLabel = null
                        ),
                        position = GuidePosition.above()
                    )
                )
                .dismissOnTouchOutside(true)
                .listener(guideListener)
                .show(this)
        }

        binding.builderBackOff.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.builderBackOff,
                        content = GuideContent.Default(
                            title = "dismissOnBackPress(false)",
                            message = "返回键不会关闭引导，只能点“完成”或“跳过”。",
                            showIndicator = false,
                            skipLabel = null
                        ),
                        position = GuidePosition.above()
                    )
                )
                .dismissOnBackPress(false)
                .listener(guideListener)
                .show(this)
        }
    }

    /** Builder 卡片内的三步引导，锚点都在本卡片内，避免滚出屏幕。 */
    private fun showBuilderGuide() {
        GuideDialogFragment.builder(this)
            .addStep(
                GuideStep(
                    view = binding.builderChipA,
                    content = GuideContent.Default(
                        title = "第 1 步",
                        message = "builder 添加步骤，默认气泡自带“下一步/完成”。"
                    ),
                    position = GuidePosition.below()
                )
            )
            .addStep(
                GuideStep(
                    view = binding.builderChipB,
                    content = GuideContent.Default(
                        title = "第 2 步",
                        message = "点击“下一步”会重新解析锚点并重新定位。"
                    ),
                    position = GuidePosition.below()
                )
            )
            .addStep(
                GuideStep(
                    view = binding.builderChipC,
                    content = GuideContent.Default(
                        title = "第 3 步",
                        message = "最后一步调用 session.finish()。",
                        finishLabel = "完成"
                    ),
                    position = GuidePosition.below()
                )
            )
            .listener(guideListener)
            .show(supportFragmentManager)
    }

    /** 最小示例：三步引导，默认“下一步/完成”按钮。 */
    private fun showMinimalGuide() {
        GuideDialogFragment.builder(this)
            .addStep(
                GuideStep(
                    view = binding.heroTitle,
                    content = GuideContent.Default(
                        title = "第 1 步 · 标题锚点",
                        message = "默认气泡自带“下一步/完成”，点“下一步”会重新解析锚点。"
                    ),
                    position = GuidePosition.below()
                )
            )
            .addStep(
                GuideStep(
                    view = binding.heroSubtitle,
                    content = GuideContent.Default(
                        title = "第 2 步 · 说明锚点",
                        message = "这就是最基本的步骤切换：不需要页面上的按钮，直接点气泡里的“下一步”。"
                    ),
                    position = GuidePosition.below()
                )
            )
            .addStep(
                GuideStep(
                    view = binding.heroStart,
                    content = GuideContent.Default(
                        title = "第 3 步 · 按钮锚点",
                        message = "最后一步的主按钮会调用 session.finish()。",
                        finishLabel = "完成"
                    ),
                    position = GuidePosition.above()
                )
            )
            .listener(guideListener)
            .show(supportFragmentManager)
    }

    /** 绑定 GuideStep 字段和锚点失效策略。 */
    private fun bindStepDemos() {
        binding.stepTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.stepChipA,
                        content = GuideContent.Default(
                            title = "GuideStep · 默认字段",
                            message = "anchor + tooltip + highlight 是最小组合；不传 highlight 时使用默认圆角高亮区。"
                        ),
                        position = GuidePosition.above()
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.stepChipB,
                        content = GuideContent.Default(
                            title = "followAnchor = false",
                            message = "只在步骤展示时定位一次，适合锚点位置固定的页面。"
                        ),
                        position = GuidePosition.above()
                    ).copy(followAnchor = false)
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.stepChipA),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "onShown 回调",
                                message = "定位完成后会执行 onShown，可以把日志或统计写在这里。"
                            ),
                            position = GuidePosition.above()
                        ),
                        onShown = { log("GuideStep.onShown") }
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.stepChipB,
                        content = GuideContent.Default(
                            title = "自定义 highlight",
                            message = "highlight 可以覆盖形状、padding、遮罩和动画。",
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(6.dp),
                            padding = 2.dp
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }

        binding.lostDismiss.setOnClickListener { showLostPolicy(AnchorLostPolicy.DISMISS) }
        binding.lostSkip.setOnClickListener { showLostPolicy(AnchorLostPolicy.SKIP_NEXT) }
        binding.lostKeep.setOnClickListener { showLostPolicy(AnchorLostPolicy.KEEP) }
    }

    /** 锚点失效策略演示：500ms 后隐藏第一个锚点。 */
    private fun showLostPolicy(policy: AnchorLostPolicy) {
        val lostListener = object : GuideListener {

            /** 转发步骤回调。 */
            override fun onStepShown(index: Int, step: GuideStep) {
                guideListener.onStepShown(index, step)
            }

            /** 转发定位回调。 */
            override fun onStepLocated(
                index: Int,
                anchorRect: Rect,
                holeRect: Rect?,
                tooltipRect: Rect
            ) {
                guideListener.onStepLocated(index, anchorRect, holeRect, tooltipRect)
            }

            /** 引导结束后恢复被隐藏的锚点。 */
            override fun onGuideEnded(reason: GuideEndReason) {
                binding.lostTarget.visibility = View.VISIBLE
                guideListener.onGuideEnded(reason)
            }
        }

        val builder = GuideDialogFragment.builder(this)
            .addStep(
                GuideStep(
                    anchor = GuideAnchor.ViewAnchor(binding.lostTarget),
                    tooltip = GuideTooltip(
                        content = GuideContent.Default(
                            title = "anchorLostPolicy.$policy",
                            message = when (policy) {
                                AnchorLostPolicy.DISMISS -> "锚点消失后立即结束引导。"
                                AnchorLostPolicy.SKIP_NEXT -> "锚点消失后自动跳到下一步。"
                                AnchorLostPolicy.KEEP -> "锚点消失后保持最后一次位置。"
                            }
                        ),
                        position = GuidePosition.above()
                    ),
                    anchorLostPolicy = policy
                )
            )

        if (policy == AnchorLostPolicy.SKIP_NEXT) {
            builder.addStep(
                GuideStep(
                    view = binding.lostNext,
                    content = GuideContent.Default(
                        title = "已自动跳到下一步",
                        message = "第一个锚点消失后，SKIP_NEXT 直接前进到这里。",
                        finishLabel = "完成"
                    ),
                    position = GuidePosition.above()
                )
            )
        }

        binding.lostTarget.visibility = View.VISIBLE
        binding.lostTarget.postDelayed({
            if (supportFragmentManager.findFragmentByTag(GuideDialogFragment.TAG) != null) {
                binding.lostTarget.visibility = View.GONE
            }
        }, 500L)

        builder.listener(lostListener).show(supportFragmentManager)
    }

    /** 气泡内容动画。 */
    private fun bindBubbleAnimationDemo() {
        binding.bubbleAnimTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.bubbleAnimFade),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "BubbleAnimation.Fade",
                                message = "气泡内容从透明淡入。"
                            ),
                            position = GuidePosition.above(),
                            animation = BubbleAnimation.Fade(duration = 400L)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.bubbleAnimScale),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "BubbleAnimation.Scale",
                                message = "气泡内容从 startScale 缩放到 1。"
                            ),
                            position = GuidePosition.above(),
                            animation = BubbleAnimation.Scale(
                                duration = 420L,
                                startScale = 0.85f
                            )
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.bubbleAnimSlide),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "BubbleAnimation.Slide",
                                message = "气泡内容从下方 offset 滑入。",
                                finishLabel = "完成"
                            ),
                            position = GuidePosition.above(),
                            animation = BubbleAnimation.Slide(
                                duration = 420L,
                                offset = 16.dp
                            )
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 GuideAnchor：View 与 Rect。 */
    private fun bindAnchorDemos() {
        binding.anchorTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.anchorChipView,
                        content = GuideContent.Default(
                            title = "GuideAnchor.ViewAnchor",
                            message = "直接持有 View，适合页面内生命周期稳定的控件。"
                        ),
                        position = GuidePosition.above()
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.RectAnchor {
                            val location = IntArray(2)
                            binding.anchorChipRect.getLocationOnScreen(location)
                            Rect(
                                location[0] - dp(8),
                                location[1] - dp(8),
                                location[0] + binding.anchorChipRect.width + dp(8),
                                location[1] + binding.anchorChipRect.height + dp(8)
                            )
                        },
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "GuideAnchor.RectAnchor",
                                message = "用屏幕矩形定位，不依赖 View；followAnchor = false 只定位一次。",
                                finishLabel = "完成"
                            ),
                            position = GuidePosition.above()
                        ),
                        followAnchor = false
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 GuidePosition.Relative 的全部方位。 */
    private fun bindSideDemos() {
        binding.sideTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.sideChipTop),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Side.TOP",
                                message = "气泡在锚点上方，margin 从高亮外框起算。"
                            ),
                            position = GuidePosition.Relative(side = Side.TOP),
                            maxWidth = GuideWidth.Fixed(220.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.sideChipBottom),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Side.BOTTOM",
                                message = "气泡在锚点下方，空间不足时会按 overflow 翻边。"
                            ),
                            position = GuidePosition.Relative(side = Side.BOTTOM),
                            maxWidth = GuideWidth.Fixed(220.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.sideChipStart),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Side.START",
                                message = "气泡在锚点左侧；左侧空间不足或 RTL 时会按 Overflow 翻到右侧。"
                            ),
                            position = GuidePosition.Relative(side = Side.START),
                            maxWidth = GuideWidth.Fixed(220.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.sideChipEnd),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Side.END",
                                message = "气泡在锚点右侧；右侧空间不足或 RTL 时会按 Overflow 翻到左侧。"
                            ),
                            position = GuidePosition.Relative(side = Side.END),
                            maxWidth = GuideWidth.Fixed(220.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.sideChipAuto),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Side.AUTO",
                                message = "按 autoOrder 依次尝试 TOP、BOTTOM、END、START。",
                                finishLabel = "完成"
                            ),
                            position = GuidePosition.Relative(
                                side = Side.AUTO,
                                autoOrder = listOf(Side.TOP, Side.BOTTOM, Side.END, Side.START)
                            ),
                            maxWidth = GuideWidth.Fixed(220.dp)
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 GuidePosition.Relative 的对齐、间距和偏移。 */
    private fun bindAlignDemos() {
        binding.alignTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.alignChipStart),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Alignment.START",
                                message = "气泡与锚点在共享边的起始位置对齐。",
                                showIndicator = false
                            ),
                            position = GuidePosition.Relative(
                                side = Side.TOP,
                                align = Alignment.START
                            ),
                            maxWidth = GuideWidth.Fixed(200.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.alignChipCenter),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Alignment.CENTER",
                                message = "气泡与锚点居中对齐，也是默认值。",
                                showIndicator = false
                            ),
                            position = GuidePosition.Relative(
                                side = Side.TOP,
                                align = Alignment.CENTER
                            ),
                            maxWidth = GuideWidth.Fixed(200.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.alignChipEnd),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Alignment.END",
                                message = "气泡与锚点在共享边的结束位置对齐。",
                                showIndicator = false
                            ),
                            position = GuidePosition.Relative(
                                side = Side.TOP,
                                align = Alignment.END
                            ),
                            maxWidth = GuideWidth.Fixed(200.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.offsetChip),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "offsetX / offsetY",
                                message = "在选边和对齐之后追加偏移：offsetX = 24dp，offsetY = 12dp。",
                                showIndicator = false
                            ),
                            position = GuidePosition.Relative(
                                side = Side.BOTTOM,
                                offsetX = 24.dp,
                                offsetY = 12.dp
                            ),
                            maxWidth = GuideWidth.Fixed(200.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.marginChip),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "margin = 24dp",
                                message = "margin 是气泡到高亮外框的距离，默认 8dp。",
                                showIndicator = false,
                                finishLabel = "完成"
                            ),
                            position = GuidePosition.Relative(
                                side = Side.TOP,
                                margin = 24.dp
                            ),
                            maxWidth = GuideWidth.Fixed(200.dp)
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 GuidePosition.Custom 的默认气泡位置。 */
    private fun bindCustomPositionDemos() {
        binding.customPosTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.posChipBottom),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "底部居中",
                                message = "GuidePosition.Custom 直接返回气泡矩形，这里固定在可用区域底部。"
                            ),
                            position = GuidePosition.Custom { _, size, available ->
                                val left = available.centerX() - size.width / 2
                                val top = available.bottom - size.height - dp(24)
                                Rect(left, top, left + size.width, top + size.height)
                            },
                            arrow = GuideArrow(enabled = false),
                            maxWidth = GuideWidth.Fixed(300.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.posChipTop),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "顶部居中",
                                message = "同一个 provider，换成 available.top 即可。"
                            ),
                            position = GuidePosition.Custom { _, size, available ->
                                val left = available.centerX() - size.width / 2
                                val top = available.top + dp(24)
                                Rect(left, top, left + size.width, top + size.height)
                            },
                            arrow = GuideArrow(enabled = false),
                            maxWidth = GuideWidth.Fixed(300.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.posChipCenter),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "屏幕中央",
                                message = "provider 的参数是 anchorRect、tooltipSize、availableRect。",
                                finishLabel = "完成"
                            ),
                            position = GuidePosition.Custom { _, size, available ->
                                val left = available.centerX() - size.width / 2
                                val top = available.centerY() - size.height / 2
                                Rect(left, top, left + size.width, top + size.height)
                            },
                            arrow = GuideArrow(enabled = false),
                            maxWidth = GuideWidth.Fixed(300.dp)
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 GuideTooltip.overflow 的四种策略。 */
    private fun bindOverflowDemo() {
        binding.overflowChip.setOnClickListener {
            binding.overflowFlipThenClamp.performClick()
        }

        binding.overflowFlipThenClamp.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.overflowChip),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Overflow.FLIP_THEN_CLAMP",
                                message = "BOTTOM 放不下，先翻到上方；上方也不够时夹回可用区域。",
                                showIndicator = false,
                                skipLabel = null
                            ),
                            position = GuidePosition.Relative(side = Side.BOTTOM),
                            overflow = Overflow.FLIP_THEN_CLAMP
                        ),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 4.dp
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }

        binding.overflowFlip.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.overflowChip),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Overflow.FLIP",
                                message = "只翻到上方，仍放不下时允许溢出可用区域。",
                                showIndicator = false,
                                skipLabel = null
                            ),
                            position = GuidePosition.Relative(side = Side.BOTTOM),
                            overflow = Overflow.FLIP
                        ),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 4.dp
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }

        binding.overflowClamp.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.overflowChip),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Overflow.CLAMP",
                                message = "不翻边，直接把气泡平移回可用区域。",
                                showIndicator = false,
                                skipLabel = null
                            ),
                            position = GuidePosition.Relative(side = Side.BOTTOM),
                            overflow = Overflow.CLAMP
                        ),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 4.dp
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }

        binding.overflowNone.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.overflowChip),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "Overflow.NONE",
                                message = "不做任何约束；放不下时允许溢出可用区域，一般只用于调试。",
                                showIndicator = false,
                                skipLabel = null
                            ),
                            position = GuidePosition.Relative(side = Side.BOTTOM),
                            overflow = Overflow.NONE
                        ),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 4.dp
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 GuideWidth 和 edgePadding。 */
    private fun bindWidthDemos() {
        binding.widthTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.widthChipWrap),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "GuideWidth.Wrap",
                                message = "默认模式：短内容保持内容宽度；这段文字比较长，气泡会在可用宽度内自动换行，不会像 Fixed(320dp) 那样被卡住。"
                            ),
                            position = GuidePosition.below(),
                            maxWidth = GuideWidth.Wrap
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.widthChipFixed),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "GuideWidth.Fixed(240.dp)",
                                message = "固定最大宽度；内容更窄时气泡仍会收缩到内容宽度。"
                            ),
                            position = GuidePosition.below(),
                            maxWidth = GuideWidth.Fixed(240.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.widthChipScreen),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "GuideWidth.FillScreen",
                                message = "气泡宽度 = 可用屏幕宽度 - 两侧 edgePadding（默认 16dp）。"
                            ),
                            position = GuidePosition.below(),
                            maxWidth = GuideWidth.FillScreen
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.widthChipMask),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "GuideWidth.FillMask",
                                message = "气泡宽度 = 遮罩宽度 - 两侧 edgePadding；遮罩由 maskRectProvider 缩小。",
                                showIndicator = false
                            ),
                            position = GuidePosition.below(),
                            maxWidth = GuideWidth.FillMask
                        ),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(10.dp),
                            padding = 6.dp,
                            maskRectProvider = { _, anchorRect ->
                                Rect(
                                    anchorRect.left - dp(160),
                                    anchorRect.top - dp(120),
                                    anchorRect.right + dp(160),
                                    anchorRect.bottom + dp(120)
                                )
                            }
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.widthChipPadding),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "edgePadding = 32dp",
                                message = "FillScreen 会按 32dp 的两侧留白重新计算宽度。",
                                finishLabel = "完成"
                            ),
                            position = GuidePosition.below(),
                            maxWidth = GuideWidth.FillScreen,
                            edgePadding = 32.dp
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 气泡样式：背景、圆角、描边和文字颜色。 */
    private fun bindBubbleStyleDemo() {
        binding.styleTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.styleChipLight),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "浅色气泡",
                                message = "backgroundColor / cornerRadius / strokeColor / strokeWidth 控制气泡外观。",
                                titleColor = 0xFF4E342E.toInt(),
                                messageColor = 0xFF6D4C41.toInt()
                            ),
                            position = GuidePosition.below(),
                            backgroundColor = 0xFFFFF8E1.toInt(),
                            cornerRadius = 22.dp,
                            strokeColor = 0xFFFFB300.toInt(),
                            strokeWidth = 2.dp
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.styleChipDark),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "深色气泡",
                                message = "深色背景时用 titleColor / messageColor 覆盖文字颜色。",
                                titleColor = 0xFFFFFFFF.toInt(),
                                messageColor = 0xFFCFD8DC.toInt(),
                                finishLabel = "完成"
                            ),
                            position = GuidePosition.below(),
                            backgroundColor = 0xFF263238.toInt(),
                            cornerRadius = 18.dp,
                            strokeColor = 0xFF546E7A.toInt(),
                            strokeWidth = 1.dp
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 箭头样式：size / edgeInset / enabled。 */
    private fun bindArrowStyleDemo() {
        binding.arrowTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.arrowStyleLarge),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "GuideArrow.size = 14dp",
                                message = "size 控制箭头伸出长度，edgeInset 控制距圆角的最小距离。"
                            ),
                            position = GuidePosition.above(),
                            arrow = GuideArrow(size = 14.dp, edgeInset = 18.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.arrowStyleNone),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "GuideArrow(enabled = false)",
                                message = "关闭箭头后，气泡与锚点之间只保留 margin。",
                                finishLabel = "完成"
                            ),
                            position = GuidePosition.above(),
                            arrow = GuideArrow(enabled = false)
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 默认气泡 GuideContent.Default。 */
    private fun bindDefaultBubbleDemo() {
        binding.defaultTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.defaultChipA,
                        content = GuideContent.Default(
                            title = "Default · 第 1 步",
                            message = "title / message / primaryLabel / finishLabel / skipLabel / showIndicator 都是 GuideContent.Default 的字段。",
                            skipLabel = "跳过"
                        ),
                        position = GuidePosition.below()
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.defaultChipB,
                        content = GuideContent.Default(
                            title = "Default · 第 2 步",
                            message = "titleColor / messageColor 可以覆盖默认文字颜色。",
                            showIndicator = false,
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.below()
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 自定义气泡内容：Custom 和 CustomBinding。 */
    private fun bindCustomContentDemo() {
        binding.customContentTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.customContentChipA),
                        tooltip = GuideTooltip(
                            content = GuideContent.Custom(R.layout.view_guide_custom_demo) { view, session ->
                                view.findViewById<View>(R.id.custom_guide_previous)
                                    .setOnClickListener { session.previous() }
                                view.findViewById<View>(R.id.custom_guide_next)
                                    .setOnClickListener { session.next() }
                            },
                            position = GuidePosition.above(),
                            maxWidth = GuideWidth.FillScreen
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.customContentChipB),
                        tooltip = GuideTooltip(
                            content = customGuideContent,
                            position = GuidePosition.above(),
                            maxWidth = GuideWidth.FillScreen
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 自定义气泡：guide_bubble / guide_arrow 说明 + 上下左右箭头。 */
    private fun bindCustomContentTour() {
        binding.customIdTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.arrowChipTop),
                        tooltip = GuideTooltip(
                            content = customArrowGuideContent,
                            position = GuidePosition.above()
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.arrowChipBottom),
                        tooltip = GuideTooltip(
                            content = customArrowGuideContent,
                            position = GuidePosition.below()
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.arrowChipStart),
                        tooltip = GuideTooltip(
                            content = customArrowGuideContent,
                            position = GuidePosition.startOf(),
                            maxWidth = GuideWidth.Fixed(200.dp)
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.arrowChipEnd),
                        tooltip = GuideTooltip(
                            content = customArrowGuideContent,
                            position = GuidePosition.endOf(),
                            maxWidth = GuideWidth.Fixed(200.dp)
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 生命周期回调与结束原因。 */
    private fun bindListenerDemo() {
        val listener = object : GuideListener {
            override fun onStepShown(index: Int, step: GuideStep) {
                guideListener.onStepShown(index, step)
            }

            override fun onStepLocated(
                index: Int,
                anchorRect: Rect,
                holeRect: Rect?,
                tooltipRect: Rect
            ) {
                guideListener.onStepLocated(index, anchorRect, holeRect, tooltipRect)
            }

            override fun onGuideEnded(reason: GuideEndReason) {
                binding.listenerChip.visibility = View.VISIBLE
                guideListener.onGuideEnded(reason)
            }
        }

        binding.listenerStart.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.listenerChip,
                        content = GuideContent.Default(
                            title = "onStepShown / onStepLocated",
                            message = "步骤切换和定位变化会分别触发这两个回调；日志在 hero 底部。",
                            skipLabel = "跳过"
                        ),
                        position = GuidePosition.below()
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.listenerChip,
                        content = GuideContent.Default(
                            title = "onGuideEnded",
                            message = "完成、跳过、返回关闭都会回调 onGuideEnded，reason 不同。",
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.below()
                    )
                )
                .listener(listener)
                .show(supportFragmentManager)
        }

        binding.listenerLost.setOnClickListener {
            binding.listenerChip.visibility = View.VISIBLE
            binding.listenerChip.postDelayed({
                if (supportFragmentManager.findFragmentByTag(GuideDialogFragment.TAG) != null) {
                    binding.listenerChip.visibility = View.GONE
                }
            }, 500L)
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.listenerChip),
                        tooltip = GuideTooltip(
                            content = GuideContent.Default(
                                title = "ANCHOR_LOST",
                                message = "锚点 500ms 后隐藏；anchorLostPolicy = DISMISS 会结束引导。",
                                showIndicator = false,
                                skipLabel = null
                            ),
                            position = GuidePosition.below()
                        ),
                        anchorLostPolicy = AnchorLostPolicy.DISMISS
                    )
                )
                .listener(listener)
                .show(supportFragmentManager)
        }
    }

    /** GuideSession 全部方法：next / previous / goTo / finish / skip / dismiss。 */
    private fun bindSessionDemo() {
        binding.sessionPlay.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.sessionChipA),
                        tooltip = GuideTooltip(
                            content = sessionGuideContent,
                            position = GuidePosition.above()
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.sessionChipB),
                        tooltip = GuideTooltip(
                            content = sessionGuideContent,
                            position = GuidePosition.above()
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.ViewAnchor(binding.sessionChipC),
                        tooltip = GuideTooltip(
                            content = sessionGuideContent,
                            position = GuidePosition.above()
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 HoleShape 的全部形状。 */
    private fun bindShapeDemos() {
        binding.shapeTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.shapeRect,
                        content = GuideContent.Default(
                            title = "HoleShape.Rect",
                            message = "高亮区是贴合锚点的直角矩形。"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.Rect,
                            padding = 10.dp
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.shapeRound,
                        content = GuideContent.Default(
                            title = "HoleShape.RoundRect(14.dp)",
                            message = "圆角半径超过短边一半时按短边内切处理。"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(14.dp),
                            padding = 10.dp
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.shapeCircle,
                        content = GuideContent.Default(
                            title = "HoleShape.Circle()",
                            message = "radius 为 null 时取高亮区矩形的内切圆；也可以传 Circle(16.dp)。"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.Circle(),
                            padding = 10.dp
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.shapeOval,
                        content = GuideContent.Default(
                            title = "HoleShape.Oval",
                            message = "椭圆始终填满整个高亮区矩形。"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.Oval,
                            padding = 10.dp
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.shapeStar,
                        content = GuideContent.Default(
                            title = "HoleShape.Custom",
                            message = "回调只需要把五角星路径追加到 path 上。",
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.Custom { rect, path ->
                                buildStarPath(rect, path)
                            },
                            padding = 10.dp
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 GuideHighlight 的 padding / holeRectProvider。 */
    private fun bindSizeDemos() {
        binding.sizeTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.sizeChipDefault,
                        content = GuideContent.Default(
                            title = "默认大小",
                            message = "高亮区 = 锚点矩形 + 四周 padding。"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 4.dp
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.sizeChipLarge,
                        content = GuideContent.Default(
                            title = "放大 22dp",
                            message = "padding 同时是气泡定位的起算距离。"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(18.dp),
                            padding = 22.dp
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.sizeChipFixed,
                        content = GuideContent.Default(
                            title = "固定 180×88dp",
                            message = "holeRectProvider 忽略锚点尺寸，只按中心对齐。"
                        ),
                        position = GuidePosition.above(),
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
                )
                .addStep(
                    GuideStep(
                        view = binding.sizeChipAnchor,
                        content = GuideContent.Default(
                            title = "锚点 + 最小尺寸",
                            message = "读取回调里的 anchor，保证高亮区至少 160×72dp。",
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.above(),
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
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 遮罩颜色。 */
    private fun bindMaskColorDemo() {
        binding.maskColorTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.maskChipDark,
                        content = GuideContent.Default(
                            title = "深色遮罩",
                            message = "maskColor = 0xE6000000。"
                        ),
                        position = GuidePosition.below(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 8.dp,
                            maskColor = 0xE6000000.toInt()
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.maskChipBlue,
                        content = GuideContent.Default(
                            title = "蓝色遮罩",
                            message = "maskColor = 0xCC1565C0。"
                        ),
                        position = GuidePosition.below(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 8.dp,
                            maskColor = 0xCC1565C0.toInt()
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.maskChipPurple,
                        content = GuideContent.Default(
                            title = "紫色遮罩",
                            message = "maskColor = 0xCC6A1B9A。"
                        ),
                        position = GuidePosition.below(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 8.dp,
                            maskColor = 0xCC6A1B9A.toInt()
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.maskChipNone,
                        content = GuideContent.Default(
                            title = "无遮罩",
                            message = "maskColor 的 alpha 为 0 时不绘制遮罩。",
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.below(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 8.dp,
                            maskColor = Color.TRANSPARENT
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 无高亮区，只压暗。 */
    private fun bindNoHoleDemo() {
        binding.noHoleStart.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.noHoleChip,
                        content = GuideContent.Default(
                            title = "shape = null",
                            message = "无高亮区，只绘制整层遮罩；maskColor 仍然生效。",
                            showIndicator = false,
                            skipLabel = null,
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.below(),
                        highlight = GuideHighlight(
                            shape = null,
                            maskColor = 0xB3000000.toInt()
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 高亮区提亮。 */
    private fun bindHoleGlowDemo() {
        binding.holeGlowStart.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.holeGlowChip,
                        content = GuideContent.Default(
                            title = "holeOverlayColor",
                            message = "在高亮区形状上单独叠加半透明白色，和 maskColor 独立。",
                            showIndicator = false,
                            skipLabel = null,
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.below(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 8.dp,
                            holeOverlayColor = 0x33FFFFFF
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 HoleAnimation 的三种动画。 */
    private fun bindAnimationDemos() {
        binding.animTour.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.animChipNone,
                        content = GuideContent.Default(
                            title = "HoleAnimation.None + animate = false",
                            message = "不做高亮区动画，同时关闭气泡淡入，直接显示最终状态。"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(14.dp),
                            padding = 12.dp,
                            holeAnimation = HoleAnimation.None,
                            animate = false
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.animChipScale,
                        content = GuideContent.Default(
                            title = "HoleAnimation.Scale",
                            message = "以高亮区中心从 0.2 缩放到 1，并让遮罩一起淡入。"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(14.dp),
                            padding = 12.dp,
                            holeAnimation = HoleAnimation.Scale(
                                duration = 420L,
                                startScale = 0.2f,
                                fadeScrim = true
                            )
                        )
                    )
                )
                .addStep(
                    GuideStep(
                        view = binding.animChipFade,
                        content = GuideContent.Default(
                            title = "HoleAnimation.Fade",
                            message = "只淡入遮罩，高亮区的大小保持不变。",
                            finishLabel = "完成"
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(14.dp),
                            padding = 12.dp,
                            holeAnimation = HoleAnimation.Fade(420L)
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 高亮区点击：穿透和点击高亮区前进。 */
    private fun bindHoleClickDemo() {
        binding.holeClickPass.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(GuideDialogFragment.TAG) == null) {
                GuideDialogFragment.builder(this)
                    .addStep(
                        GuideStep(
                            view = binding.holeClickPass,
                            content = GuideContent.Default(
                                title = "passThroughTargetTouch",
                                message = "高亮区内触摸会转发给 Activity；直接点计数按钮，引导不会关闭。",
                                showIndicator = false,
                                skipLabel = null
                            ),
                            position = GuidePosition.above(),
                            highlight = GuideHighlight(
                                shape = HoleShape.RoundRect(12.dp),
                                padding = 10.dp
                            )
                        ).copy(passThroughTargetTouch = true)
                    )
                    .listener(guideListener)
                    .show(supportFragmentManager)
            } else {
                tapCount++
                binding.holeClickPass.text = getString(R.string.demo_counter_value, tapCount)
                log("高亮区点击 +1 · 总计 $tapCount")
            }
        }

        binding.holeClickAdvance.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.holeClickAdvance,
                        content = GuideContent.Default(
                            title = "advanceOnTargetClick",
                            message = "点击高亮区只调用 session.next()，不执行按钮自身逻辑。",
                            showIndicator = false,
                            skipLabel = null
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 10.dp
                        )
                    ).copy(advanceOnTargetClick = true)
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 局部遮罩 + 遮罩外穿透。 */
    private fun bindLocalMaskDemo() {
        binding.localMaskStart.setOnClickListener {
            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        view = binding.localMaskChip,
                        content = GuideContent.Default(
                            title = "局部遮罩 + 遮罩外穿透",
                            message = "maskRectProvider 缩小遮罩范围；passThroughOutsideMask 放行范围外触摸，弹引导后仍可滚动列表。",
                            showIndicator = false,
                            skipLabel = null
                        ),
                        position = GuidePosition.below(),
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
                    ).copy(passThroughOutsideMask = true)
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** RecyclerView item 定位。 */
    private fun bindRecyclerItemDemo() {
        binding.recyclerTour.setOnClickListener {
            binding.itemStatus.text = "准备定位 Item 1 / 3 / 8…"
            binding.itemRecycler.scrollToPosition(0)
            binding.itemRecycler.post {
                GuideDialogFragment.builder(this)
                    .addStep(
                        GuideStep(
                            anchor = GuideAnchor.RecyclerViewItemAnchor(
                                recyclerView = binding.itemRecycler,
                                position = 0,
                                childId = R.id.itemAction,
                                scrollToItem = false
                            ),
                            tooltip = GuideTooltip(
                                content = GuideContent.Default(
                                    title = "Item 1 · 已可见",
                                    message = "scrollToItem = false，item 已经可见时直接解析锚点。"
                                ),
                                position = GuidePosition.above()
                            ),
                            highlight = GuideHighlight(
                                shape = HoleShape.RoundRect(12.dp),
                                padding = 8.dp
                            ),
                            onShown = {
                                binding.itemStatus.text = "Item 1 已可见：scrollToItem = false"
                            }
                        )
                    )
                    .addStep(
                        GuideStep(
                            anchor = GuideAnchor.RecyclerViewItemAnchor(
                                recyclerView = binding.itemRecycler,
                                position = 2,
                                childId = R.id.itemAction
                            ),
                            tooltip = GuideTooltip(
                                content = GuideContent.Default(
                                    title = "Item 3 · 自动滚动",
                                    message = "目标不在屏幕内时发起 scrollToPosition，并等待列表布局完成。"
                                ),
                                position = GuidePosition.above()
                            ),
                            highlight = GuideHighlight(
                                shape = HoleShape.RoundRect(12.dp),
                                padding = 8.dp
                            ),
                            onShown = {
                                binding.itemStatus.text = "Item 3：等待自动滚动并定位"
                            }
                        )
                    )
                    .addStep(
                        GuideStep(
                            anchor = GuideAnchor.RecyclerViewItemAnchor(
                                recyclerView = binding.itemRecycler,
                                position = 7,
                                childId = R.id.itemAction
                            ),
                            tooltip = GuideTooltip(
                                content = GuideContent.Default(
                                    title = "Item 8 · 等待滚动",
                                    message = "isPending 为 true 时引导等待，不会按锚点丢失关闭。",
                                    finishLabel = "完成"
                                ),
                                position = GuidePosition.above()
                            ),
                            highlight = GuideHighlight(
                                shape = HoleShape.RoundRect(12.dp),
                                padding = 8.dp
                            ),
                            onShown = {
                                binding.itemStatus.text = "Item 8：等待滚动到目标后定位"
                            }
                        )
                    )
                    .listener(guideListener)
                    .show(supportFragmentManager)
            }
        }
    }

    /** 等待 item 可见后再显示。 */
    private fun bindWaitVisibleDemo() {
        binding.waitStart.setOnClickListener {
            binding.waitStatus.text = "列表已隐藏：引导正在等待 item 可见…"
            binding.waitRecycler.visibility = View.INVISIBLE
            binding.waitRecycler.postDelayed({
                binding.waitRecycler.visibility = View.VISIBLE
                binding.waitStatus.text = "列表已显示：等待滚动到 Item 8 并定位。"
            }, 600L)

            GuideDialogFragment.builder(this)
                .addStep(
                    GuideStep(
                        anchor = GuideAnchor.RecyclerViewItemAnchor(
                            recyclerView = binding.waitRecycler,
                            position = 7,
                            childId = R.id.itemAction
                        ),
                        content = GuideContent.Default(
                            title = "等待可见",
                            message = "列表隐藏 600ms；item 可见并滚动到位后，气泡才出现。",
                            showIndicator = false,
                            skipLabel = null
                        ),
                        position = GuidePosition.above(),
                        highlight = GuideHighlight(
                            shape = HoleShape.RoundRect(12.dp),
                            padding = 8.dp
                        )
                    )
                )
                .listener(guideListener)
                .show(supportFragmentManager)
        }
    }

    /** 绑定 showOnce 演示。 */
    private fun bindShowOnceDemo() {
        binding.showOnce.setOnClickListener { showOnceDemo() }
        binding.showOnceReset.setOnClickListener {
            demoStorage.reset(ONCE_KEY)
            log("showOnce: 状态已重置，可以再次展示")
        }
    }

    /** 首次进入自动播放一次，只指向 hero 内部始终可见的元素。 */
    private fun showWelcomeOnce() {
        GuideDialogFragment.builder(this)
            .addStep(
                GuideStep(
                    view = binding.heroTitle,
                    content = GuideContent.Default(
                        title = "欢迎使用 PM Guide",
                        message = "这是首次进入自动播放的引导，只会出现一次。"
                    ),
                    position = GuidePosition.below()
                )
            )
            .addStep(
                GuideStep(
                    view = binding.heroSubtitle,
                    content = GuideContent.Default(
                        title = "向下就是 API 手册",
                        message = "每张卡片按 API 类分组，点一次“播放”，再用“下一步”连续看完。",
                        finishLabel = "完成"
                    ),
                    position = GuidePosition.below()
                )
            )
            .listener(guideListener)
            .storage(demoStorage)
            .showOnce(WELCOME_KEY)
            .show(supportFragmentManager)
    }

    /** showOnce 演示；完成或跳过后写入本地存储。 */
    private fun showOnceDemo() {
        val guide = GuideDialogFragment.builder(this)
            .addStep(
                GuideStep(
                    view = binding.showOnce,
                    content = GuideContent.Default(
                        title = "showOnce",
                        message = "完成或跳过后写入 GuideStorage，下次点击会直接跳过。",
                        showIndicator = false,
                        skipLabel = "跳过"
                    ),
                    position = GuidePosition.above()
                )
            )
            .listener(guideListener)
            .storage(demoStorage)
            .showOnce(ONCE_KEY)
            .show(supportFragmentManager)

        log(
            if (guide == null) {
                "showOnce: 已看过，本次跳过"
            } else {
                "showOnce: 展示中（完成或跳过才落盘）"
            }
        )
    }

    /** 把最新事件写到 hero 卡片底部。 */
    private fun log(message: String) {
        binding.guideLog.text = message
    }

    /** dp 转 px。 */
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    /** 基于 SharedPreferences 的演示存储。 */
    private inner class DemoStorage : GuideStorage {

        /** 演示专用的 SharedPreferences。 */
        private val prefs: SharedPreferences =
            getSharedPreferences("pm_guide_handbook", MODE_PRIVATE)

        /** 判断某个 key 是否已经完成或跳过。 */
        override fun wasShown(key: String): Boolean = prefs.getBoolean(key, false)

        /** 完成或跳过后写入标记。 */
        override fun markShown(key: String) {
            prefs.edit().putBoolean(key, true).apply()
        }

        /** 清除单个 key 的标记。 */
        override fun reset(key: String) {
            prefs.edit().remove(key).apply()
        }

        /** 清空所有引导状态。 */
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
