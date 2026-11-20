package com.cuotiben.notebook.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一道错题。题目可以是图片（拍照裁剪），也可以是纯文字（语音录入或 OCR 结果）。
 */
@Entity(tableName = "mistakes")
data class Mistake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val imagePath: String,
    val subject: String = Subjects.UNCATEGORIZED,
    val questionNo: String = "",
    val title: String = "",
    /** 题干与选项文字：拍照识别（已过滤手写）或语音录入，可继续编辑。 */
    @ColumnInfo(defaultValue = "''")
    val text: String = "",
    val answer: String = "",
    val analysis: String = "",
    val reason: String = "",
    val tags: String = "",
    val status: Int = STATUS_TODO,
    val favorite: Boolean = false,
    val reviewCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val nextReviewAt: Long? = null,
) {
    companion object {
        const val STATUS_TODO = 0
        const val STATUS_DONE = 1
    }
}

val Mistake.isDone: Boolean
    get() = status == Mistake.STATUS_DONE

val Mistake.tagList: List<String>
    get() = tags.split(',', '，')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

val Mistake.isDue: Boolean
    get() = !isDone && (nextReviewAt == null || nextReviewAt <= System.currentTimeMillis())

object Subjects {
    const val UNCATEGORIZED = "未分类"

    val options = listOf(
        UNCATEGORIZED,
        "数学",
        "语文",
        "英语",
        "物理",
        "化学",
        "生物",
        "政治",
        "历史",
        "地理",
    )
}

object Reasons {
    val options = listOf(
        "概念不清",
        "计算失误",
        "审题不清",
        "方法不当",
        "记忆不牢",
        "粗心大意",
    )
}
