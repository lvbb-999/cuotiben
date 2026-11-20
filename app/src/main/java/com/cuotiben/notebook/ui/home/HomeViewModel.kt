package com.cuotiben.notebook.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.MistakeRepository
import com.cuotiben.notebook.data.Subjects
import com.cuotiben.notebook.data.isDone
import com.cuotiben.notebook.data.isDue
import com.cuotiben.notebook.data.tagList
import com.cuotiben.notebook.util.ImageStore
import com.cuotiben.notebook.util.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class StatusFilter(val label: String) {
    TODO("待攻克"),
    DONE("已掌握"),
    ALL("全部"),
}

data class HomeUiState(
    val items: List<Mistake> = emptyList(),
    val filter: StatusFilter = StatusFilter.TODO,
    val subject: String? = null,
    val query: String = "",
    val subjects: List<String> = emptyList(),
    val totalCount: Int = 0,
    val todoCount: Int = 0,
    val doneCount: Int = 0,
    val weekAdded: Int = 0,
    val dueCount: Int = 0,
    val loading: Boolean = true,
) {
    val completionRate: Float
        get() = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount
}

data class BackupUiState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val message: String? = null,
)

class HomeViewModel(private val repository: MistakeRepository) : ViewModel() {

    private val filter = MutableStateFlow(StatusFilter.TODO)
    private val subject = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState = _backupState.asStateFlow()

    val uiState = combine(
        repository.observeAll(),
        filter,
        subject,
        query,
    ) { all, currentFilter, currentSubject, currentQuery ->
        val weekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val visible = all.asSequence()
            .filter {
                when (currentFilter) {
                    StatusFilter.TODO -> !it.isDone
                    StatusFilter.DONE -> it.isDone
                    StatusFilter.ALL -> true
                }
            }
            .filter { currentSubject == null || it.subject == currentSubject }
            .filter { currentQuery.isBlank() || it.matches(currentQuery) }
            .toList()

        HomeUiState(
            items = visible,
            filter = currentFilter,
            subject = currentSubject,
            query = currentQuery,
            subjects = all.map { it.subject }.distinct().sortedBy { subjectOrder(it) },
            totalCount = all.size,
            todoCount = all.count { !it.isDone },
            doneCount = all.count { it.isDone },
            weekAdded = all.count { it.createdAt >= weekStart },
            dueCount = all.count { it.isDue },
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun setFilter(value: StatusFilter) {
        filter.value = value
    }

    fun setSubject(value: String?) {
        subject.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleDone(mistake: Mistake) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = if (mistake.isDone) {
                mistake.copy(
                    status = Mistake.STATUS_TODO,
                    completedAt = null,
                    nextReviewAt = null,
                )
            } else {
                mistake.copy(
                    status = Mistake.STATUS_DONE,
                    completedAt = now,
                    nextReviewAt = null,
                )
            }
            repository.update(updated)
        }
    }

    private fun subjectOrder(subject: String): Int {
        val index = Subjects.options.indexOf(subject)
        return if (index == -1) Int.MAX_VALUE else index
    }

    fun exportBackup(context: Context, uri: Uri) {
        if (_backupState.value.running) return
        _backupState.value = BackupUiState(running = true)
        viewModelScope.launch {
            val mistakes = repository.snapshot()
            val result = BackupManager.export(
                context = context.applicationContext,
                mistakes = mistakes,
                uri = uri,
                onProgress = { done, total ->
                    _backupState.update { it.copy(done = done, total = total) }
                },
            )
            _backupState.value = BackupUiState(
                message = result.fold(
                    onSuccess = { "已导出 $it 道错题" },
                    onFailure = { "导出失败：${it.message ?: "文件无法写入"}" },
                ),
            )
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        if (_backupState.value.running) return
        _backupState.value = BackupUiState(running = true)
        viewModelScope.launch {
            val result = BackupManager.read(
                context = context.applicationContext,
                uri = uri,
                onProgress = { done, total ->
                    _backupState.update { it.copy(done = done, total = total) }
                },
            )
            result.fold(
                onSuccess = { mistakes ->
                    var count = 0
                    mistakes.forEach { mistake ->
                        runCatching { repository.insert(mistake) }
                            .onSuccess { count++ }
                            .onFailure { ImageStore.deleteQuietly(mistake.imagePath) }
                    }
                    _backupState.value = BackupUiState(
                        message = "已导入 $count 道错题，已追加到现有列表",
                    )
                },
                onFailure = { throwable ->
                    _backupState.value = BackupUiState(
                        message = "导入失败：${throwable.message ?: "文件无法读取"}",
                    )
                },
            )
        }
    }

    fun clearBackupMessage() {
        _backupState.update { it.copy(message = null) }
    }

    private fun Mistake.matches(text: String): Boolean {
        val keyword = text.trim()
        if (keyword.isEmpty()) return true
        return listOf(
            subject,
            questionNo,
            title,
            text,
            answer,
            analysis,
            reason,
            tags,
            tagList.joinToString(" "),
        ).any { it.contains(keyword, ignoreCase = true) }
    }
}
