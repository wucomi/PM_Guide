package com.example.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.guide.databinding.ViewDemoItemBinding

/**
 * RecyclerView item 引导示例的适配器。
 *
 * ## 锚点设计
 * item 里的 [ViewDemoItemBinding.itemAction] 就是引导锚点；
 * 引导通过 `GuideAnchor.RecyclerViewItemAnchor(position, childId)` 定位它，
 * 不持有 itemView，因此 item 被回收、复用后依然有效。
 *
 * ## 数据
 * 固定 8 个 item，保证有需要自动滚动才能看到的目标。
 */
class DemoItemAdapter(
    /** item 内操作按钮的点击回调，参数是 adapter position。 */
    private val onAction: (position: Int) -> Unit
) : RecyclerView.Adapter<DemoItemAdapter.ItemHolder>() {

    /**
     * ViewBinding 形式的 ViewHolder。
     *
     * ## 说明
     * 避免在 item 内重复 `findViewById`。
     */
    class ItemHolder(val binding: ViewDemoItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder =
        ItemHolder(
            ViewDemoItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    /** 绑定标题、副标题和操作按钮。 */
    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        holder.binding.itemTitle.text = "Item ${position + 1}"
        holder.binding.itemSubtitle.text =
            if (position % 2 == 0) "跟随列表的锚点" else "可滚动的 item"
        holder.binding.itemAction.text = "操作 ${position + 1}"
        holder.binding.itemAction.setOnClickListener { onAction(position) }
    }

    /** 示例固定 8 个 item，保证有需要自动滚动才能看到的目标。 */
    override fun getItemCount(): Int = 8
}
