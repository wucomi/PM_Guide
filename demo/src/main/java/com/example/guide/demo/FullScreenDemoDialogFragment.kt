package com.example.guide.demo

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.guide.demo.databinding.DialogFullScreenDemoBinding

/**
 * 全屏 Dialog 演示：DialogFragment + 全屏主题 + 沉浸式（推荐做法）。
 *
 * 关键点：
 * - [onCreate] 里 [setStyle] 指定全屏主题：windowIsFloating=false、无标题、透明 window 背景、进出场动画；
 * - [onStart] 里把 window 宽高设为 MATCH_PARENT，否则默认按内容包裹，铺不满屏幕；
 * - targetSdk 36（Android 15+）强制 edge-to-edge（windowOptOutEdgeToEdgeEnforcement 已失效），
 *   沉浸式用 [WindowCompat.setDecorFitsSystemWindows] + [WindowInsetsControllerCompat] 在代码里实现，
 *   windowIsFloating=false 的 Dialog window 与 Activity window 同样适用；
 * - DialogFragment 由 FragmentManager 托管，旋转屏幕/重建后自动恢复，比裸 Dialog 更稳。
 */
class FullScreenDemoDialogFragment : DialogFragment() {

    private var _binding: DialogFullScreenDemoBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 必须在 onCreate 里设置样式；主题定义见 values/themes.xml 的 Theme.FullScreenDialog
        setStyle(STYLE_NORMAL, R.style.Theme_FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFullScreenDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDialogTitle.setText(R.string.full_screen_dialog_heading_fragment)
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnConfirm.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        // 全屏关键一：Dialog 默认是内容包裹尺寸，必须手动铺满
        dialog?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 沉浸式关键三：允许内容铺进挖孔/刘海区域。
            // 状态栏隐藏后，默认的 DEFAULT 模式会保留挖孔区不放内容，
            // 顶部就会留出一条不归 Dialog 管的空隙（露出 Activity 的底色）。
            // 与 enableEdgeToEdge 内部保持一致：28~29 用 SHORT_EDGES，30+ 用 ALWAYS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
            // 沉浸式：内容延伸到系统栏下面。targetSdk 36 强制 edge-to-edge，
            // 这里显式声明一次，Android 8~14 上也能拿到一致行为
            WindowCompat.setDecorFitsSystemWindows(this, false)
            // 沉浸式：隐藏状态栏与导航栏，从屏幕边缘下滑临时唤出；
            // 只想“延伸到系统栏后面”而不隐藏的话，删掉下面 apply 里这三行即可
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "FullScreenDemoDialog"

        /** 显示全屏 Dialog；用 TAG 去重避免重复添加。 */
        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                FullScreenDemoDialogFragment().show(fragmentManager, TAG)
            }
        }
    }
}
