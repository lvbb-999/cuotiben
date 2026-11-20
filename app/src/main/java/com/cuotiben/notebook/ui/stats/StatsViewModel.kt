package com.cuotiben.notebook.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuotiben.notebook.data.Mistake
import com.cuotiben.notebook.data.MistakeRepository
import com.cuotiben.notebook.data.Subjects
import com.cuotiben.notebook.data.isDone
import com.cuotiben.notebook.util.DateFormats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class DayCount(val label: String, val count: Int)

data class StatsUiState(
    val loading: Boolean = true,
    val total: Int = 0,
    val todo: Int = 0,
    val done: Int = 0,
    val weekAdded: Int = 0,
    val totalReviews: Int = 0,
    val subjects: List<Pair<String, Int>> = emptyList(),
    val reasons: List<Pair<String, Int>> = emptyList(),
    val last7Days: List<DayCount> = emptyList(),
) {
    val completionRate: Float
        get() = if (total == 0) 0f else done.toFloat() / total
}

class StatsViewModel(repository: MistakeRepository) : ViewModel() {

    val uiState = repository.observeAll()
        .map { compute(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )

    private fun compute(items: List<Mistake>): StatsUiState {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val weekStart = today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        val last7 = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            DayCount(
                label = if (offset == 0) "今天" else DateFormats.weekday(start),
                count = items.count { it.createdAt in start until end },
            )
        }

        val subjects = items
            .groupingBy { it.subject.ifBlank { Subjects.UNCATEGORIZED } }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

        val reasons = items
            .mapNotNull { item -> item.reason.takeIf { it.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

        return StatsUiState(
            loading = false,
            total = items.size,
            todo = items.count { !it.isDone },
            done = items.count { it.isDone },
            weekAdded = items.count { it.createdAt >= weekStart },
            totalReviews = items.sumOf { it.reviewCount },
            subjects = subjects,
            reasons = reasons,
            last7Days = last7,
        )
    }
}
