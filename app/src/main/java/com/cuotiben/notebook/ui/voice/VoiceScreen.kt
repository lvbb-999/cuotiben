package com.cuotiben.notebook.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cuotiben.notebook.MistakeBookApp
import com.cuotiben.notebook.data.Subjects
import com.cuotiben.notebook.ui.components.SectionTitle
import com.cuotiben.notebook.ui.components.SoftCard
import com.cuotiben.notebook.ui.components.SubjectChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MistakeBookApp
    val viewModel: VoiceViewModel = viewModel(
        factory = viewModelFactory {
            initializer { VoiceViewModel(context.applicationContext, app.repository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importModel) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startRecording()
    }

    fun toggleRecording() {
        if (state.recording) {
            viewModel.stopRecording()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("语音录题") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Button(
                    onClick = { viewModel.save(onSaved) },
                    enabled = state.hasContent && !state.saving,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                ) {
                    Text(
                        text = if (state.saving) "正在保存…" else "保存这道错题",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            if (!state.modelReady) {
                ModelSetupCard(
                    importing = state.importing,
                    progress = state.importProgress,
                    onPickModel = { importLauncher.launch(arrayOf("*/*")) },
                )
            } else {
                MicCard(
                    recording = state.recording,
                    engineLoading = state.engineLoading,
                    seconds = state.seconds,
                    onToggle = ::toggleRecording,
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.error ?: "",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SoftCard {
                SectionTitle(
                    title = "识别文本",
                    trailing = {
                        TextButton(
                            onClick = viewModel::parseTranscript,
                            enabled = state.liveText.isNotBlank(),
                        ) {
                            Text("重新解析")
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.liveText,
                    onValueChange = viewModel::setLiveText,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("说的题目会实时出现在这里，也可以直接手打或粘贴") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            SoftCard {
                SectionTitle("整理成错题")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.questionNo,
                    onValueChange = viewModel::setQuestionNo,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("题号") },
                    placeholder = { Text("如：第 12 题") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.stem,
                    onValueChange = viewModel::setStem,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("题干") },
                    placeholder = { Text("题目内容") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.options,
                    onValueChange = viewModel::setOptions,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("选项") },
                    placeholder = { Text("A. …\nB. …\nC. …\nD. …") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.answer,
                    onValueChange = viewModel::setAnswer,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("答案") },
                    placeholder = { Text("如：B，或写下关键步骤") },
                    minLines = 2,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("科目", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Subjects.options) { subject ->
                        SubjectChip(
                            label = subject,
                            selected = subject == state.subject,
                            onClick = { viewModel.setSubject(subject) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "语音识别在手机本地完成，录音不会上传。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MicCard(
    recording: Boolean,
    engineLoading: Boolean,
    seconds: Int,
    onToggle: () -> Unit,
) {
    val container = if (recording) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = container,
                enabled = !engineLoading,
            ) {
                Box(
                    modifier = Modifier.size(86.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (engineLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (recording) "结束录音" else "开始录音",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    engineLoading -> "正在加载语音模型…"
                    recording -> "正在录音 ${formatDuration(seconds)}，点一下结束"
                    else -> "点一下，开始说题目"
                },
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "可以这样念：第二题，下列哪个选项正确，A…，B…，答案是B",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ModelSetupCard(
    importing: Boolean,
    progress: Int,
    onPickModel: () -> Unit,
) {
    SoftCard {
        SectionTitle("先导入离线语音模型")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "语音识别使用 Vosk 中文模型（约 42MB）。应用完全离线，不会自动下载，需要手动导入一次。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "1. 在电脑打开 alphacephei.com/vosk/models，下载 vosk-model-small-cn-0.22.zip\n" +
                "2. 把 zip 传到手机\n" +
                "3. 点下面的按钮选择这个 zip，等待解压完成",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPickModel,
            enabled = !importing,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (progress > 0) "已解压 $progress 个文件…" else "正在准备…")
            } else {
                Text("选择模型 zip")
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(minutes, rest)
}
