package com.example.guide.demo

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.example.guide.demo.databinding.ActivityVideoFirstFrameBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Demo：加载一个视频 URL 的首帧并显示到 ImageView，提供两种实现。
 *
 * 1. MediaMetadataRetriever：直接以 http(s) URL 为数据源，后台线程取帧；
 * 2. Glide：`RequestOptions.frameOf(0)` 一行完成加载，线程和生命周期由 Glide 管理。
 */
class VideoFirstFrameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoFirstFrameBinding

    /** 同一时间只处理一个解码任务。 */
    private val decodeExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** 每次点击自增，回到主线程时用来丢弃过期结果。 */
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoFirstFrameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.loadButton.setOnClickListener { loadFirstFrame() }
        binding.glideButton.setOnClickListener { loadWithGlide() }
    }

    /** 实现一：MediaMetadataRetriever 在后台解码首帧，再切回主线程显示。 */
    private fun loadFirstFrame() {
        val url = readUrl() ?: return

        // 取消可能仍在进行的 Glide 请求，避免它稍后覆盖这里的结果。
        Glide.with(this).clear(binding.preview)

        val request = ++requestId
        setLoading(true)

        // 先清掉上一张图，让用户明确看到正在加载。
        binding.preview.setImageDrawable(null)
        binding.status.text = getString(R.string.video_first_frame_loading)

        // 按 ImageView 宽度缩放，避免 4K 视频在内存里长期保留一张大图。
        val targetWidth = binding.preview.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels

        decodeExecutor.execute {
            val result = runCatching { decodeFirstFrame(url, targetWidth) }
            runOnUiThread {
                if (request != requestId || isFinishing || isDestroyed) return@runOnUiThread
                setLoading(false)
                result
                    .onSuccess { bitmap ->
                        if (bitmap == null) {
                            showError(getString(R.string.video_first_frame_no_frame))
                        } else {
                            binding.preview.setImageBitmap(bitmap)
                            binding.emptyHint.isVisible = false
                            binding.status.text = getString(
                                R.string.video_first_frame_success,
                                bitmap.width,
                                bitmap.height
                            )
                        }
                    }
                    .onFailure { error ->
                        showError(error.message ?: error.javaClass.simpleName)
                    }
            }
        }
    }

    /** 取输入框 URL；为空时在输入框上报错并返回 null。 */
    private fun readUrl(): String? {
        val url = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (url.isEmpty()) {
            binding.urlInputLayout.error = getString(R.string.video_first_frame_url_required)
            return null
        }
        binding.urlInputLayout.error = null
        return url
    }

    /**
     * 实现二：Glide + frameOf(0)。
     *
     * 注意：Glide 会先把远程视频完整下载到磁盘缓存再解码；视频很大时，
     * 首帧耗时可能比 MediaMetadataRetriever 的按需读取更长。
     */
    private fun loadWithGlide() {
        val url = readUrl() ?: return

        // 让尚未完成的 MediaMetadataRetriever 任务丢弃结果。
        requestId++
        setLoading(true)
        binding.preview.setImageDrawable(null)
        binding.status.text = getString(R.string.video_first_frame_loading)

        Glide.with(this)
            .load(url)
            .apply(RequestOptions.frameOf(0L))
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    error: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (!isFinishing && !isDestroyed) {
                        setLoading(false)
                        showError(
                            error?.rootCauses
                                ?.joinToString { it.message.orEmpty() }
                                ?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.video_first_frame_failed_generic)
                        )
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (!isFinishing && !isDestroyed) {
                        setLoading(false)
                        binding.emptyHint.isVisible = false
                        binding.status.text = getString(
                            R.string.video_first_frame_success,
                            resource.intrinsicWidth,
                            resource.intrinsicHeight
                        )
                    }
                    return false
                }
            })
            .into(binding.preview)
    }

    /**
     * 同步解码首帧，调用方需保证在后台线程执行。
     *
     * @return 已应用旋转角并按 [targetWidthPx] 缩放的首帧；取不到时返回 null。
     */
    private fun decodeFirstFrame(url: String, targetWidthPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            // 第二个参数是 HTTP 头，需要鉴权的视频可以在这里加 Authorization。
            retriever.setDataSource(url, emptyMap())
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            // 优先第 0 微秒的关键帧，取不到再退到第 1 秒。
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            scaleToWidth(rotate(frame, rotation), targetWidthPx)
        } finally {
            retriever.release()
        }
    }

    /** MediaMetadataRetriever 不应用旋转元数据，竖屏视频需要手动转正。 */
    private fun rotate(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** 等比缩放到目标宽度；目标宽度无效或原图更小时保持原样。 */
    private fun scaleToWidth(bitmap: Bitmap, targetWidthPx: Int): Bitmap {
        if (targetWidthPx <= 0 || bitmap.width <= targetWidthPx) return bitmap
        val targetHeight = (bitmap.height * (targetWidthPx.toFloat() / bitmap.width))
            .roundToInt()
            .coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidthPx, targetHeight, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** 加载中转圈并禁用按钮；空闲时按有无图片决定空态提示是否显示。 */
    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.loadButton.isEnabled = !loading
        binding.glideButton.isEnabled = !loading
        binding.emptyHint.isVisible = !loading && binding.preview.drawable == null
    }

    /** 失败时清空预览并展示原因。 */
    private fun showError(message: String) {
        binding.preview.setImageDrawable(null)
        binding.emptyHint.isVisible = true
        binding.status.text = getString(R.string.video_first_frame_failed, message)
    }

    override fun onDestroy() {
        // 让还在解码的任务回主线程时直接丢弃结果。
        requestId++
        decodeExecutor.shutdownNow()
        super.onDestroy()
    }
}
