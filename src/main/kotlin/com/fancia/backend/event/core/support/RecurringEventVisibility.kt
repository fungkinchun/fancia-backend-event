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
        recurrence.pausedUntil?.let { pausedUntil ->
            if (recurrence.frequency == RecurrenceFrequency.NONE) {
                throw IllegalArgumentException("pausedUntil is only valid for recurring events")
            }
        }
    }

    fun validatePause(event: Event, pausedUntil: LocalDateTime?) {
        if (pausedUntil == null) return
        if (event.recurrenceFrequency == RecurrenceFrequency.NONE) {
            throw IllegalArgumentException("Cannot pause a one-time event")
        }
    }

    fun isListable(event: Event, now: LocalDateTime): Boolean {
        if (isPauseActive(event, now)) return false
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

    private fun isPauseActive(event: Event, now: LocalDateTime): Boolean {
        if (event.recurrenceFrequency == RecurrenceFrequency.NONE) return false
        val pausedUntil = event.recurrencePausedUntil ?: return false
        return now.isBefore(pausedUntil)
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
        if (daysMask.toDayOfWeekSet().any { it.value > today.value }) {
            return true
        }
        return true
    }

    internal fun isMonthlyListable(anchorStart: LocalDateTime, now: LocalDateTime): Boolean {
        val month = YearMonth.from(now)
        val occurrenceDay = resolveMonthlyDay(anchorStart.dayOfMonth, month)
        val todayDay = now.dayOfMonth
        if (todayDay == occurrenceDay) {
            return isDailyListable(anchorStart, now)
        }
        if (occurrenceDay > todayDay) {
            return true
        }
        // Occurrence day already passed this month — still repeats next month.
        return true
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
            daysMask.toDayOfWeekSet().filter { it.value > today.value }.minByOrNull { it.value }
                ?: daysMask.toDayOfWeekSet().minByOrNull { it.value }
                ?: return null
        val daysUntil =
            if (nextDay.value > today.value) {
                nextDay.value - today.value
            } else {
                7 - today.value + nextDay.value
            }
        return now.toLocalDate().plusDays(daysUntil.toLong()).atTime(anchorStart.toLocalTime())
    }

    private fun nextMonthlyOccurrenceStart(anchorStart: LocalDateTime, now: LocalDateTime): LocalDateTime {
        val month = YearMonth.from(now)
        val day = resolveMonthlyDay(anchorStart.dayOfMonth, month)
        val candidate = LocalDate.of(month.year, month.month, day).atTime(anchorStart.toLocalTime())
        if (!candidate.isBefore(now)) {
            return candidate
        }
        val nextMonth = month.plusMonths(1)
        val nextDay = resolveMonthlyDay(anchorStart.dayOfMonth, nextMonth)
        return LocalDate.of(nextMonth.year, nextMonth.month, nextDay).atTime(anchorStart.toLocalTime())
    }
}
