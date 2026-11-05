package com.example.guide.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.guide.demo.databinding.ActivityNestedScrollDemoBinding
import com.example.guide.demo.databinding.ItemNestedDemoBinding
import com.example.guide.demo.databinding.ViewNestedDemoChildABinding
import com.example.guide.demo.databinding.ViewNestedDemoChildBBinding

/**
 * Demo：NestedScrollView 里放两个动态子布局，底栏覆盖在滚动内容上。
 *
 * 关键点：
 * 1. 两个子布局同时 inflate，但同一时间只有一个 VISIBLE，另一个 GONE；
 * 2. NestedScrollView 设置 clipToPadding=false + paddingBottom = 底栏高度 + 16dp，
 *    内容可以滑到底栏背后，滚到最底时最后一段内容停在底栏上方；
 * 3. 子布局 B 里的列表容器在列表项少时用 minimumHeight 撑满剩余视口，
 *    列表项多时随 RecyclerView 自然增高，整体交给外层统一滚动。
 *
 * 注意：RecyclerView 放在 NestedScrollView 里必须用 wrap_content 并关闭嵌套滚动，
 * 否则会出现两个滚动容器抢手势、列表被裁切的问题。数据量很大时更推荐
 * ConcatAdapter / 多类型 RecyclerView，而不是把长列表整个塞进 ScrollView。
 */
class NestedScrollDemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNestedScrollDemoBinding
    private lateinit var childABinding: ViewNestedDemoChildABinding
    private lateinit var childBBinding: ViewNestedDemoChildBBinding

    private val listAdapter = DemoListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNestedScrollDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupDynamicChildren()
        setupBottomBar()
        binding.bottomBar.applyTranslucentBarBackground()
        fitContentAboveBottomBar()
        showChild(CHILD_A)
    }

    /** 动态加载两个子布局；列表和切换逻辑都在这里初始化。 */
    private fun setupDynamicChildren() {
        childABinding = ViewNestedDemoChildABinding.inflate(layoutInflater, binding.childHost, false)
        childBBinding = ViewNestedDemoChildBBinding.inflate(layoutInflater, binding.childHost, false)
        binding.childHost.addView(childABinding.root)
        binding.childHost.addView(childBBinding.root)

        childBBinding.demoList.apply {
            layoutManager = LinearLayoutManager(this@NestedScrollDemoActivity)
            adapter = listAdapter
            // 数量变化时整块重建即可，避免局部动画和 NestedScrollView 的高度计算打架。
            itemAnimator = null
        }

        childBBinding.countGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val count = when (checkedId) {
                R.id.countNone -> 0
                R.id.countThree -> 3
                else -> LIST_COUNT_LONG
            }
            renderList(count)
        }

        renderList(LIST_COUNT_LONG)
    }

    /** 底栏两个 tab 切换子布局，同一时间只显示一个。 */
    private fun setupBottomBar() {
        binding.tabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showChild(if (checkedId == R.id.tabLayoutA) CHILD_A else CHILD_B)
        }
    }

    private fun showChild(child: Int) {
        childABinding.root.isVisible = child == CHILD_A
        childBBinding.root.isVisible = child == CHILD_B

        // 切换后回到顶部，并等布局完成再重算列表容器的剩余高度。
        binding.nestedScroll.post {
            binding.nestedScroll.scrollTo(0, 0)
            updateListFillHeight()
        }
    }

    private fun renderList(count: Int) {
        listAdapter.setItemCount(count)
        childBBinding.listCount.text = getString(R.string.nested_demo_list_count, count)
        childBBinding.listEmpty.isVisible = count == 0

        // 列表项减少后内容可能变短，clamp 一下当前滚动位置，再重算填充高度。
        binding.nestedScroll.post {
            binding.nestedScroll.scrollTo(
                binding.nestedScroll.scrollX,
                binding.nestedScroll.scrollY
            )
            updateListFillHeight()
        }
    }

    /**
     * 底栏高度只在运行时才知道（受字号、导航栏、文案影响），
     * 用实际高度设置 NestedScrollView 的底部 padding。
     *
     * clipToPadding=false 时 padding 不会裁剪内容，但会算进滚动范围：
     * 滚动过程中内容能画到底栏后面，滚到底时又不会被底栏挡住。
     */
    private fun fitContentAboveBottomBar() {
        binding.bottomBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val height = bottom - top
            if (height <= 0 || height == oldBottom - oldTop) return@addOnLayoutChangeListener

            binding.nestedScroll.updatePadding(bottom = height + dp(16))
            binding.nestedScroll.post { updateListFillHeight() }
        }
    }

    /**
     * 子布局 B 下方的列表容器要占满剩余视口：
     * 列表项少时靠 minimumHeight 撑起来，列表项多时自然高度已经超过它，
     * RecyclerView 继续随内容增高，由外层 NestedScrollView 统一滚动。
     */
    private fun updateListFillHeight() {
        if (!childBBinding.root.isVisible || binding.nestedScroll.height == 0) return

        val listSection = childBBinding.listSection
        val scrollLocation = IntArray(2)
        val listLocation = IntArray(2)
        binding.nestedScroll.getLocationInWindow(scrollLocation)
        listSection.getLocationInWindow(listLocation)

        // getLocationInWindow 含滚动偏移，这里还原成静止时的位置。
        val topInViewport = listLocation[1] - scrollLocation[1] + binding.nestedScroll.scrollY
        val available =
            binding.nestedScroll.height - binding.nestedScroll.paddingBottom - topInViewport

        val minHeight = available.coerceAtLeast(0)
        if (listSection.minimumHeight != minHeight) {
            listSection.minimumHeight = minHeight
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 简单的列表 Adapter：只负责把当前数量映射成 0..count-1 的条目。 */
    private class DemoListAdapter : RecyclerView.Adapter<DemoListAdapter.Holder>() {

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
                    context.getString(R.string.nested_demo_item_title, position + 1)
                binding.itemSubtitle.text = context.getString(R.string.nested_demo_item_subtitle)
                binding.itemDivider.isVisible = position < count - 1
            }
        }
    }

    private companion object {
        const val CHILD_A = 0
        const val CHILD_B = 1
        const val LIST_COUNT_LONG = 20
    }
}
