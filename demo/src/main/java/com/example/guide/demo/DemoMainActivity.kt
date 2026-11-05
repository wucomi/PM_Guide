package com.example.guide.demo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.guide.demo.databinding.ActivityDemoMainBinding

/** Demo module 的入口：按需打开各个独立示例。 */
class DemoMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDemoMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDemoMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardNestedScroll.setOnClickListener {
            startActivity(Intent(this, NestedScrollDemoActivity::class.java))
        }
        binding.cardVideoFirstFrame.setOnClickListener {
            startActivity(Intent(this, VideoFirstFrameActivity::class.java))
        }
        binding.cardSlotDemo.setOnClickListener {
            startActivity(Intent(this, SlotBottomBarDemoActivity::class.java))
        }
        binding.cardFullScreenDialog.setOnClickListener {
            startActivity(Intent(this, FullScreenDialogDemoActivity::class.java))
        }
    }
}
