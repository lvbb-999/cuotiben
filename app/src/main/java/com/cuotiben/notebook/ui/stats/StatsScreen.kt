package com.cuotiben.notebook.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cuotiben.notebook.MistakeBookApp
import com.cuotiben.notebook.ui.Routes
import com.cuotiben.notebook.ui.components.AppBottomBar
import com.cuotiben.notebook.ui.components.EmptyState
import com.cuotiben.notebook.ui.components.SectionTitle
import com.cuotiben.notebook.ui.components.SoftCard
import com.cuotiben.notebook.ui.components.TagPill
import kotlin.math.roundToInt

@Composable
fun StatsScreen(onNavigate: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as MistakeBookApp
    val viewModel: StatsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StatsViewModel(app.repository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppBottomBar(currentRoute = Routes.STATS, onNavigate = onNavigate) },
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
                text = "统计",
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = "看见自己的进步，比刷更多题更重要",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            if (state.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.total == 0) {
                EmptyState(
                    emoji = "📊",
                    title = "还没有可统计的数据",
                    subtitle = "收录第一道错题后，这里会显示你的学习轨迹",
                    actionLabel = "去收录错题",
                    onAction = { onNavigate(Routes.HOME) },
                )
            } else {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompletionRing(state.completionRate)
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            StatLine("共收录", "${state.total} 道")
                            StatLine("待攻克", "${state.todo} 道", MaterialTheme.colorScheme.secondary)
                            StatLine("已掌握", "${state.done} 道", MaterialTheme.colorScheme.primary)
                            StatLine("本周新增", "${state.weekAdded} 道", MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "累计复习 ${state.totalReviews} 次 · 完成率 ${(state.completionRate * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(14.dp))
                SoftCard {
                    SectionTitle("各科错题")
                    Spacer(Modifier.height(12.dp))
                    val maxCount = state.subjects.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                    state.subjects.forEach { (subject, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = subject,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(62.dp),
                                maxLines = 1,
                            )
                            LinearProgressIndicator(
                                progress = { count.toFloat() / maxCount },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(26.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SoftCard {
                    SectionTitle("近 7 天收录")
                    Spacer(Modifier.height(14.dp))
                    val maxDay = state.last7Days.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        state.last7Days.forEach { day ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = day.count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .height((8 + 64f * day.count / maxDay).dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (day.count > 0) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        ),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = day.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SoftCard {
                    SectionTitle("错因分布")
                    Spacer(Modifier.height(10.dp))
                    if (state.reasons.isEmpty()) {
                        Text(
                            text = "还没有记录错因。整理时标一下，更容易发现自己的薄弱点。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.reasons.forEach { (reason, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TagPill(
                                    text = reason,
                                    container = MaterialTheme.colorScheme.secondaryContainer,
                                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "$count 道",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CompletionRing(rate: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier.width(112.dp).height(112.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = primary,
                startAngle = -90f,
                sweepAngle = 360f * rate.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(rate * 100).roundToInt()}%",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "完成率",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
        )
    }
}
