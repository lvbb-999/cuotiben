package com.cuotiben.notebook.ui.voice

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.MistakeRepository
import com.cuotiben.notebook.data.Subjects
import com.cuotiben.notebook.util.voice.SpokenQuestionParser
import com.cuotiben.notebook.util.voice.VoiceRecorder
import com.cuotiben.notebook.util.voice.VoskModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model

data class VoiceUiState(
    val modelReady: Boolean = false,
    val importing: Boolean = false,
    val importProgress: Int = 0,
    val engineLoading: Boolean = false,
    val recording: Boolean = false,
    val seconds: Int = 0,
    val liveText: String = "",
    val questionNo: String = "",
    val stem: String = "",
    val options: String = "",
    val answer: String = "",
    val subject: String = Subjects.UNCATEGORIZED,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val hasContent: Boolean
        get() = stem.isNotBlank() || options.isNotBlank() || liveText.isNotBlank()
}

class VoiceViewModel(
    private val appContext: Context,
    private val repository: MistakeRepository,
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("cuotiben_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        VoiceUiState(
            modelReady = VoskModelManager.isInstalled(appContext),
            subject = prefs.getString("last_subject", Subjects.UNCATEGORIZED)
                ?: Subjects.UNCATEGORIZED,
        ),
    )
    val uiState = _uiState.asStateFlow()

    private var model: Model? = null
    private var recorder: VoiceRecorder? = null
    private var ticker: Job? = null
    private var fieldsTouched = false

    fun importModel(uri: Uri) {
        if (_uiState.value.importing) return
        _uiState.update { it.copy(importing = true, importProgress = 0, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                VoskModelManager.importZip(appContext, uri) { progress ->
                    _uiState.update { it.copy(importProgress = progress) }
                }
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(importing = false, modelReady = true, importProgress = 100)
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            importing = false,
                            error = throwable.message ?: "模型导入失败",
                        )
                    }
                },
            )
        }
    }

    fun startRecording() {
        if (_uiState.value.recording || _uiState.value.engineLoading) return
        val modelDir = VoskModelManager.modelDir(appContext)
        if (!VoskModelManager.isInstalled(appContext)) {
            _uiState.update { it.copy(error = "还没有导入语音模型") }
            return
        }
        _uiState.update { it.copy(engineLoading = true, error = null) }
        viewModelScope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    model ?: Model(modelDir.absolutePath).also { model = it }
                }
            }
            loaded.onFailure { throwable ->
                _uiState.update {
                    it.copy(engineLoading = false, error = throwable.message ?: "语音模型加载失败")
                }
            }
            val ready = loaded.getOrNull() ?: return@launch
            fieldsTouched = false
            _uiState.update { it.copy(liveText = "", seconds = 0) }
            val session = VoiceRecorder(
                onPartial = { text ->
                    _uiState.update { it.copy(liveText = text) }
                },
                onFinal = { text ->
                    _uiState.update { it.copy(liveText = text, recording = false) }
                    stopTicker()
                    if (!fieldsTouched) parseText(text)
                },
                onError = { message ->
                    _uiState.update { it.copy(error = message, recording = false) }
                    stopTicker()
                },
            )
            recorder?.release()
            recorder = session
            val started = withContext(Dispatchers.IO) { session.start(appContext, ready) }
            if (!started) {
                _uiState.update { it.copy(engineLoading = false, recording = false) }
                return@launch
            }
            _uiState.update { it.copy(engineLoading = false, recording = true, seconds = 0) }
            startTicker()
        }
    }

    fun stopRecording() {
        if (!_uiState.value.recording) return
        recorder?.stop()
        stopTicker()
        _uiState.update { it.copy(recording = false) }
        if (!fieldsTouched) parseText(_uiState.value.liveText)
    }

    fun setLiveText(value: String) {
        _uiState.update { it.copy(liveText = value) }
    }

    fun parseTranscript() {
        parseText(_uiState.value.liveText)
    }

    fun setQuestionNo(value: String) {
        fieldsTouched = true
        _uiState.update { it.copy(questionNo = value) }
    }

    fun setStem(value: String) {
        fieldsTouched = true
        _uiState.update { it.copy(stem = value) }
    }

    fun setOptions(value: String) {
        fieldsTouched = true
        _uiState.update { it.copy(options = value) }
    }

    fun setAnswer(value: String) {
        fieldsTouched = true
        _uiState.update { it.copy(answer = value) }
    }

    fun setSubject(value: String) {
        _uiState.update { it.copy(subject = value) }
    }

    fun save(onSaved: (Long) -> Unit) {
        val state = _uiState.value
        if (state.saving) return
        val stem = state.stem.trim()
        val options = state.options.trim()
        val content = buildString {
            append(stem)
            if (options.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(options)
            }
        }.trim()
        val finalText = content.ifBlank { state.liveText.trim() }
        if (finalText.isBlank()) {
            _uiState.update { it.copy(error = "先写下题干，或用语音说出题目") }
            return
        }
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val id = repository.insert(
                Mistake(
                    imagePath = "",
                    subject = state.subject,
                    questionNo = state.questionNo.trim(),
                    text = finalText,
                    answer = state.answer.trim(),
                ),
            )
            prefs.edit().putString("last_subject", state.subject).apply()
            _uiState.update { it.copy(saving = false) }
            onSaved(id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun parseText(text: String) {
        if (text.isBlank()) return
        val parsed = SpokenQuestionParser.parse(text)
        _uiState.update { state ->
            state.copy(
                questionNo = parsed.questionNo.ifBlank { state.questionNo },
                stem = parsed.stem.ifBlank { state.stem },
                options = parsed.options.ifBlank { state.options },
                answer = parsed.answer.ifBlank { state.answer },
            )
        }
    }

    private fun startTicker() {
        stopTicker()
        val startedAt = System.currentTimeMillis()
        ticker = viewModelScope.launch {
            while (isActive && _uiState.value.recording) {
                _uiState.update {
                    it.copy(seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt())
                }
                delay(1000)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTicker()
        recorder?.release()
        recorder = null
        model?.let { runCatching { it.close() } }
        model = null
    }
}
