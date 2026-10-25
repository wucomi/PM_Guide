package com.pm.guide

import android.content.Context
import androidx.core.content.edit

/**
 * “只引导一次”的持久化抽象。
 *
 * ## 职责
 * 组件只关心 key 是否展示过，不关心存储介质。
 *
 * ## 注入
 * 使用 [GuideDialogFragment.Builder.showOnce] 时，可通过
 * [GuideDialogFragment.Builder.storage] 替换默认实现。
 */
interface GuideStorage {
    /** key 对应的引导是否已经完成或跳过过。 */
    fun wasShown(key: String): Boolean

    /** 标记为已展示；只应在 FINISHED / SKIPPED 时调用。 */
    fun markShown(key: String)

    /** 清除标记，用于调试或“重新引导”。 */
    fun reset(key: String)
}

/**
 * 默认的 SharedPreferences 实现。
 *
 * 只保存“key 是否已完成/跳过”，使用组件私有文件。
 */
internal class SharedPreferencesGuideStorage(context: Context) : GuideStorage {

    /** 组件使用的私有存储文件；只保存 key 是否已展示。 */
    private val preferences =
        (context.applicationContext ?: context)
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 读取 key 是否已经完成/跳过过。 */
    override fun wasShown(key: String): Boolean = preferences.getBoolean(key, false)

    /** 写入“已展示”标记。 */
    override fun markShown(key: String) {
        preferences.edit { putBoolean(key, true) }
    }

    /** 清除单个 key 的标记。 */
    override fun reset(key: String) {
        preferences.edit { remove(key) }
    }

    private companion object {
        /** 引导状态的存储文件名。 */
        const val FILE_NAME = "com.pm.guide.state"
    }
}
