package com.fancia.backend.event.core.support

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.shared.event.core.dto.EventRecurrenceDto
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import com.fancia.backend.shared.event.core.exception.RecurrenceDaysOfWeekNotSupportedException
import com.fancia.backend.shared.event.core.exception.WeeklyRecurrenceRequiresDaysOfWeekException
import com.fancia.backend.shared.event.core.model.RecurrenceDaysMask
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

object RecurringEventVisibility {
    fun validateRecurrence(recurrence: EventRecurrenceDto) {
        val daysMask = RecurrenceDaysMask.fromDayOfWeekSet(recurrence.daysOfWeek)
        when (recurrence.frequency) {
            RecurrenceFrequency.NONE, RecurrenceFrequency.DAILY, RecurrenceFrequency.MONTHLY ->
                if (daysMask.isNotEmpty()) {
                    throw RecurrenceDaysOfWeekNotSupportedException()
                }

            RecurrenceFrequency.WEEKLY ->
                if (daysMask.isEmpty()) {
                    throw WeeklyRecurrenceRequiresDaysOfWeekException()
                }
        }
    }

    fun isListable(event: Event, now: LocalDateTime): Boolean {
        val anchorStart = event.startTime ?: return false
        return when (event.recurrenceFrequency) {
            RecurrenceFrequency.NONE -> !anchorStart.isBefore(now)
            RecurrenceFrequency.DAILY -> isDailyListable(anchorStart, now)
            RecurrenceFrequency.WEEKLY -> isWeeklyListable(
                anchorStart,
                RecurrenceDaysMask(event.recurrenceDaysMask),
                now
            )

            RecurrenceFrequency.MONTHLY -> isMonthlyListable(anchorStart, now)
        }
    }

    fun nextOccurrenceStart(event: Event, now: LocalDateTime): LocalDateTime? {
        if (!isListable(event, now)) return null
        val anchorStart = event.startTime ?: return null
        return when (event.recurrenceFrequency) {
            RecurrenceFrequency.NONE -> anchorStart
            RecurrenceFrequency.DAILY -> now.toLocalDate().atTime(anchorStart.toLocalTime())
            RecurrenceFrequency.WEEKLY -> nextWeeklyOccurrenceStart(
                anchorStart,
                RecurrenceDaysMask(event.recurrenceDaysMask),
                now
            )

            RecurrenceFrequency.MONTHLY -> nextMonthlyOccurrenceStart(anchorStart, now)
        }
    }

    fun nextOccurrenceEnd(event: Event, now: LocalDateTime): LocalDateTime? {
        val start = nextOccurrenceStart(event, now) ?: return null
        val anchorStart = event.startTime ?: return null
        val anchorEnd = event.endTime ?: return null
        return start.plus(Duration.between(anchorStart, anchorEnd))
    }

    internal fun isDailyListable(anchorStart: LocalDateTime, now: LocalDateTime): Boolean {
        val todayStart = now.toLocalDate().atTime(anchorStart.toLocalTime())
        return !todayStart.isBefore(now)
    }

    internal fun isWeeklyListable(
        anchorStart: LocalDateTime,
        daysMask: RecurrenceDaysMask,
        now: LocalDateTime,
    ): Boolean {
        if (daysMask.isEmpty()) return false
        val today = now.dayOfWeek
        if (daysMask.contains(today)) {
            return isDailyListable(anchorStart, now)
        }
        return daysMask.toDayOfWeekSet().any { it.value > today.value }
    }

    internal fun isMonthlyListable(anchorStart: LocalDateTime, now: LocalDateTime): Boolean {
        val month = YearMonth.from(now)
        val occurrenceDay = resolveMonthlyDay(anchorStart.dayOfMonth, month)
        val todayDay = now.dayOfMonth
        if (todayDay == occurrenceDay) {
            return isDailyListable(anchorStart, now)
        }
        return occurrenceDay > todayDay
    }

    internal fun resolveMonthlyDay(anchorDayOfMonth: Int, month: YearMonth): Int {
        return minOf(anchorDayOfMonth, month.lengthOfMonth())
    }

    private fun nextWeeklyOccurrenceStart(
        anchorStart: LocalDateTime,
        daysMask: RecurrenceDaysMask,
        now: LocalDateTime,
    ): LocalDateTime? {
        val today = now.dayOfWeek
        if (daysMask.contains(today)) {
            return now.toLocalDate().atTime(anchorStart.toLocalTime())
        }
        val nextDay =
            daysMask.toDayOfWeekSet().filter { it.value > today.value }.minByOrNull { it.value } ?: return null
        val daysUntil = nextDay.value - today.value
        return now.toLocalDate().plusDays(daysUntil.toLong()).atTime(anchorStart.toLocalTime())
    }

    private fun nextMonthlyOccurrenceStart(anchorStart: LocalDateTime, now: LocalDateTime): LocalDateTime {
        val month = YearMonth.from(now)
        val day = resolveMonthlyDay(anchorStart.dayOfMonth, month)
        return LocalDate.of(month.year, month.month, day).atTime(anchorStart.toLocalTime())
    }
}
