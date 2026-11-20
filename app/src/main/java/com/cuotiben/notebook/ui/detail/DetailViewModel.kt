package com.cuotiben.notebook.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.MistakeRepository
import com.cuotiben.notebook.data.isDone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val item: Mistake? = null,
    val dirty: Boolean = false,
    val savedAt: Long? = null,
)

class DetailViewModel(
    private val repository: MistakeRepository,
    private val mistakeId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val item = repository.get(mistakeId)
            _uiState.value = DetailUiState(loading = false, item = item)
        }
    }

    fun edit(transform: (Mistake) -> Mistake) {
        val current = _uiState.value.item ?: return
        val updated = transform(current)
        _uiState.update { it.copy(item = updated, dirty = true) }
        scheduleSave(updated)
    }

    fun toggleDone() {
        val current = _uiState.value.item ?: return
        val now = System.currentTimeMillis()
        val updated = if (current.isDone) {
            current.copy(
                status = Mistake.STATUS_TODO,
                completedAt = null,
                nextReviewAt = null,
            )
        } else {
            current.copy(
                status = Mistake.STATUS_DONE,
                completedAt = now,
                nextReviewAt = null,
            )
        }
        saveImmediately(updated)
    }

    fun toggleFavorite() {
        val current = _uiState.value.item ?: return
        saveImmediately(current.copy(favorite = !current.favorite))
    }

    fun delete(onDeleted: () -> Unit) {
        val current = _uiState.value.item ?: return
        saveJob?.cancel()
        viewModelScope.launch {
            repository.delete(current)
            onDeleted()
        }
    }

    /** 返回上一页前把尚未落盘的编辑立即写入。 */
    fun flush() {
        val state = _uiState.value
        val item = state.item ?: return
        if (!state.dirty) return
        saveJob?.cancel()
        repository.updateAsync(item)
        _uiState.update { it.copy(dirty = false) }
    }

    private fun scheduleSave(item: Mistake) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repository.update(item)
            _uiState.update { state ->
                if (state.item == item) {
                    state.copy(dirty = false, savedAt = System.currentTimeMillis())
                } else {
                    state
                }
            }
        }
    }

    private fun saveImmediately(item: Mistake) {
        _uiState.update { it.copy(item = item, dirty = true) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            repository.update(item)
            _uiState.update { state ->
                if (state.item == item) {
                    state.copy(dirty = false, savedAt = System.currentTimeMillis())
                } else {
                    state
                }
            }
        }
    }
}
