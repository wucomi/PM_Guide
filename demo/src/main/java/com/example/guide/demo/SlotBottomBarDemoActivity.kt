package com.example.guide.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.guide.demo.databinding.ActivitySlotBottomBarDemoBinding
import com.example.guide.demo.databinding.ItemNestedDemoBinding

/**
 * 参考首页 slot 结构做的 Demo：
 *
 * - slot1-4 是普通的 wrap_content 内容；
 * - slot5 是设备列表，不再使用 0dp + weight，避免被 NestedScrollView 锁死高度；
 * - RecyclerView 用 wrap_content + 关闭嵌套滚动，整个页面由外层 NestedScrollView 滚动；
 * - 底栏高度在运行时测量，作为 NestedScrollView 的 paddingBottom；
 * - 列表项少时给 slot5 设置 minimumHeight，让它仍然占满剩余视口。
 *
 * 三个场景：
 * 1. 全部显示：普通内容和设备列表同时存在；
 * 2. 隐藏 Slot5：验证 slot4 滚到底不被底栏遮挡；
 * 3. 隐藏 Slot1-3：验证设备列表能滑到底栏后面，且最后一项不被遮挡。
 */
class SlotBottomBarDemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySlotBottomBarDemoBinding
    private val deviceAdapter = DeviceListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySlotBottomBarDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        // 让滚动容器先拿到焦点，避免 slot5 里的计数按钮在首次布局时触发自动滚动。
        binding.nestedScroll.requestFocus()
        binding.bottomBar.applyTranslucentBarBackground()
        setupDeviceList()
        setupScenarios()
        fitContentAboveBottomBar()
        applyScenario(Scenario.ALL)
    }

    private fun setupDeviceList() {
        binding.deviceList.apply {
            layoutManager = LinearLayoutManager(this@SlotBottomBarDemoActivity)
            adapter = deviceAdapter
            itemAnimator = null
        }

        binding.deviceCountGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val count = when (checkedId) {
                R.id.deviceCountNone -> 0
                R.id.deviceCountThree -> 3
                else -> DEVICE_COUNT_LONG
            }
            renderDevices(count)
        }

        renderDevices(DEVICE_COUNT_LONG)
    }

    private fun setupScenarios() {
        binding.scenarioGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val scenario = when (checkedId) {
                R.id.scenarioHideSlot5 -> Scenario.HIDE_SLOT5
                R.id.scenarioHideTop -> Scenario.HIDE_TOP
                else -> Scenario.ALL
            }
            applyScenario(scenario)
        }
    }

    /** 模拟首页按业务状态动态显隐 slot。 */
    private fun applyScenario(scenario: Scenario) {
        val hideTopSlots = scenario == Scenario.HIDE_TOP
        binding.slot1.isVisible = !hideTopSlots
        binding.slot2.isVisible = !hideTopSlots
        binding.slot3.isVisible = !hideTopSlots
        binding.slot5.isVisible = scenario != Scenario.HIDE_SLOT5

        // 等显隐后的布局完成，再回到顶部并重算 slot5 的填充高度。
        binding.nestedScroll.post {
            binding.nestedScroll.scrollTo(0, 0)
            updateSlot5FillHeight()
        }
    }

    private fun renderDevices(count: Int) {
        deviceAdapter.setItemCount(count)
        binding.deviceCount.text = getString(R.string.slot_demo_count_format, count)
        binding.deviceEmpty.isVisible = count == 0

        // 数量变少后内容可能缩短，clamp 当前滚动位置，再重算填充高度。
        binding.nestedScroll.post {
            binding.nestedScroll.scrollTo(
                binding.nestedScroll.scrollX,
                binding.nestedScroll.scrollY
            )
            updateSlot5FillHeight()
        }
    }

    /**
     * 底栏覆盖在滚动内容上，用实际高度设置 paddingBottom：
     * 内容可以画到底栏背后，滚到最底时最后一块内容停在底栏上方。
     */
    private fun fitContentAboveBottomBar() {
        binding.bottomBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val height = bottom - top
            if (height <= 0 || height == oldBottom - oldTop) return@addOnLayoutChangeListener

            binding.nestedScroll.updatePadding(bottom = height + dp(16))
            binding.nestedScroll.post { updateSlot5FillHeight() }
        }
    }

    /**
     * slot5 是页面最后一块内容：
     * - 设备少时用 minimumHeight 撑满剩余视口，避免列表下面出现大片空白；
     * - 设备多时自然高度已经超过它，RecyclerView 继续撑高内容，外层统一滚动。
     *
     * 注意不能用 0dp + weight 来实现“占满剩余”，否则内容会被锁在视口高度，
     * 列表再长也无法滚到底栏后面。
     */
    private fun updateSlot5FillHeight() {
        if (!binding.slot5.isVisible || binding.nestedScroll.height == 0) return

        val scrollLocation = IntArray(2)
        val slotLocation = IntArray(2)
        binding.nestedScroll.getLocationInWindow(scrollLocation)
        binding.slot5.getLocationInWindow(slotLocation)

        // getLocationInWindow 含滚动偏移，这里还原成静止时的位置。
        val topInViewport = slotLocation[1] - scrollLocation[1] + binding.nestedScroll.scrollY
        val available =
            binding.nestedScroll.height - binding.nestedScroll.paddingBottom - topInViewport

        val minHeight = available.coerceAtLeast(0)
        if (binding.slot5.minimumHeight != minHeight) {
            binding.slot5.minimumHeight = minHeight
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class Scenario {
        ALL,
        HIDE_SLOT5,
        HIDE_TOP
    }

    private class DeviceListAdapter : RecyclerView.Adapter<DeviceListAdapter.Holder>() {

        private var itemCount = 0

        fun setItemCount(count: Int) {
            if (itemCount == count) return
            itemCount = count
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = itemCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val itemBinding = ItemNestedDemoBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(itemBinding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(position, itemCount)
        }

        class Holder(private val binding: ItemNestedDemoBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(position: Int, count: Int) {
                val context = binding.root.context
                binding.itemBadge.text = (position + 1).toString().padStart(2, '0')
                binding.itemTitle.text =
                    context.getString(R.string.slot_demo_device_title, position + 1)
                binding.itemSubtitle.text = context.getString(R.string.slot_demo_device_subtitle)
                binding.itemDivider.isVisible = position < count - 1
            }
        }
    }

    private companion object {
        const val DEVICE_COUNT_LONG = 20
    }
}
