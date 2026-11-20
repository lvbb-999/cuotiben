package com.cuotiben.notebook.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil.compose.AsyncImage
import com.cuotiben.notebook.MistakeBookApp
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.tagList
import com.cuotiben.notebook.ui.Routes
import com.cuotiben.notebook.ui.components.AppBottomBar
import com.cuotiben.notebook.ui.components.SoftCard
import com.cuotiben.notebook.ui.components.StatusPill
import com.cuotiben.notebook.ui.components.TagPill
import java.io.File

@Composable
fun ReviewScreen(
    onOpenDetail: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    onGoHome: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as MistakeBookApp
    val viewModel: ReviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ReviewViewModel(app.repository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppBottomBar(currentRoute = Routes.REVIEW, onNavigate = onNavigate) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "复习",
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = if (state.queue.isEmpty()) {
                    "按遗忘曲线复习，把错题真正变成会的"
                } else {
                    "今日待复习 ${state.queue.size} 道 · 已复习 ${state.reviewedCount} 道"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            when {
                state.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.finished || state.current == null -> {
                    ReviewFinished(
                        reviewedCount = state.reviewedCount,
                        hadQueue = state.queue.isNotEmpty(),
                        onGoHome = onGoHome,
                    )
                }

                else -> {
                    val current = state.current
                    if (current != null) {
                        Text(
                            text = "第 ${state.index + 1} / ${state.queue.size} 题",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (state.queue.isEmpty()) 0f
                                else state.index.toFloat() / state.queue.size
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        ReviewCard(
                            mistake = current,
                            revealed = state.revealed,
                            saving = state.saving,
                            onReveal = viewModel::reveal,
                            onKnown = { viewModel.mark(true) },
                            onUnknown = { viewModel.mark(false) },
                            onOpenDetail = { onOpenDetail(current.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ReviewCard(
    mistake: Mistake,
    revealed: Boolean,
    saving: Boolean,
    onReveal: () -> Unit,
    onKnown: () -> Unit,
    onUnknown: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagPill(
                text = mistake.subject,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (mistake.questionNo.isNotBlank()) {
                Spacer(Modifier.size(6.dp))
                Text(
                    text = mistake.questionNo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            StatusPill(done = false)
        }

        Spacer(Modifier.height(10.dp))
        val image = mistake.imagePath.takeIf { it.isNotBlank() }
            ?.let { path -> File(path).takeIf { file -> file.exists() } }
        if (image != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 130.dp, max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = image,
                    contentDescription = "复习题目",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (mistake.text.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = mistake.text,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (mistake.title.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = mistake.title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (mistake.tagList.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                mistake.tagList.take(3).forEach { TagPill("#$it") }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (!revealed) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "答案与解析已隐藏",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "先在心里回忆一遍，再点开对照。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onReveal,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("显示答案与解析")
            }
        } else {
            if (mistake.answer.isNotBlank()) {
                Text(
                    text = "我的答案",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = mistake.answer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
            }
            if (mistake.analysis.isNotBlank()) {
                Text(
                    text = "解析 / 正确解法",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = mistake.analysis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
            }
            if (mistake.answer.isBlank() && mistake.analysis.isBlank()) {
                Text(
                    text = "还没有填写答案和解析，可以点「查看详情并补充」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onUnknown,
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("还不熟")
                }
                Button(
                    onClick = onKnown,
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("掌握了")
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onOpenDetail,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("查看详情并补充")
            }
        }
    }
}

@Composable
private fun ReviewFinished(
    reviewedCount: Int,
    hadQueue: Boolean,
    onGoHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🎉", fontSize = 56.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (reviewedCount > 0) "本轮复习完成" else "今天没有待复习的错题",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                reviewedCount > 0 -> "刚刚复习了 $reviewedCount 道。\"还不熟\"的题会按 1、2、4、7、15 天的节奏再次出现。"
                hadQueue -> "剩下的题已经排进之后的复习计划。"
                else -> "新收录的错题会自动出现在这里，按遗忘曲线提醒你复习。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGoHome,
            shape = RoundedCornerShape(50),
        ) {
            Icon(
                imageVector = Icons.Rounded.School,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("回到错题本", fontWeight = FontWeight.SemiBold)
        }
    }
}
