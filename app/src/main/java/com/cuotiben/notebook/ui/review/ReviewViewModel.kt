package com.cuotiben.notebook.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.MistakeRepository
import com.cuotiben.notebook.data.isDone
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val loading: Boolean = true,
    val queue: List<Mistake> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val reviewedCount: Int = 0,
    val totalTodo: Int = 0,
    val saving: Boolean = false,
) {
    val current: Mistake? get() = queue.getOrNull(index)
    val finished: Boolean get() = !loading && index >= queue.size
}

class ReviewViewModel(private val repository: MistakeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val all = repository.observeAll().first()
            val now = System.currentTimeMillis()
            val due = all
                .filter { !it.isDone && (it.nextReviewAt == null || it.nextReviewAt <= now) }
                .sortedWith(
                    compareBy<Mistake> { it.nextReviewAt ?: 0L }
                        .thenByDescending { it.createdAt },
                )
            _uiState.value = ReviewUiState(
                loading = false,
                queue = due,
                totalTodo = all.count { !it.isDone },
            )
        }
    }

    fun reveal() {
        _uiState.update { it.copy(revealed = true) }
    }

    fun mark(known: Boolean) {
        val state = _uiState.value
        val current = state.current ?: return
        if (state.saving) return
        _uiState.update { it.copy(saving = true) }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = if (known) {
                current.copy(
                    status = Mistake.STATUS_DONE,
                    completedAt = now,
                    nextReviewAt = null,
                    reviewCount = current.reviewCount + 1,
                )
            } else {
                val intervalDays = when (current.reviewCount) {
                    0 -> 1L
                    1 -> 2L
                    2 -> 4L
                    3 -> 7L
                    else -> 15L
                }
                current.copy(
                    reviewCount = current.reviewCount + 1,
                    nextReviewAt = now + intervalDays * 24L * 60L * 60L * 1000L,
                )
            }
            repository.update(updated)
            _uiState.update {
                it.copy(
                    index = it.index + 1,
                    revealed = false,
                    reviewedCount = it.reviewedCount + 1,
                    saving = false,
                )
            }
        }
    }
}
