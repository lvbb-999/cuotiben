package com.cuotiben.notebook.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.MistakeRepository
import com.cuotiben.notebook.util.DetectedRegion
import com.cuotiben.notebook.util.ImageStore
import com.cuotiben.notebook.util.QuestionDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelectUiState(
    val loading: Boolean = true,
    val image: ImageBitmap? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val regions: List<DetectedRegion> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val saving: Boolean = false,
    val detecting: Boolean = false,
    val ignoreHandwriting: Boolean = true,
    val handwritingLines: Int = 0,
    val totalLines: Int = 0,
    val error: String? = null,
    val manualMode: Boolean = false,
)

class SelectQuestionsViewModel(
    private val appContext: Context,
    private val repository: MistakeRepository,
    private val fileName: String,
) : ViewModel() {

    private val sourceFile = ImageStore.photoFile(appContext, fileName)
    private val detector = QuestionDetector()

    private var bitmap: Bitmap? = null
    private var detectJob: Job? = null

    private val _uiState = MutableStateFlow(SelectUiState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                ImageStore.loadNormalized(sourceFile, 1800)
            }
            if (loaded == null) {
                _uiState.update {
                    it.copy(loading = false, error = "图片加载失败，请返回重新拍摄")
                }
                return@launch
            }
            bitmap = loaded
            _uiState.update {
                it.copy(
                    loading = false,
                    image = loaded.asImageBitmap(),
                    imageWidth = loaded.width,
                    imageHeight = loaded.height,
                )
            }
            runDetection(loaded, _uiState.value.ignoreHandwriting)
        }
    }

    private fun runDetection(source: Bitmap, ignoreHandwriting: Boolean) {
        detectJob?.cancel()
        detectJob = viewModelScope.launch {
            _uiState.update { it.copy(detecting = true) }
            val result = withContext(Dispatchers.Default) {
                runCatching { detector.detect(source, ignoreHandwriting) }.getOrNull()
            }
            val state = _uiState.value
            val manual = state.regions.filter { it.label == MANUAL_LABEL }
            val auto = result?.regions?.takeIf { it.isNotEmpty() }
                ?: listOf(DetectedRegion(RectF(0f, 0f, 1f, 1f), null, "整页"))
            val regions = auto + manual
            val manualIndices = manual.indices.map { auto.size + it }.toSet()
            val selected = when {
                manualIndices.isNotEmpty() -> manualIndices
                regions.size == 1 -> setOf(0)
                else -> emptySet()
            }
            _uiState.update {
                it.copy(
                    detecting = false,
                    regions = regions,
                    selected = selected,
                    handwritingLines = result?.handwritingLines ?: 0,
                    totalLines = result?.totalLines ?: 0,
                )
            }
        }
    }

    fun setIgnoreHandwriting(enabled: Boolean) {
        if (_uiState.value.ignoreHandwriting == enabled || _uiState.value.detecting) return
        _uiState.update { it.copy(ignoreHandwriting = enabled) }
        bitmap?.let { runDetection(it, enabled) }
    }

    fun toggleRegion(index: Int) {
        _uiState.update { state ->
            val selection = state.selected.toMutableSet()
            if (!selection.add(index)) selection.remove(index)
            state.copy(selected = selection)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selected = state.regions.indices.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selected = emptySet()) }
    }

    fun setManualMode(enabled: Boolean) {
        _uiState.update { it.copy(manualMode = enabled) }
    }

    fun addManualRegion(rect: RectF) {
        val safe = RectF(
            rect.left.coerceIn(0f, 0.98f),
            rect.top.coerceIn(0f, 0.98f),
            rect.right.coerceIn(0.02f, 1f),
            rect.bottom.coerceIn(0.02f, 1f),
        )
        if (safe.right - safe.left < 0.03f || safe.bottom - safe.top < 0.02f) return
        _uiState.update { state ->
            val regions = state.regions + DetectedRegion(safe, null, MANUAL_LABEL)
            state.copy(
                regions = regions,
                selected = state.selected + regions.lastIndex,
                manualMode = false,
            )
        }
    }

    fun save(subject: String, onFinished: (Int) -> Unit) {
        val state = _uiState.value
        if (state.saving) return
        val source = bitmap ?: return
        val chosen = state.selected.sorted().mapNotNull { state.regions.getOrNull(it) }
        if (chosen.isEmpty()) return

        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val destFiles = chosen.map { ImageStore.newQuestionFile(appContext) }
            val results = withContext(Dispatchers.IO) {
                ImageStore.cropMany(
                    bitmap = source,
                    requests = chosen.mapIndexed { index, region ->
                        region.rect to destFiles[index]
                    },
                )
            }
            var count = 0
            withContext(Dispatchers.IO) {
                chosen.forEachIndexed { index, region ->
                    if (results.getOrElse(index) { false }) {
                        repository.insert(
                            Mistake(
                                imagePath = destFiles[index].absolutePath,
                                subject = subject,
                                questionNo = if (
                                    region.label == "整页" || region.label == MANUAL_LABEL
                                ) {
                                    ""
                                } else {
                                    region.label
                                },
                                text = region.text.take(4000),
                            ),
                        )
                        count++
                    }
                }
            }
            _uiState.update { it.copy(saving = false) }
            if (results.isNotEmpty() && results.all { it }) {
                runCatching { sourceFile.delete() }
            }
            onFinished(count)
        }
    }

    override fun onCleared() {
        super.onCleared()
        detectJob?.cancel()
        detector.close()
        bitmap = null
    }

    companion object {
        private const val MANUAL_LABEL = "手动框选"
    }
}
