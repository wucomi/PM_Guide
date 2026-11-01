package com.example.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.guide.databinding.ViewDemoItemBinding

/** RecyclerView 示例适配器，固定 8 项方便演示滚动与等待。 */
class DemoItemAdapter(
    /** item 内操作按钮的点击回调。 */
    private val onAction: (position: Int) -> Unit
) : RecyclerView.Adapter<DemoItemAdapter.ItemHolder>() {

    /** ViewBinding 形式的 ViewHolder。 */
    class ItemHolder(val binding: ViewDemoItemBinding) : RecyclerView.ViewHolder(binding.root)

    /** 创建 item 视图。 */
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

    /** 固定 8 个 item。 */
    override fun getItemCount(): Int = 8
}
