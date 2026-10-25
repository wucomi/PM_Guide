# PM Guide

基于 `DialogFragment` 的 Android 新手引导 / 聚光灯组件。支持锚点跟随、自动选边、翻边、遮罩挖洞、箭头、挖洞动画、自定义挖洞大小与形状、气泡样式、穿透点击和一次性引导。

本工程分为两个模块：`guide` 是可直接复用的 Android Library，`app` 是 API 使用手册 Demo。不包含设备连接、私有协议或厂商 SDK 依赖。

## 本次更新

- **模块拆分**：引导组件整体移入 `guide`（Android Library，namespace `com.pm.guide`）；Demo 保留在 `app`，通过 `implementation project(':guide')` 依赖。
- **主题可覆盖**：`guide` 默认使用 `Theme.Material3.DayNight.NoActionBar` 作为 `Theme.Guide.Base`，宿主 App 可以重新定义同名 style 继承自己的主题。
- **结构精简**：`GuideTooltip` / `GuideArrow` 独立到 `GuideTooltip.kt`；`GuidePositioner` 只负责气泡定位，箭头落点移到 `GuideOverlayView`；移除 `GuideAnchor.IdAnchor`。
- 新增：`GuideHighlight.holeOverlayColor`，可在挖洞区域叠加半透明高亮色（提亮或强调目标）。
- **注释与精简**：统一补齐方法/字段注释，移除明显重复或 demo 相关的注释；`GuideOverlayView` 复用布局/绘制对象，默认气泡布局改用 ViewBinding。
- **修复**：点击“下一步”后气泡不再出现。原因是步骤切换时只监听了 Activity 窗口的 `OnPreDraw`，Dialog 显示期间 Activity 不再重绘，导致新锚点没有被解析、气泡一直停在 `alpha = 0`。现在同时监听引导自身窗口，并在 `bind()` 时立即解析一次锚点。
- **修复**：引导显示时状态栏没有被遮罩覆盖。Dialog 窗口现在使用 `FLAG_LAYOUT_IN_SCREEN` + `FLAG_LAYOUT_NO_LIMITS` + `setDecorFitsSystemWindows(false)` 铺满整屏，遮罩会覆盖状态栏和导航栏区域；气泡仍只在扣除系统栏后的可用区域内定位。
- 新增：`GuideTooltip.backgroundColor` / `cornerRadius` / `strokeColor` / `strokeWidth`，以及 `GuideContent.Default.titleColor` / `messageColor`。
- 新增：`HoleAnimation.Scale` / `HoleAnimation.Fade` 挖洞入场动画。
- 新增：`HoleShape.Oval` / `HoleShape.Custom`，以及 `GuideHighlight.holeRectProvider` 自定义洞的大小和位置（回调直接拿到锚点 rect）。
- 新增：`GuideListener.onStepLocated()` 回传 `anchorRect` / `holeRect` / `tooltipRect`。
- 新增：`GuideStep.passThroughTargetTouch`，洞内触摸穿透到宿主 Activity。
- 新增：`GuideHighlight.maskRectProvider` 自定义遮罩范围，以及 `GuideStep.passThroughOutsideMask` 让遮罩范围之外保持可交互。
- 新增：`GuideAnchor.RecyclerViewItemAnchor`，按 adapter position 引导 RecyclerView item，支持自动滚动和 pending 等待。
- 新增：`GuideContent.CustomBinding`，直接使用 ViewBinding / DataBinding，不再 `findViewById`。
- 新增：`GuidePlacement.customPosition` 完全自定义气泡位置。
- Demo 重做：移除固定靶区和底部栏，每个 API 卡片里的按钮本身就是演示锚点；顶部 hero 卡片播放完整三步引导，右上角菜单或 showOnce 卡片里的“重置状态”可以重置状态。页面结构、14 张卡片和按钮全部写在 `activity_main.xml`，`MainActivity` 只负责点击绑定。

## 运行

- 使用 Android Studio 打开工程，或执行 `./gradlew :app:assembleDebug`。
- 需要 JDK 17（AGP 8.6 / Gradle 8.7）。
- 启动后进入 API 手册页：顶部 hero 卡片播放完整引导，下面每张卡片对应一组能力，点击卡片里的按钮就会以该按钮为锚点运行引导。
- 首次进入会自动播放一次欢迎引导；`showOnce` 示例使用独立 key，右上角菜单可以重置状态。

## 模块划分

```text
guide/   Android Library（namespace com.pm.guide）
app/     API 使用手册 Demo（依赖 :guide）
```

宿主接入：

```gradle
dependencies {
    implementation project(':guide')
    // 或发布后使用 implementation 'com.pm.guide:guide:x.y.z'
}
```

`guide` 的公开 API 暴露了 `DialogFragment`、`RecyclerView`、`ViewBinding` 和 Material 主题资源，因此依赖声明里使用 `api`；`core-ktx` 和 `lifecycle` 只在内部实现中使用。

引导 Dialog 默认继承 `Theme.Guide.Base`。宿主可以让它继承自己的主题：

```xml
<!-- app/src/main/res/values/themes.xml -->
<style name="Theme.Guide.Base" parent="Theme.MyApp" />
```

## 目录结构

```text
guide/src/main/java/com/pm/guide/
├── GuideDialogFragment.kt   # DialogFragment 宿主、Builder、Session
├── GuideOverlayView.kt      # 遮罩、挖洞、动画、箭头、气泡、触摸与穿透
├── GuidePositioner.kt       # 纯几何定位器（可单测）
├── GuideAnchor.kt           # View / 矩形 / RecyclerView item 锚点
├── GuidePlacement.kt        # 方位、对齐、间距、溢出、自定义定位
├── GuideTooltip.kt          # 气泡内容、尺寸、背景样式和箭头
├── GuideHighlight.kt        # 挖洞形状、大小、遮罩颜色、入场动画
├── GuideContent.kt          # 默认内容 / 自定义内容 / ViewBinding 内容
├── GuideStep.kt             # 步骤、穿透、锚点失效策略
├── GuideSession.kt          # 会话控制与生命周期回调
├── GuideStorage.kt          # showOnce 持久化
└── GuideDp.kt               # Dp 工具

app/src/main/java/com/example/guide/
├── MainActivity.kt          # API 使用手册 Demo
├── HandBookSectionView.kt   # XML 驱动的能力卡片容器
├── ShapePreviewButton.kt    # 挖洞形状预览按钮（含星形路径工具）
└── DemoItemAdapter.kt       # RecyclerView item 引导示例适配器
```

默认气泡布局在 `guide/src/main/res/layout/view_guide_tooltip_default.xml`，Dialog 与文本样式在 `guide/src/main/res/values/guide_styles.xml`；自定义内容示例仍在 app 的 `view_guide_custom_demo.xml`。

## 最小可用

```kotlin
GuideDialogFragment.builder(this)
    .addStep(
        anchor = binding.guideTargetTitle,
        content = GuideContent.Default(
            title = "标题区域",
            message = "气泡会围绕这个目标显示。"
        ),
        placement = GuidePlacement(
            side = Side.BOTTOM,
            align = Alignment.CENTER
        )
    )
    .show(supportFragmentManager)
```

完整步骤写法：

```kotlin
GuideStep(
    anchor = GuideAnchor.ViewAnchor(binding.guideTargetAction),
    tooltip = GuideTooltip(
        content = GuideContent.Default("操作", "说明文案"),
        placement = GuidePlacement(side = Side.TOP),
        arrow = GuideArrow(enabled = true)
    ),
    highlight = GuideHighlight(shape = HoleShape.RoundRect(12.dp))
)
```

`Builder` 只要求 `Context`，不要求 `FragmentActivity`：

- Fragment：`builder(requireContext()).show(this)`（扩展函数，使用 `childFragmentManager`）
- Activity：`builder(this).show(this)`（扩展函数，使用 `supportFragmentManager`）
- Adapter：`builder(holder.itemView.context).show(fragmentManager)`，建议由宿主 Fragment 传入 `childFragmentManager`
- 不用 `showOnce` 时可以 `builder()` 不传 Context

## API 速查

### GuideAnchor：引导指向哪里

| 写法 | 说明 |
| --- | --- |
| `GuideAnchor.ViewAnchor(view)` | 直接持有 View，最可靠；不要在 RecyclerView item 中长期持有 |
| `GuideAnchor.RectAnchor { rect }` | 每次刷新调用 provider，返回任意矩形 |
| `GuideAnchor.RecyclerViewItemAnchor(rv, position, childId)` | 按 adapter position 定位 RecyclerView item，支持自动滚动 |

坐标约定：`resolveRect()` 返回锚点的**可见**矩形。锚点不可见、宽高为 0 或 detach 时返回 `null`，由 `GuideStep.anchorLostPolicy` 决定后续行为。

#### RecyclerView item 引导

```kotlin
GuideStep(
    anchor = GuideAnchor.RecyclerViewItemAnchor(
        recyclerView = binding.itemRecycler,
        position = 2,                 // adapter position
        childId = R.id.itemAction,    // item 内部控件；找不到时回退到 itemView
        scrollToItem = true           // 目标不在屏幕上时自动滚动
    )
)
```

- 锚点按 **adapter position** 解析，不持有 itemView，item 被回收/复用后仍然有效。
- `scrollToItem = true` 时，目标不可见会调用 `scrollToPosition` 并进入 pending 状态；
  `GuideAnchor.isPending()` 让组件等待布局完成，而不是误判为锚点丢失。
- 等待超过 1.5 秒仍拿不到 item，会交回 `anchorLostPolicy` 处理。
- `position` 在引导展示期间应保持稳定；`childId` 不存在时回退到整个 itemView。

### GuidePlacement：气泡放在哪里

```kotlin
GuidePlacement(
    side = Side.AUTO,               // AUTO / TOP / BOTTOM / START / END
    align = Alignment.CENTER,       // START / CENTER / END
    margin = 8.dp,                  // 从洞（高亮外框）起算
    offsetX = 0.dp,
    offsetY = 0.dp,
    autoOrder = listOf(Side.BOTTOM, Side.TOP, Side.END, Side.START),
    overflow = Overflow.FLIP_THEN_CLAMP,
    edgePadding = 16.dp
)
```

- `TOP` / `BOTTOM` / `START` / `END` 是相对锚点的四个方位，`START`/`END` 在 RTL 下自动翻转。
- `Alignment.START/CENTER/END` 控制沿共享边对齐。
- `Overflow.FLIP_THEN_CLAMP`（默认）：先翻到对侧，仍放不下再平移进可用区域；也支持 `FLIP`、`CLAMP`、`NONE`。

完全自定义定位：

```kotlin
GuidePlacement(
    customPosition = { anchorRect, tooltipSize, availableRect ->
        val left = availableRect.centerX() - tooltipSize.width / 2
        val top = availableRect.bottom - tooltipSize.height - marginPx
        Rect(left, top, left + tooltipSize.width, top + tooltipSize.height)
    }
)
```

设置 `customPosition` 后 `side` / `align` / `margin` / `offset` 不再参与计算；返回值仍会按 `overflow` 决定是否夹回可用区域。箭头方向由组件根据气泡与锚点的相对位置自动推断。

### GuideTooltip：气泡长什么样

```kotlin
GuideTooltip(
    content = GuideContent.Default("标题", "正文"),
    placement = GuidePlacement(side = Side.BOTTOM),
    arrow = GuideArrow(enabled = true, size = 8.dp, edgeInset = 12.dp),
    maxWidth = 320.dp,
    backgroundColor = 0xFFFFF8E1.toInt(),
    cornerRadius = 22.dp,
    strokeColor = 0xFFFFB300.toInt(),
    strokeWidth = 2.dp
)
```

- `backgroundColor = null` 时使用主题 `colorSurface`；箭头自动跟随气泡背景色。
- `cornerRadius` / `strokeColor` / `strokeWidth` 只作用于气泡外框，不影响自定义内容内部布局。

### GuideContent：气泡内容

```kotlin
// 内置样式：标题 + 正文 + 下一步/完成/跳过
GuideContent.Default(
    title = "标题",
    message = "正文",
    primaryLabel = "下一步",
    finishLabel = "知道了",
    skipLabel = "跳过",
    showIndicator = true,
    titleColor = null,
    messageColor = null
)

// 自定义布局：内容完全由 XML 决定，交互直接调用 GuideSession
GuideContent.Custom(R.layout.view_guide_custom_demo) { view, session ->
    view.findViewById<MaterialButton>(R.id.custom_guide_next)
        .setOnClickListener { session.next() }
    view.findViewById<MaterialButton>(R.id.custom_guide_previous)
        .setOnClickListener { session.previous() }
}

// ViewBinding / DataBinding：直接拿到生成的 Binding，不再 findViewById
GuideContent.CustomBinding(
    create = { inflater, parent ->
        ViewGuideCustomDemoBinding.inflate(inflater, parent, false)
    },
    bind = { binding, session ->
        binding.customGuideMessage.text = "第 ${session.index + 1} 步"
        binding.customGuideNext.setOnClickListener { session.next() }
    }
)
```

`CustomBinding` 的泛型是 `ViewBinding`，DataBinding 生成的 `ViewDataBinding` 也实现了它，所以两种绑定方式都能直接用。使用前需要在模块里开启 `viewBinding` 或 `dataBinding`。

自定义背景色较深时，记得同时设置 `titleColor` / `messageColor`（或自定义布局里自己的颜色），否则默认文字颜色来自主题，可能对比度不足。

### GuideHighlight：遮罩与挖洞

```kotlin
GuideHighlight(
    shape = HoleShape.RoundRect(12.dp),   // null = 不挖洞
    padding = 4.dp,
    maskColor = 0xB3000000.toInt(),       // 任意带 alpha 的背景色
    holeOverlayColor = Color.TRANSPARENT, // 挖洞区域高亮色，可提亮目标
    animate = true,                       // 气泡淡入
    holeAnimation = HoleAnimation.Scale(  // 洞的入场动画
        duration = 420L,
        startScale = 0.2f,
        fadeScrim = true
    ),
    holeRectProvider = null,              // 自定义洞的大小/位置
    maskRectProvider = null               // 自定义遮罩范围（默认整屏）
)
```

`holeOverlayColor` 会在洞形状上再叠加一层颜色，与 `maskColor` 独立：
- 半透明白色（如 `0x33FFFFFF`）可以提亮目标；
- 品牌色/警告色可以做视觉强调；
- `maskColor` 透明时依然生效。

**挖洞形状**：

| 形状 | 说明 |
| --- | --- |
| `HoleShape.Rect` | 贴合洞矩形的直角矩形 |
| `HoleShape.RoundRect(radius)` | 圆角矩形，半径不超过短边一半 |
| `HoleShape.Circle(radius?)` | 圆形；`radius = null` 时取内切圆 |
| `HoleShape.Oval` | 椭圆，填满整个洞矩形 |
| `HoleShape.Custom { rect, path -> ... }` | 追加任意 Path，适合星形/异形 |

**自定义洞大小**：

```kotlin
GuideHighlight(
    shape = HoleShape.RoundRect(16.dp),
    holeRectProvider = { anchor ->
        // anchor 是锚点的可见屏幕矩形；返回值同样使用屏幕坐标
        val width = maxOf(anchor.width(), minWidthPx)
        val height = maxOf(anchor.height(), minHeightPx)
        Rect(
            anchor.centerX() - width / 2,
            anchor.centerY() - height / 2,
            anchor.centerX() + width / 2,
            anchor.centerY() + height / 2
        )
    }
)
```

`holeRectProvider` 返回的矩形同时作为气泡定位基准（`GuidePlacement.margin` 从它起算）。设置后 `padding` 不再生效。

**自定义遮罩范围（局部引导）**：

```kotlin
GuideHighlight(
    maskRectProvider = { containerRect, anchorRect ->
        // 两个参数都是屏幕坐标；只压暗锚点周围的一块区域
        val padX = halfWidthPx
        val padY = halfHeightPx
        Rect(
            anchorRect.left - padX,
            anchorRect.top - padY,
            anchorRect.right + padX,
            anchorRect.bottom + padY
        )
    }
)
```

- `maskRectProvider = null`（默认）：遮罩铺满整个窗口。
- 返回值会被裁剪到窗口范围内；返回空矩形表示不画遮罩。
- 遮罩范围**只决定画哪里**。默认仍然整屏拦截触摸；要让范围外继续可交互，配合 `GuideStep.passThroughOutsideMask = true`。

**入场动画**：

| 动画 | 效果 |
| --- | --- |
| `HoleAnimation.None` | 直接显示（默认） |
| `HoleAnimation.Scale(duration, startScale, fadeScrim)` | 从中心缩放，可选遮罩淡入 |
| `HoleAnimation.Fade(duration)` | 洞大小不变，遮罩淡入 |

动画只在步骤切换后的第一次有效定位时播放；锚点移动（滚动、键盘弹出）时洞和气泡即时跟随，不会重播，避免拖影。

### GuideStep：单个步骤

```kotlin
GuideStep(
    anchor = GuideAnchor.ViewAnchor(target),
    tooltip = GuideTooltip(content = content),
    highlight = GuideHighlight(),
    followAnchor = null,              // null = 使用 Builder 的全局默认值
    advanceOnTargetClick = false,     // 点击洞 = 下一步，不执行锚点自身逻辑
    passThroughTargetTouch = false,   // 洞内触摸穿透给锚点
    anchorLostPolicy = AnchorLostPolicy.DISMISS,
    onShown = { session -> /* 埋点 */ },
    passThroughOutsideMask = false    // 遮罩范围之外的触摸穿透给页面
)
```

两种穿透可以独立组合：

| 配置 | 效果 |
| --- | --- |
| `passThroughTargetTouch = true` | 洞内的触摸转发给锚点，锚点按钮/开关可以真正点击；引导不会因这次点击关闭 |
| `passThroughOutsideMask = true` | 只有遮罩范围内拦截触摸；配合 `maskRectProvider` 做局部引导，遮罩外的列表仍然可以滚动、点击 |
| 两者都开启 | 洞内 + 遮罩外都透传，只有“遮罩内且洞外”的区域保持拦截 |

`advanceOnTargetClick` 则是另一种语义：点击洞会调用 `session.next()`，**不会**触发锚点按钮自身的点击事件。它与两种 `passThrough` 互斥使用。

穿透实现说明：Dialog 窗口本身会拦截触摸，组件把事件重新分发到 `activity.window.decorView`，因此只支持宿主是 Activity 的场景。锚点可以是按钮、开关等可交互控件。

### 锚点失效

锚点不可见、宽高为 0 或 detach 时，`GuideStep.anchorLostPolicy` 决定行为：

- `DISMISS`（默认）：立即结束引导，原因 `ANCHOR_LOST`
- `SKIP_NEXT`：跳过当前步骤
- `KEEP`：保留上一次位置；如果始终拿不到锚点，气泡会回退到屏幕中央

锚点重新 attach 后，监听会自动挂到新的 `ViewTreeObserver` 上。

### GuideSession：流程控制

```kotlin
interface GuideSession {
    val steps: List<GuideStep>
    val index: Int
    fun next()
    fun previous()
    fun goTo(index: Int)
    fun finish()   // FINISHED
    fun skip()     // SKIPPED
    fun dismiss()  // DISMISSED
}
```

在 `GuideContent.Custom` 的 `bind` 回调和 `GuideStep.onShown` 中都能拿到会话。会话在引导结束后失效。

### GuideListener：生命周期回调

```kotlin
GuideDialogFragment.builder(this)
    .addStep(...)
    .listener(object : GuideListener {
        override fun onStepShown(index: Int, step: GuideStep) = Unit

        override fun onStepLocated(
            index: Int,
            anchorRect: Rect,
            holeRect: Rect?,
            tooltipRect: Rect
        ) = Unit

        override fun onGuideEnded(reason: GuideEndReason) = Unit
    })
```

- `onStepShown`：步骤切换后触发，回退/跳转也会再次触发。
- `onStepLocated`：首次定位、锚点移动等导致矩形变化时触发；三个矩形都是屏幕坐标，可直接用于埋点或日志。`holeRect` 在 `shape = null` 时为 `null`。
- `onGuideEnded`：每次会话最多一次，`reason` 为 `FINISHED` / `SKIPPED` / `DISMISSED` / `ANCHOR_LOST` / `HOST_DESTROYED`。

### showOnce / 自定义存储

```kotlin
GuideDialogFragment.builder(this)
    .addStep(...)
    .storage(myStorage)       // 可选，默认 SharedPreferences
    .showOnce("welcome_v1")
    .show(supportFragmentManager)
```

- 展示前检查 `GuideStorage.wasShown(key)`；
- 只有 `FINISHED` 和 `SKIPPED` 才标记为已看；
- 返回键关闭、锚点丢失、宿主销毁都不会落盘；
- `GuideStorage` 提供 `wasShown` / `markShown` / `reset`，可实现服务端或其他介质。

## Demo 页对应关系

Demo 不再有独立的“靶子区”和底部栏：每张卡片里的按钮本身就是锚点，点击即演示。

| 卡片 | 涉及的 API |
| --- | --- |
| 顶部 hero | 多步骤引导、`GuideListener` |
| 1 · 步骤切换与会话回调 | `addStep` 连续步骤、`onStepShown` / `onStepLocated` / `onGuideEnded` |
| 2 · 全部方位 | `Side` / `Alignment` / `offsetX` / `offsetY` / `AUTO` |
| 3 · 遮罩背景色 | `GuideHighlight.maskColor` / `holeOverlayColor` |
| 4 · 气泡样式 | `GuideTooltip.backgroundColor` / `cornerRadius` / `strokeColor` / `strokeWidth`、`titleColor` / `messageColor` |
| 5 · 挖洞形状 | `HoleShape.Rect` / `RoundRect` / `Circle` / `Oval` / `Custom` |
| 6 · 挖洞大小 | `GuideHighlight.holeRectProvider`、`GuideListener.onStepLocated` |
| 7 · 挖洞动画 | `HoleAnimation.None` / `Scale` / `Fade` |
| 8 · 锚点穿透 / 点击洞 | `passThroughTargetTouch`、`advanceOnTargetClick` |
| 9 · 局部遮罩 + 遮罩外穿透 | `GuideHighlight.maskRectProvider`、`passThroughOutsideMask` |
| 10 · 自定义内容 | `GuideContent.Custom`、`GuideSession` |
| 11 · 自定义定位 | `GuidePlacement.customPosition`、`GuideArrow(enabled = false)` |
| 12 · RectAnchor | `GuideAnchor.RectAnchor` |
| 13 · showOnce | `Builder.showOnce` / `storage` / `GuideStorage.reset` |
| 14 · RecyclerView item | `GuideAnchor.RecyclerViewItemAnchor`、pending 等待与自动滚动 |

## 状态栏与系统栏

引导宿主窗口现在会铺满整屏并绘制遮罩到状态栏、导航栏区域。气泡和箭头的可用区域仍会扣除：

- 系统栏（状态栏、导航栏）
- 刘海 / 挖孔
- 输入法（IME）

因此遮罩能完整压暗状态栏，同时气泡不会跑到状态栏或键盘下面。

## 已知边界

- 步骤持有 `View` 引用，配置变更或进程恢复后不会自动重建，会以 `HOST_DESTROYED` 结束。
- `RectAnchor` 没有对应的 View 生命周期，动态矩形需要外部触发重绘，或设置 `followAnchor = false` 做一次性定位。
- 穿透点击依赖宿主 Activity 的 `decorView`，纯 Dialog / 其他 Window 宿主不支持。
- 引导默认是模态的；`passThroughTargetTouch` 放行洞内触摸，`passThroughOutsideMask` 放行遮罩外触摸，两者可以组合使用。
- `RecyclerViewItemAnchor` 以 adapter position 为准；如果引导期间数据源顺序变化，锚点会跟随新的 position。目标超过 1.5 秒仍不可见时按 `anchorLostPolicy` 处理。
- `GuideContent.CustomBinding` 需要宿主模块开启 `viewBinding` 或 `dataBinding`。
