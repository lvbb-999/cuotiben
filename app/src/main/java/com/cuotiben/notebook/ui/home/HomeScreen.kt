package com.cuotiben.notebook.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.cuotiben.notebook.MistakeBookApp
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.isDone
import com.cuotiben.notebook.data.tagList
import com.cuotiben.notebook.ui.Routes
import com.cuotiben.notebook.ui.components.AppBottomBar
import com.cuotiben.notebook.ui.components.EmptyState
import com.cuotiben.notebook.ui.components.SoftCard
import com.cuotiben.notebook.ui.components.StatusPill
import com.cuotiben.notebook.ui.components.SubjectFilterRow
import com.cuotiben.notebook.ui.components.TagPill
import com.cuotiben.notebook.util.DateFormats
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onVoice: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as MistakeBookApp
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(app.repository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backup by viewModel.backupState.collectAsStateWithLifecycle()

    var searching by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.exportBackup(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingImport = uri }

    LaunchedEffect(backup.message) {
        val message = backup.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearBackupMessage()
    }

    LaunchedEffect(searching) {
        if (searching) {
            delay(150)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppBottomBar(currentRoute = Routes.HOME, onNavigate = onNavigate) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
                icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                text = { Text("拍错题", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 108.dp),
        ) {
            item {
                HomeHeader(
                    totalCount = state.totalCount,
                    searching = searching,
                    onVoice = onVoice,
                    onExport = {
                        exportLauncher.launch("cuotiben-backup-${LocalDate.now()}.zip")
                    },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onToggleSearch = {
                        searching = !searching
                        if (!searching) {
                            viewModel.setQuery("")
                            keyboard?.hide()
                        }
                    },
                )
            }
            item {
                OverviewCard(state)
            }
            item {
                StatusTabs(state = state, onSelect = viewModel::setFilter)
            }
            if (searching) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("搜索题目、答案、标签…") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = if (state.query.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清空")
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { keyboard?.hide() },
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
            if (state.subjects.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    SubjectFilterRow(
                        selected = state.subject,
                        subjects = state.subjects,
                        onSelect = viewModel::setSubject,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (state.loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.items.isEmpty()) {
                item {
                    val copy = emptyCopy(state)
                    EmptyState(
                        emoji = copy.first,
                        title = copy.second,
                        subtitle = copy.third,
                        actionLabel = if (state.query.isBlank()) "拍下第一道错题" else null,
                        onAction = onAdd,
                    )
                }
            } else {
                items(state.items, key = { it.id }) { mistake ->
                    MistakeCard(
                        mistake = mistake,
                        onClick = { onOpenDetail(mistake.id) },
                        onToggle = { viewModel.toggleDone(mistake) },
                    )
                }
            }
        }
    }

    if (backup.running) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (backup.total > 0) {
                            "正在处理 ${backup.done}/${backup.total}"
                        } else {
                            "正在准备…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入备份") },
            text = { Text("导入的错题会追加到现有列表，不会覆盖已有错题。是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        viewModel.importBackup(context, uri)
                    },
                ) {
                    Text("导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun HomeHeader(
    totalCount: Int,
    searching: Boolean,
    onVoice: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 12.dp, top = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${DateFormats.greeting()} · ${DateFormats.date(now)} ${DateFormats.weekday(now)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "错题本",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onVoice) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "语音录题",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (searching) "关闭搜索" else "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("导出备份") },
                        leadingIcon = {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onExport()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导入备份") },
                        leadingIcon = {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onImport()
                        },
                    )
                }
            }
        }
        Text(
            text = if (totalCount == 0) {
                "把不会的，变成会的"
            } else {
                "已收录 $totalCount 道错题 · 把不会的，变成会的"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverviewCard(state: HomeUiState) {
    SoftCard(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatCell(
                value = state.todoCount.toString(),
                label = "待攻克",
                color = MaterialTheme.colorScheme.secondary,
            )
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            StatCell(
                value = state.doneCount.toString(),
                label = "已掌握",
                color = MaterialTheme.colorScheme.primary,
            )
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            StatCell(
                value = state.weekAdded.toString(),
                label = "本周新增",
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "总完成率",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(state.completionRate * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { state.completionRate },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun RowScope.StatCell(value: String, label: String, color: Color) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusTabs(state: HomeUiState, onSelect: (StatusFilter) -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            StatusFilter.entries.forEach { filter ->
                val selected = state.filter == filter
                val count = when (filter) {
                    StatusFilter.TODO -> state.todoCount
                    StatusFilter.DONE -> state.doneCount
                    StatusFilter.ALL -> state.totalCount
                }
                Surface(
                    onClick = { onSelect(filter) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                ) {
                    Text(
                        text = "${filter.label} $count",
                        modifier = Modifier.padding(vertical = 9.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MistakeCard(
    mistake: Mistake,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val thumbnail = remember(mistake.imagePath) {
        mistake.imagePath.takeIf { it.isNotBlank() }?.let { File(it).takeIf { file -> file.exists() } }
    }
    SoftCard(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = "题目图片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = "文字题目",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagPill(
                        text = mistake.subject,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    if (mistake.questionNo.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = mistake.questionNo,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    StatusPill(done = mistake.isDone)
                }
                Spacer(Modifier.height(7.dp))
                val headline = mistake.title
                    .ifBlank { mistake.text.lineSequence().firstOrNull { it.isNotBlank() } ?: "" }
                    .ifBlank { mistake.answer }
                    .ifBlank { mistake.analysis }
                    .trim()
                Text(
                    text = headline.ifBlank { "点击查看详情，补上答案和错因" },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (headline.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mistake.tagList.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        mistake.tagList.take(2).forEach { tag ->
                            TagPill("#$tag")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateFormats.date(mistake.createdAt) +
                            if (mistake.reviewCount > 0) " · 复习 ${mistake.reviewCount} 次" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (mistake.isDone) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = if (mistake.isDone) "标记为未完成" else "标记为已完成",
                            tint = if (mistake.isDone) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun emptyCopy(state: HomeUiState): Triple<String, String, String> {
    if (state.query.isNotBlank()) {
        return Triple("🔍", "没有找到相关错题", "换个关键词，或到「全部」里找找看")
    }
    return when (state.filter) {
        StatusFilter.TODO -> Triple(
            "🌿",
            "没有待攻克的错题",
            "拍下新的错题，或去「已掌握」里回顾一下",
        )
        StatusFilter.DONE -> Triple(
            "📚",
            "还没有已掌握的错题",
            "把真正弄懂的题标记为已掌握，它们会出现在这里",
        )
        StatusFilter.ALL -> Triple(
            "📷",
            "错题本还是空的",
            "拍一张作业或试卷，自动框出每道题，选择要收录的那一题",
        )
    }
}
