package com.cuotiben.notebook.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {

    private val monthDay = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val yearMonthDay = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val hourMinute = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun date(ts: Long): String {
        val date = toDate(ts)
        val today = LocalDate.now()
        return when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date.year == today.year -> date.format(monthDay)
            else -> date.format(yearMonthDay)
        }
    }

    fun dateTime(ts: Long): String {
        val zoned = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
        val date = zoned.toLocalDate()
        val today = LocalDate.now()
        return when {
            date == today -> "今天 ${zoned.format(hourMinute)}"
            date == today.minusDays(1) -> "昨天 ${zoned.format(hourMinute)}"
            date.year == today.year -> "${date.format(monthDay)} ${zoned.format(hourMinute)}"
            else -> "${date.format(yearMonthDay)} ${zoned.format(hourMinute)}"
        }
    }

    fun fullDate(ts: Long): String = toDate(ts).format(yearMonthDay)

    fun weekday(ts: Long): String {
        return when (toDate(ts).dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }

    fun greeting(): String {
        val hour = java.time.LocalTime.now().hour
        return when {
            hour < 6 -> "夜深了"
            hour < 11 -> "早上好"
            hour < 14 -> "中午好"
            hour < 18 -> "下午好"
            else -> "晚上好"
        }
    }

    fun daysBetween(start: Long, end: Long = System.currentTimeMillis()): Long {
        return java.time.temporal.ChronoUnit.DAYS.between(toDate(start), toDate(end))
    }

    private fun toDate(ts: Long): LocalDate =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
}
