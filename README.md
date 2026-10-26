# PM Guide

基于 `DialogFragment` 的 Android 新手引导 / 聚光灯组件。支持锚点跟随、自动选边、翻边、遮罩挖洞、箭头、挖洞动画、自定义挖洞大小与形状、气泡样式、穿透点击和一次性引导。

接入：

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
├── GuidePosition.kt         # 相对/自定义位置、方位、对齐、溢出
├── GuideTooltip.kt          # 气泡内容、GuideWidth、背景样式和箭头
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
        position = GuidePosition.below()
    )
    .show(supportFragmentManager)
```

最常见的一步引导可以直接用 `GuideStep` 的快捷构造：

```kotlin
GuideStep(
    view = binding.guideTargetAction,
    content = GuideContent.Default("操作", "说明文案"),
    position = GuidePosition.above(),
    highlight = GuideHighlight(shape = HoleShape.RoundRect(12.dp))
)
```

需要箭头、宽度或气泡样式时，再换成主构造：

```kotlin
GuideStep(
    anchor = GuideAnchor.ViewAnchor(binding.guideTargetAction),
    tooltip = GuideTooltip(
        content = GuideContent.Default("操作", "说明文案"),
        position = GuidePosition.above(),
        arrow = GuideArrow(enabled = true),
        maxWidth = GuideWidth.Fixed(280.dp)
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

### GuideTooltip.position：气泡放在哪里

常用方位可以直接用工厂方法：

```kotlin
position = GuidePosition.below()                         // 下方，间距 8dp
position = GuidePosition.above(margin = 12.dp)           // 上方
position = GuidePosition.startOf(align = Alignment.START)
position = GuidePosition.endOf()
position = GuidePosition.of(Side.AUTO)                   // 自动选边
```

需要完整控制时使用 `Relative`：

```kotlin
GuideTooltip(
    content = content,
    position = GuidePosition.Relative(
        side = Side.AUTO,               // AUTO / TOP / BOTTOM / START / END
        align = Alignment.CENTER,       // START / CENTER / END
        margin = 8.dp,                  // 从洞（高亮外框）起算
        offsetX = 0.dp,
        offsetY = 0.dp,
        autoOrder = listOf(Side.BOTTOM, Side.TOP, Side.END, Side.START)
    ),
    overflow = Overflow.FLIP_THEN_CLAMP,
    edgePadding = 16.dp
)
```

- `GuidePosition.Relative`：`TOP` / `BOTTOM` / `START` / `END` 是相对锚点的四个方位，`START`/`END` 在 RTL 下自动翻转。
- `Alignment.START/CENTER/END` 控制沿共享边对齐。
- `Overflow.FLIP_THEN_CLAMP`（默认）：先翻到对侧，仍放不下再平移进可用区域；也支持 `FLIP`、`CLAMP`、`NONE`。

完全自定义定位：

```kotlin
GuideTooltip(
    content = content,
    position = GuidePosition.custom { anchorRect, tooltipSize, availableRect ->
        val left = availableRect.centerX() - tooltipSize.width / 2
        val top = availableRect.bottom - tooltipSize.height - marginPx
        Rect(left, top, left + tooltipSize.width, top + tooltipSize.height)
    }
)
```

`GuidePosition.custom` 绕过选边、对齐和偏移，返回值仍会按 `overflow` 决定是否夹回可用区域。箭头方向由组件根据气泡与锚点的相对位置自动推断。

### GuideTooltip：气泡长什么样

```kotlin
GuideTooltip(
    content = GuideContent.Default("标题", "正文"),
    position = GuidePosition.Relative(side = Side.BOTTOM),
    arrow = GuideArrow(enabled = true, size = 8.dp, edgeInset = 12.dp),
    maxWidth = GuideWidth.Fixed(320.dp),
    backgroundColor = 0xFFFFF8E1.toInt(),
    cornerRadius = 22.dp,
    strokeColor = 0xFFFFB300.toInt(),
    strokeWidth = 2.dp
)
```

- `backgroundColor = null` 时，默认内容使用主题 `colorSurface`；自定义内容不绘制库背景，由自定义布局自己实现卡片背景。
- `cornerRadius` / `strokeColor` / `strokeWidth` 只影响库绘制的气泡外框；自定义内容默认透明时不会绘制外框，这些参数也就不生效。

#### 气泡宽度：GuideWidth

| 写法 | 效果 |
| --- | --- |
| `GuideWidth.Fixed(320.dp)` | 最大宽度；内容更窄时收缩到内容宽度 |
| `GuideWidth.FillScreen` | 占满可用屏幕宽度，两侧预留 `edgePadding` |
| `GuideWidth.FillMask` | 占满遮罩宽度，两侧预留 `edgePadding`；未设置 `maskRectProvider` 时等同 `FillScreen` |

Fill 模式按 `EXACTLY` 测量气泡宽度，适合整行卡片式引导。`edgePadding` 是 `GuideTooltip` 的参数，宽度和定位共用同一个留白。

#### 自定义气泡 / 箭头

自定义布局里用两个库 ID：

| ID | 说明 |
| --- | --- |
| `@id/guide_bubble` | 气泡卡片；库用它的尺寸和位置应用 `GuidePositioner` / `overflow` |
| `@id/guide_arrow` | 自定义箭头；库按 `resolvedSide` 定位/旋转它，并停止绘制默认 Canvas 箭头 |

箭头和气泡按普通布局写，气泡放在箭头下面：

```xml
<ImageView
    android:id="@id/guide_arrow"
    android:layout_width="30dp"
    android:layout_height="20dp"
    android:src="@drawable/my_arrow"
    app:layout_constraintBottom_toTopOf="@id/guide_bubble"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent" />

<LinearLayout
    android:id="@id/guide_bubble"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@drawable/my_bubble_bg"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toBottomOf="@id/guide_arrow">
    <!-- 标题、正文、按钮 -->
</LinearLayout>
```

```kotlin
GuideTooltip(
    content = GuideContent.Custom(R.layout.my_guide) { ... },
    position = GuidePosition.below()
)
```

- `GuidePositioner` 以 `guide_bubble` 的尺寸为准，`FLIP_THEN_CLAMP`、`edgePadding` 重新生效；
- 库会平移整个自定义布局，让 `guide_bubble` 落在计算出的位置；
- `guide_arrow` 的最终位置由库根据 `resolvedSide` 和挖孔中心重新计算：
  - 气泡在挖孔下方时箭头在气泡上边缘；
  - 气泡在上方时箭头在气泡下边缘并旋转 180°；
  - 气泡在左右两侧时箭头在对应侧边并旋转 ±90°；
  - 沿边缘的位置会滑动到挖孔中心附近，不会固定死在气泡中间；
- 自定义箭头存在时，气泡与挖孔的间距会自动不小于箭头长度，避免箭头伸进挖孔；
- `GuideArrow.edgeInset` 控制箭头中心距气泡圆角的最小距离；
- 箭头资源默认按“朝上”绘制；XML 里的位置只作为布局占位，最终视觉位置由库覆盖；
- 自定义内容默认不绘制库气泡背景，卡片、圆角、描边和箭头都由布局自己实现；
- 自定义布局根节点建议 `android:clipChildren="false"`，箭头伸出根布局时不会被裁剪。

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

`holeRectProvider` 返回的矩形同时作为气泡定位基准（`GuidePosition.Relative.margin` 从它起算）。设置后 `padding` 不再生效。

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

简单步骤可以直接用快捷构造：

```kotlin
GuideStep(
    view = target,
    content = GuideContent.Default("标题", "正文"),
    position = GuidePosition.below()
)
```

完整配置：

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
| 10 · 自定义内容 | `GuideContent.Custom`、`GuideSession`、`@id/guide_bubble`、`@id/guide_arrow` |
| 11 · 自定义定位 | `GuidePosition.Custom`、`GuideWidth.FillScreen`、`GuideWidth.FillMask`、`GuideArrow(enabled = false)` |
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
