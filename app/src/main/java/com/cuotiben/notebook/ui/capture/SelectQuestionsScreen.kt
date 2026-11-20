package com.cuotiben.notebook.ui.capture

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cuotiben.notebook.MistakeBookApp
import com.cuotiben.notebook.data.Subjects
import com.cuotiben.notebook.ui.components.SubjectChip
import com.cuotiben.notebook.util.DetectedRegion
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectQuestionsScreen(
    fileName: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MistakeBookApp
    val viewModel: SelectQuestionsViewModel = viewModel(
        key = "select-$fileName",
        factory = viewModelFactory {
            initializer {
                SelectQuestionsViewModel(context.applicationContext, app.repository, fileName)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val prefs = remember {
        context.getSharedPreferences("cuotiben_settings", Context.MODE_PRIVATE)
    }
    var subject by rememberSaveable {
        mutableStateOf(
            prefs.getString("last_subject", Subjects.UNCATEGORIZED) ?: Subjects.UNCATEGORIZED,
        )
    }
    var showSubjectDialog by rememberSaveable { mutableStateOf(false) }
    var manualRect by remember {
        mutableStateOf(RectF(0.06f, 0.28f, 0.94f, 0.64f))
    }

    fun saveSelection() {
        viewModel.save(subject) { count ->
            if (count > 0) {
                prefs.edit().putString("last_subject", subject).apply()
                Toast.makeText(context, "已收录 $count 道错题", Toast.LENGTH_SHORT).show()
                onDone()
            } else {
                Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("框选题目") },
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
            if (!state.loading && state.image != null && state.error == null) {
                if (state.manualMode) {
                    ManualBottomBar(
                        onCancel = { viewModel.setManualMode(false) },
                        onConfirm = { viewModel.addManualRegion(manualRect) },
                    )
                } else {
                    SelectionBottomBar(
                        selectedCount = state.selected.size,
                        onSelectAll = viewModel::selectAll,
                        onClear = viewModel::clearSelection,
                        onSave = { showSubjectDialog = true },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val image = state.image
            when {
                state.error != null -> ErrorContent(
                    message = state.error ?: "图片加载失败",
                    onBack = onBack,
                )

                state.loading || image == null -> LoadingContent()

                else -> Column(Modifier.fillMaxSize()) {
                    InstructionRow(
                        regionCount = state.regions.size,
                        manualMode = state.manualMode,
                        detecting = state.detecting,
                        ignoreHandwriting = state.ignoreHandwriting,
                        handwritingLines = state.handwritingLines,
                        onToggleHandwriting = {
                            viewModel.setIgnoreHandwriting(!state.ignoreHandwriting)
                        },
                        onManual = { viewModel.setManualMode(true) },
                    )
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val ratio = state.imageWidth.toFloat() /
                            state.imageHeight.coerceAtLeast(1).toFloat()
                        val displayWidth = minOf(maxWidth, maxHeight * ratio)
                        val displayHeight = displayWidth / ratio
                        Box(
                            modifier = Modifier
                                .width(displayWidth)
                                .height(displayHeight)
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(18.dp),
                                ),
                        ) {
                            Image(
                                bitmap = image,
                                contentDescription = "待框选的题目照片",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (state.manualMode) {
                                ManualCropOverlay(
                                    rect = manualRect,
                                    onRectChange = { manualRect = it },
                                )
                            } else {
                                RegionOverlay(
                                    regions = state.regions,
                                    selected = state.selected,
                                    onToggle = viewModel::toggleRegion,
                                )
                            }
                        }
                    }
                    if (state.manualMode) {
                        Text(
                            text = "拖动四角或边框调整范围，覆盖完整题目即可",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            text = "提示：自动框选不准时，可用「手动框选」自己圈出题目",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (state.saving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "正在收录错题…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    if (showSubjectDialog) {
        AlertDialog(
            onDismissRequest = { showSubjectDialog = false },
            title = { Text("收录 ${state.selected.size} 道错题") },
            text = {
                Column {
                    Text(
                        text = "选择科目，保存后也能在详情页修改。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(Subjects.options) { option ->
                            SubjectChip(
                                label = option,
                                selected = option == subject,
                                onClick = { subject = option },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSubjectDialog = false
                        saveSelection()
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubjectDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun InstructionRow(
    regionCount: Int,
    manualMode: Boolean,
    detecting: Boolean,
    ignoreHandwriting: Boolean,
    handwritingLines: Int,
    onToggleHandwriting: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp, top = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (regionCount == 1) "已框出 1 道题" else "已框出 $regionCount 道题",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (manualMode) {
                        "自己框选：拖动边框，圈住要收录的题目"
                    } else {
                        "点选要收录的题目，可多选"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!manualMode) {
                TextButton(onClick = onManual) {
                    Icon(
                        imageVector = Icons.Rounded.Crop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("手动框选")
                }
            }
        }
        if (!manualMode) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChip(
                    selected = ignoreHandwriting,
                    onClick = onToggleHandwriting,
                    enabled = !detecting,
                    shape = RoundedCornerShape(50),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = {
                        Text(
                            text = if (handwritingLines > 0) {
                                "忽略手写 · 已过滤 $handwritingLines 行"
                            } else {
                                "忽略手写"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
                if (detecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "重新识别中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(18.dp))
        Text(
            text = "正在识别题目…",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "本地识别，照片不会上传",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "😕", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack, shape = RoundedCornerShape(50)) {
            Text("返回重拍")
        }
    }
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已选 $selectedCount 道",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSelectAll) { Text("全选") }
                TextButton(onClick = onClear) { Text("清空") }
            }
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = onSave,
                enabled = selectedCount > 0,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = if (selectedCount > 0) {
                        "收录所选 $selectedCount 道题"
                    } else {
                        "请点选要收录的题目"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ManualBottomBar(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
            ) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(2f)
                    .height(50.dp),
            ) {
                Text("加入这道题")
            }
        }
    }
}

@Composable
private fun RegionOverlay(
    regions: List<DetectedRegion>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.WHITE
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(regions) {
                detectTapGestures { position ->
                    val nx = position.x / size.width
                    val ny = position.y / size.height
                    val index = regions.indexOfLast { region ->
                        region.rect.contains(nx, ny)
                    }
                    if (index >= 0) onToggle(index)
                }
            },
    ) {
        labelPaint.textSize = 11.sp.toPx()
        val chipPadding = 7.dp.toPx()
        val chipHeight = 20.dp.toPx()

        regions.forEachIndexed { index, region ->
            val rect = region.rect
            val left = rect.left * size.width
            val top = rect.top * size.height
            val right = rect.right * size.width
            val bottom = rect.bottom * size.height
            val isSelected = index in selected

            if (isSelected) {
                drawRect(
                    color = primary.copy(alpha = 0.16f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                )
            } else {
                drawRect(
                    color = Color.Black.copy(alpha = 0.08f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                )
            }

            drawRect(
                color = if (isSelected) primary else Color.White.copy(alpha = 0.9f),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(
                    width = if (isSelected) 2.5.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )

            val label = region.label
            val textWidth = labelPaint.measureText(label)
            val chipWidth = textWidth + chipPadding * 2
            val chipX = (left + 7.dp.toPx()).coerceAtMost(right - chipWidth - 4.dp.toPx())
            val chipY = (top + 6.dp.toPx()).coerceAtMost(bottom - chipHeight - 4.dp.toPx())
            drawRoundRect(
                color = if (isSelected) primary else Color.Black.copy(alpha = 0.6f),
                topLeft = Offset(chipX, chipY),
                size = Size(chipWidth, chipHeight),
                cornerRadius = CornerRadius(chipHeight / 2),
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                chipX + chipPadding,
                chipY + chipHeight * 0.72f,
                labelPaint,
            )
        }
    }
}

@Composable
private fun ManualCropOverlay(
    rect: RectF,
    onRectChange: (RectF) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val latestRect = rememberUpdatedState(rect)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var mode = 0
                var start = RectF()
                var totalDx = 0f
                var totalDy = 0f
                val threshold = 42.dp.toPx()

                fun distanceTo(px: Float, py: Float, x: Float, y: Float): Float =
                    hypot(px - x, py - y)

                detectDragGestures(
                    onDragStart = { position ->
                        val r = latestRect.value
                        val l = r.left * size.width
                        val t = r.top * size.height
                        val rr = r.right * size.width
                        val b = r.bottom * size.height
                        start = RectF(r)
                        totalDx = 0f
                        totalDy = 0f
                        mode = when {
                            distanceTo(position.x, position.y, l, t) <= threshold -> 1
                            distanceTo(position.x, position.y, rr, t) <= threshold -> 2
                            distanceTo(position.x, position.y, l, b) <= threshold -> 3
                            distanceTo(position.x, position.y, rr, b) <= threshold -> 4
                            position.x in l..rr && position.y in t..b -> 5
                            else -> 0
                        }
                    },
                    onDragEnd = { mode = 0 },
                    onDragCancel = { mode = 0 },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (mode == 0) return@detectDragGestures
                        totalDx += dragAmount.x / size.width
                        totalDy += dragAmount.y / size.height
                        val base = start
                        var left = base.left
                        var top = base.top
                        var right = base.right
                        var bottom = base.bottom
                        val minWidth = 0.12f
                        val minHeight = 0.06f
                        when (mode) {
                            1 -> {
                                left = (base.left + totalDx).coerceIn(0f, right - minWidth)
                                top = (base.top + totalDy).coerceIn(0f, bottom - minHeight)
                            }
                            2 -> {
                                right = (base.right + totalDx)
                                    .coerceIn(left + minWidth, 1f)
                                top = (base.top + totalDy).coerceIn(0f, bottom - minHeight)
                            }
                            3 -> {
                                left = (base.left + totalDx).coerceIn(0f, right - minWidth)
                                bottom = (base.bottom + totalDy)
                                    .coerceIn(top + minHeight, 1f)
                            }
                            4 -> {
                                right = (base.right + totalDx)
                                    .coerceIn(left + minWidth, 1f)
                                bottom = (base.bottom + totalDy)
                                    .coerceIn(top + minHeight, 1f)
                            }
                            5 -> {
                                val width = base.width()
                                val height = base.height()
                                left = (base.left + totalDx).coerceIn(0f, 1f - width)
                                top = (base.top + totalDy).coerceIn(0f, 1f - height)
                                right = left + width
                                bottom = top + height
                            }
                        }
                        onRectChange(RectF(left, top, right, bottom))
                    },
                )
            },
    ) {
        val left = rect.left * size.width
        val top = rect.top * size.height
        val right = rect.right * size.width
        val bottom = rect.bottom * size.height
        val scrim = Color.Black.copy(alpha = 0.46f)

        drawRect(scrim, Offset(0f, 0f), Size(size.width, top))
        drawRect(scrim, Offset(0f, bottom), Size(size.width, size.height - bottom))
        drawRect(scrim, Offset(0f, top), Size(left, bottom - top))
        drawRect(scrim, Offset(right, top), Size(size.width - right, bottom - top))

        drawRect(
            color = primary,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 2.5.dp.toPx()),
        )

        val thirdW = (right - left) / 3f
        val thirdH = (bottom - top) / 3f
        for (i in 1..2) {
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(left + thirdW * i, top),
                end = Offset(left + thirdW * i, bottom),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(left, top + thirdH * i),
                end = Offset(right, top + thirdH * i),
                strokeWidth = 1.dp.toPx(),
            )
        }

        listOf(
            Offset(left, top),
            Offset(right, top),
            Offset(left, bottom),
            Offset(right, bottom),
        ).forEach { center ->
            drawCircle(color = Color.White, radius = 9.dp.toPx(), center = center)
            drawCircle(
                color = primary,
                radius = 9.dp.toPx(),
                center = center,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
    }
}
