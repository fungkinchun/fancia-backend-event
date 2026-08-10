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
        recurrence.pausedUntil?.let {
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

    /**
     * Upcoming browse: one-time events that have not started, or recurring series
     * that still have a future occurrence (including between slots).
     */
    fun isListable(event: Event, now: LocalDateTime): Boolean {
        if (isPauseActive(event, now)) return false
        val anchorStart = event.startTime ?: return false
        return when (event.recurrenceFrequency) {
            RecurrenceFrequency.NONE -> !anchorStart.isBefore(now)
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKLY -> RecurrenceDaysMask(event.recurrenceDaysMask).isNotEmpty()
            RecurrenceFrequency.MONTHLY -> true
        }
    }

    /**
     * Past browse: one-time events that have started, or recurring series that have
     * already had at least one occurrence (anchor start is before [now]).
     * A recurring event can be both past-listable and upcoming-listable.
     */
    fun isPastListable(event: Event, now: LocalDateTime): Boolean {
        val anchorStart = event.startTime ?: return false
        return anchorStart.isBefore(now)
    }

    fun nextOccurrenceStart(event: Event, now: LocalDateTime): LocalDateTime? {
        if (!isListable(event, now)) return null
        val anchorStart = event.startTime ?: return null
        val from = if (now.isBefore(anchorStart)) anchorStart else now
        return when (event.recurrenceFrequency) {
            RecurrenceFrequency.NONE -> anchorStart
            RecurrenceFrequency.DAILY -> nextDailyOccurrenceStart(anchorStart, from)
            RecurrenceFrequency.WEEKLY -> nextWeeklyOccurrenceStart(
                anchorStart,
                RecurrenceDaysMask(event.recurrenceDaysMask),
                from,
            )

            RecurrenceFrequency.MONTHLY -> nextMonthlyOccurrenceStart(anchorStart, from)
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

    /** True when today's slot at the anchor clock time is still upcoming. */
    internal fun isDailyListable(anchorStart: LocalDateTime, now: LocalDateTime): Boolean {
        val todayStart = now.toLocalDate().atTime(anchorStart.toLocalTime())
        return !todayStart.isBefore(now)
    }

    internal fun isWeeklyListable(
        anchorStart: LocalDateTime,
        daysMask: RecurrenceDaysMask,
        now: LocalDateTime,
    ): Boolean {
        return daysMask.isNotEmpty()
    }

    internal fun isMonthlyListable(anchorStart: LocalDateTime, now: LocalDateTime): Boolean {
        return true
    }

    internal fun resolveMonthlyDay(anchorDayOfMonth: Int, month: YearMonth): Int {
        return minOf(anchorDayOfMonth, month.lengthOfMonth())
    }

    private fun nextDailyOccurrenceStart(anchorStart: LocalDateTime, now: LocalDateTime): LocalDateTime {
        val candidate = now.toLocalDate().atTime(anchorStart.toLocalTime())
        return if (candidate.isBefore(now)) candidate.plusDays(1) else candidate
    }

    private fun nextWeeklyOccurrenceStart(
        anchorStart: LocalDateTime,
        daysMask: RecurrenceDaysMask,
        now: LocalDateTime,
    ): LocalDateTime? {
        val today = now.dayOfWeek
        val anchorTime = anchorStart.toLocalTime()
        if (daysMask.contains(today)) {
            val todayStart = now.toLocalDate().atTime(anchorTime)
            if (!todayStart.isBefore(now)) {
                return todayStart
            }
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
        return now.toLocalDate().plusDays(daysUntil.toLong()).atTime(anchorTime)
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
