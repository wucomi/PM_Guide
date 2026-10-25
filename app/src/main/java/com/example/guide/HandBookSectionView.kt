package com.example.guide

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * API 手册的单张能力卡片。
 *
 * ## 使用方式
 * 卡片结构、编号、标题、说明、代码块和操作区定义在
 * `view_handbook_section.xml` 中；编号/标题/说明/代码通过自定义属性传入。
 *
 * ```xml
 * <com.example.guide.HandBookSectionView
 *     app:hbNumber="1"
 *     app:hbTitle="标题"
 *     app:hbDescription="说明"
 *     app:hbCode="@string/code" />
 * ```
 *
 * ## 子 View 分流
 * 标签内部声明的子 View 会在 `onFinishInflate` 中移动：
 * `match_parent` → 正文区；其余 → 横向操作区。
 */
class HandBookSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 内层 MaterialCardView；XML 里声明的按钮不会被误当成卡片移动。 */
    private val sectionCard: View

    /** 横向操作区，所有 wrap_content 的子按钮会移动到这里。 */
    private val actionContainer: LinearLayout

    /** 正文区，match_parent 的子 View（如 RecyclerView）会移动到这里。 */
    private val bodyContainer: LinearLayout

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_handbook_section, this, true)
        // attachToRoot = true 时 inflate 返回的是 root（也就是 this），
        // 必须按 id 取内层卡片，否则会把卡片本身也当成待移动的子 View。
        sectionCard = findViewById(R.id.sectionCard)
        actionContainer = sectionCard.findViewById(R.id.sectionActions)
        bodyContainer = sectionCard.findViewById(R.id.sectionBody)
        bindContent(attrs)
    }

    /** 读取自定义属性并填充编号、标题、说明和代码块。 */
    private fun bindContent(attrs: AttributeSet?) {
        val typed = context.obtainStyledAttributes(attrs, R.styleable.HandBookSectionView)
        try {
            val number = typed.getInt(R.styleable.HandBookSectionView_hbNumber, 0)
            val title = typed.getString(R.styleable.HandBookSectionView_hbTitle).orEmpty()
            val description =
                typed.getString(R.styleable.HandBookSectionView_hbDescription).orEmpty()
            val code = typed.getString(R.styleable.HandBookSectionView_hbCode).orEmpty()

            sectionCard.findViewById<TextView>(R.id.sectionBadge).text =
                number.toString().padStart(2, '0')
            sectionCard.findViewById<TextView>(R.id.sectionTitle).text = title
            sectionCard.findViewById<TextView>(R.id.sectionDescription).text = description
            sectionCard.findViewById<TextView>(R.id.sectionCode).text = code
        } finally {
            typed.recycle()
        }
    }

    /**
     * 把 XML 中声明在卡片标签内部的子 View 分流：
     * - `match_parent`：正文区（例如 RecyclerView）；
     * - 其余：横向操作区（按钮、形状预览）。
     *
     * 卡片本身会被跳过，避免形成视图环。
     */
    override fun onFinishInflate() {
        super.onFinishInflate()
        val childrenToMove = ArrayList<View>()
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child !== sectionCard) {
                childrenToMove.add(child)
            }
        }
        childrenToMove.forEach { child ->
            val existing = child.layoutParams
            removeView(child)
            val params = if (existing is LayoutParams) {
                existing
            } else {
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }
            if (params.width == LayoutParams.MATCH_PARENT) {
                // match_parent 的子 View（如 RecyclerView）放进正文区
                bodyContainer.addView(child, params)
            } else {
                params.marginEnd = dp(8)
                actionContainer.addView(child, params)
            }
        }
    }

    /** dp 转 px。 */
    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
