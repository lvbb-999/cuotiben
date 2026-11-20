package com.cuotiben.notebook.util.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 用 Vosk 做本地实时语音识别。SpeechService 自己管理麦克风线程，
 * 这里只负责生命周期和把「已确认片段 + 临时结果」拼成完整文本。
 */
class VoiceRecorder(
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {

    private val running = AtomicBoolean(false)
    private val committed = StringBuilder()
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null

    /** 返回是否成功开始录音。 */
    fun start(context: Context, model: Model): Boolean {
        if (running.get()) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError("没有麦克风权限")
            return false
        }

        releaseSession()
        committed.setLength(0)
        running.set(true)
        try {
            val sessionRecognizer = Recognizer(model, SAMPLE_RATE)
            val service = SpeechService(sessionRecognizer, SAMPLE_RATE)
            recognizer = sessionRecognizer
            speechService = service
            val started = service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String) {
                    val partial = jsonText(hypothesis, "partial")
                    if (partial.isNotBlank()) {
                        onPartial(committed.toString() + CjkText.joinTokens(partial))
                    }
                }

                override fun onResult(hypothesis: String) {
                    val text = jsonText(hypothesis, "text")
                    if (text.isNotBlank()) {
                        committed.append(CjkText.joinTokens(text))
                        onPartial(committed.toString())
                    }
                }

                override fun onFinalResult(hypothesis: String) {
                    val text = jsonText(hypothesis, "text")
                    if (text.isNotBlank()) committed.append(CjkText.joinTokens(text))
                    running.set(false)
                    onFinal(committed.toString())
                }

                override fun onError(exception: Exception) {
                    running.set(false)
                    onError(exception.message ?: "语音识别出错")
                }

                override fun onTimeout() = Unit
            })
            if (!started) {
                running.set(false)
                onError("麦克风正在被占用，请稍后再试")
                return false
            }
            return true
        } catch (throwable: Throwable) {
            running.set(false)
            onError(throwable.message ?: "语音识别启动失败")
            return false
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { speechService?.stop() }
    }

    fun release() {
        running.set(false)
        val worker = Thread { releaseSession() }
        worker.isDaemon = true
        worker.start()
    }

    private fun releaseSession() {
        speechService?.let { service ->
            runCatching { service.cancel() }
            runCatching { service.shutdown() }
        }
        speechService = null
        recognizer?.let { runCatching { it.close() } }
        recognizer = null
    }

    private fun jsonText(json: String, key: String): String =
        runCatching { JSONObject(json).optString(key, "") }.getOrDefault("")

    companion object {
        const val SAMPLE_RATE = 16000f
    }
}
