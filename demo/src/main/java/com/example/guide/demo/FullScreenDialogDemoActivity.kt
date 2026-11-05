package com.example.guide.demo

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.example.guide.demo.databinding.ActivityFullScreenDialogDemoBinding
import com.example.guide.demo.databinding.DialogFullScreenDemoBinding

/** 全屏 Dialog Demo 入口：对比 DialogFragment 与传统 Dialog 两种实现。 */
class FullScreenDialogDemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenDialogDemoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36（Android 15+）强制 edge-to-edge；enableEdgeToEdge 让 Android 8~14 行为一致
        enableEdgeToEdge()
        binding = ActivityFullScreenDialogDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 系统栏区域用 padding 避开，防止标题和按钮顶到状态栏/手势条下面
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }

        // 方式一：DialogFragment（推荐），由 FragmentManager 托管，生命周期安全
        binding.btnShowDialogFragment.setOnClickListener {
            FullScreenDemoDialogFragment.show(supportFragmentManager)
        }

        // 方式二：传统 Dialog，需要自己管理重建/泄漏
        binding.btnShowPlainDialog.setOnClickListener { showPlainDialog() }
    }

    /** 传统 Dialog 版：全屏主题 + 手动铺满 + 沉浸式；页面旋转重建时 Dialog 会消失，需要自己处理。 */
    private fun showPlainDialog() {
        val dialog = Dialog(this, R.style.Theme_FullScreenDialog)
        val dialogBinding = DialogFullScreenDemoBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.setText(R.string.full_screen_dialog_heading_plain)
        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirm.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(dialogBinding.root)
        dialog.show()
        applyFullScreenImmersive(dialog.window)
    }

    /** 与 DialogFragment 版一致：铺满 + 铺进挖孔区 + 内容延伸到系统栏下 + 隐藏系统栏（下滑唤出）。 */
    private fun applyFullScreenImmersive(window: Window?) {
        window?.apply {
            // 关键：不设置就是 wrap_content，必须手动铺满
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 挖孔/刘海屏：状态栏隐藏后 DEFAULT 模式保留挖孔区，顶部会留出空隙，必须 SHORT_EDGES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}
