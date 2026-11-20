package com.cuotiben.notebook.ui.detail

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.cuotiben.notebook.MistakeBookApp
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.Reasons
import com.cuotiben.notebook.data.Subjects
import com.cuotiben.notebook.data.isDone
import com.cuotiben.notebook.data.tagList
import com.cuotiben.notebook.ui.components.EmptyState
import com.cuotiben.notebook.ui.components.SectionTitle
import com.cuotiben.notebook.ui.components.SoftCard
import com.cuotiben.notebook.ui.components.StatusPill
import com.cuotiben.notebook.ui.components.SubjectChip
import com.cuotiben.notebook.ui.components.TagPill
import com.cuotiben.notebook.util.DateFormats
import com.cuotiben.notebook.util.ImageStore
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    mistakeId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MistakeBookApp
    val viewModel: DetailViewModel = viewModel(
        key = "detail-$mistakeId",
        factory = viewModelFactory {
            initializer { DetailViewModel(app.repository, mistakeId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showDelete by rememberSaveable { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    val item = state.item

    fun leave() {
        viewModel.flush()
        onBack()
    }

    BackHandler { leave() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("错题详情") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (item.favorite) {
                                    Icons.Rounded.Star
                                } else {
                                    Icons.Rounded.StarBorder
                                },
                                contentDescription = if (item.favorite) "取消收藏" else "收藏",
                                tint = if (item.favorite) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { shareMistake(context, item) }) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "分享",
                            )
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            state.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            item == null -> {
                EmptyState(
                    emoji = "😕",
                    title = "这道错题不存在",
                    subtitle = "它可能已经被删除了",
                    actionLabel = "返回",
                    onAction = { leave() },
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                DetailContent(
                    item = item,
                    dirty = state.dirty,
                    savedAt = state.savedAt,
                    topPadding = padding.calculateTopPadding(),
                    bottomPadding = padding.calculateBottomPadding(),
                    onEdit = viewModel::edit,
                    onToggleDone = viewModel::toggleDone,
                    onFullscreen = { fullscreen = true },
                )
            }
        }
    }

    if (fullscreen && item != null && item.imagePath.isNotBlank() && File(item.imagePath).exists()) {
        FullscreenImage(path = item.imagePath, onClose = { fullscreen = false })
    }

    if (showDelete && item != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这道错题？") },
            text = { Text("题目图片和整理内容都会从本机删除，且无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        viewModel.delete { onBack() }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun DetailContent(
    item: Mistake,
    dirty: Boolean,
    savedAt: Long?,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onEdit: ((Mistake) -> Mistake) -> Unit,
    onToggleDone: () -> Unit,
    onFullscreen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = topPadding + 6.dp, bottom = bottomPadding + 28.dp),
    ) {
        val image = remember(item.imagePath) {
            item.imagePath.takeIf { it.isNotBlank() }
                ?.let { path -> File(path).takeIf { file -> file.exists() } }
        }
        if (image != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onFullscreen),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 170.dp, max = 360.dp),
                ) {
                    AsyncImage(
                        model = image,
                        contentDescription = "题目图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "点击放大",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = onToggleDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (item.isDone) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                contentColor = if (item.isDone) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondary
                },
            ),
        ) {
            Icon(
                imageVector = if (item.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.TaskAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (item.isDone) "已掌握 · 点击重新攻克" else "标记为已掌握",
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(done = item.isDone)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "收录于 ${DateFormats.dateTime(item.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = when {
                    dirty -> "正在保存…"
                    savedAt != null -> "已自动保存"
                    else -> "自动保存"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        SoftCard {
            SectionTitle("题目内容")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = item.text,
                onValueChange = { value -> onEdit { it.copy(text = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("题干与选项") },
                placeholder = { Text("拍照识别或语音录入的文字会出现在这里，识别有误可直接修改") },
                minLines = 3,
                maxLines = 12,
                shape = RoundedCornerShape(14.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        SoftCard {
            SectionTitle("基本信息")
            Spacer(Modifier.height(10.dp))
            OptionChips(
                options = Subjects.options,
                selected = item.subject,
                onSelect = { subject ->
                    onEdit { it.copy(subject = subject) }
                },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = item.questionNo,
                onValueChange = { value -> onEdit { it.copy(questionNo = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("题号") },
                placeholder = { Text("如：第 12 题") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = item.title,
                onValueChange = { value -> onEdit { it.copy(title = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("知识点 / 备注") },
                placeholder = { Text("这道题考的是什么？") },
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "错因",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            OptionChips(
                options = Reasons.options,
                selected = item.reason,
                onSelect = { reason ->
                    onEdit { it.copy(reason = if (it.reason == reason) "" else reason) }
                },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = item.tags,
                onValueChange = { value -> onEdit { it.copy(tags = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标签") },
                placeholder = { Text("用逗号分隔，如：函数，易错") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        SoftCard {
            SectionTitle("我的答案")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = item.answer,
                onValueChange = { value -> onEdit { it.copy(answer = value) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("写下你的答案或关键步骤…") },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(14.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        SoftCard {
            SectionTitle("解析 / 正确解法")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = item.analysis,
                onValueChange = { value -> onEdit { it.copy(analysis = value) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("记录正确思路、易错点，下次复习一眼看懂…") },
                minLines = 5,
                maxLines = 12,
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (item.tagList.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(item.tagList) { tag ->
                    TagPill("#$tag")
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            text = buildString {
                append("已复习 ${item.reviewCount} 次")
                if (item.completedAt != null) {
                    append(" · 完成于 ${DateFormats.dateTime(item.completedAt)}")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "修改会自动保存到本机，图片仅保存在应用内。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptionChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            SubjectChip(
                label = option,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun FullscreenImage(path: String, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val image = remember(path) { File(path) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = image,
                contentDescription = "题目大图",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 6f)
                            scale = nextScale
                            offset = if (nextScale <= 1.01f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }
            Text(
                text = "双指缩放查看题目",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }
}

private fun shareMistake(context: Context, item: Mistake) {
    val text = buildString {
        append("【错题本】")
        append(item.subject)
        if (item.questionNo.isNotBlank()) append(" ").append(item.questionNo)
        append("\n")
        if (item.title.isNotBlank()) append(item.title).append("\n")
        if (item.text.isNotBlank()) append(item.text).append("\n")
        if (item.answer.isNotBlank()) append("答案：").append(item.answer).append("\n")
        if (item.analysis.isNotBlank()) append("解析：").append(item.analysis)
    }
    val file = if (item.imagePath.isNotBlank()) {
        ImageStore.shareableFile(context, item.imagePath)
    } else {
        null
    }
    val intent = if (file != null) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = android.content.ClipData.newRawUri("错题图片", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
    context.startActivity(Intent.createChooser(intent, "分享错题"))
}
