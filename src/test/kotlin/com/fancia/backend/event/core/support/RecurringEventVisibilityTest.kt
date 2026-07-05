package com.fancia.backend.event.core.support

import com.fancia.backend.event.core.entity.Event
import com.fancia.backend.shared.event.core.dto.EventRecurrenceDto
import com.fancia.backend.shared.event.core.enums.RecurrenceFrequency
import com.fancia.backend.shared.event.core.exception.RecurrenceDaysOfWeekNotSupportedException
import com.fancia.backend.shared.event.core.exception.WeeklyRecurrenceRequiresDaysOfWeekException
import com.fancia.backend.shared.event.core.model.RecurrenceDaysMask
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.YearMonth

class RecurringEventVisibilityTest : FunSpec({
    test("RecurrenceDaysMask encodes Sunday as SMTWTFS 1000000") {
        val mask = RecurrenceDaysMask.fromSmsString("1000000")

        mask.bits shouldBe RecurrenceDaysMask.SUNDAY
        mask.toDayOfWeekSet() shouldBe setOf(DayOfWeek.SUNDAY)
        mask.toSmsString() shouldBe "1000000"
    }

    test("RecurrenceDaysMask encodes Monday and Friday as 0100010") {
        RecurrenceDaysMask.fromDayOfWeekSet(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)).toSmsString() shouldBe "0100010"
    }

    test("validateRecurrence accepts NONE for one-time events") {
        RecurringEventVisibility.validateRecurrence(
            EventRecurrenceDto(frequency = RecurrenceFrequency.NONE),
        )
    }

    test("validateRecurrence rejects daysOfWeek for NONE") {
        shouldThrow<RecurrenceDaysOfWeekNotSupportedException> {
            RecurringEventVisibility.validateRecurrence(
                EventRecurrenceDto(
                    frequency = RecurrenceFrequency.NONE,
                    daysOfWeek = setOf(DayOfWeek.MONDAY),
                ),
            )
        }
    }

    test("validateRecurrence rejects weekly recurrence without daysOfWeek") {
        shouldThrow<WeeklyRecurrenceRequiresDaysOfWeekException> {
            RecurringEventVisibility.validateRecurrence(
                EventRecurrenceDto(frequency = RecurrenceFrequency.WEEKLY),
            )
        }
    }

    test("validateRecurrence rejects daysOfWeek for daily recurrence") {
        shouldThrow<RecurrenceDaysOfWeekNotSupportedException> {
            RecurringEventVisibility.validateRecurrence(
                EventRecurrenceDto(
                    frequency = RecurrenceFrequency.DAILY,
                    daysOfWeek = setOf(DayOfWeek.MONDAY),
                ),
            )
        }
    }

    test("daily event is listable before today's start time and filtered after") {
        val anchorStart = LocalDateTime.of(2020, 1, 1, 10, 0)

        RecurringEventVisibility.isDailyListable(anchorStart, LocalDateTime.of(2030, 5, 25, 9, 0)) shouldBe true
        RecurringEventVisibility.isDailyListable(anchorStart, LocalDateTime.of(2030, 5, 25, 10, 0)) shouldBe true
        RecurringEventVisibility.isDailyListable(anchorStart, LocalDateTime.of(2030, 5, 25, 10, 1)) shouldBe false
    }

    test("weekly event stays listable after this week's recurrence day has passed") {
        val anchorStart = LocalDateTime.of(2030, 6, 3, 10, 0) // Monday
        val mondayOnly = RecurrenceDaysMask.fromDayOfWeekSet(setOf(DayOfWeek.MONDAY))

        RecurringEventVisibility.isWeeklyListable(
            anchorStart,
            mondayOnly,
            LocalDateTime.of(2030, 6, 3, 9, 0),
        ) shouldBe true
        RecurringEventVisibility.isWeeklyListable(
            anchorStart,
            mondayOnly,
            LocalDateTime.of(2030, 6, 5, 9, 0),
        ) shouldBe true
    }

    test("weekly event stays listable when another recurrence day is still upcoming this week") {
        val anchorStart = LocalDateTime.of(2030, 6, 3, 10, 0)
        val mondayAndFriday = RecurrenceDaysMask.fromDayOfWeekSet(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))

        RecurringEventVisibility.isWeeklyListable(
            anchorStart,
            mondayAndFriday,
            LocalDateTime.of(2030, 6, 5, 12, 0),
        ) shouldBe true
    }

    test("monthly event stays listable after this month's occurrence day has passed") {
        val anchorStart = LocalDateTime.of(2030, 1, 1, 18, 0)

        RecurringEventVisibility.isMonthlyListable(anchorStart, LocalDateTime.of(2030, 5, 1, 9, 0)) shouldBe true
        RecurringEventVisibility.isMonthlyListable(anchorStart, LocalDateTime.of(2030, 5, 25, 9, 0)) shouldBe true
    }

    test("monthly event clamps anchor day to last valid day of month") {
        RecurringEventVisibility.resolveMonthlyDay(31, YearMonth.of(2030, 2)) shouldBe 28
        RecurringEventVisibility.resolveMonthlyDay(31, YearMonth.of(2028, 2)) shouldBe 29
    }

    test("one-time event ignores recurrence pause timestamp") {
        val anchorStart = LocalDateTime.of(2030, 6, 10, 10, 0)
        val event = Event().apply {
            startTime = anchorStart
            recurrenceFrequency = RecurrenceFrequency.NONE
            recurrencePausedUntil = LocalDateTime.of(2030, 6, 10, 0, 0)
        }
        val now = LocalDateTime.of(2030, 6, 5, 9, 0)

        RecurringEventVisibility.isListable(event, now) shouldBe true
    }

    test("recurring event is not listable while pause is active") {
        val anchorStart = LocalDateTime.of(2030, 6, 3, 10, 0)
        val event = Event().apply {
            startTime = anchorStart
            recurrenceFrequency = RecurrenceFrequency.WEEKLY
            recurrenceDaysMask = RecurrenceDaysMask.fromDayOfWeekSet(setOf(DayOfWeek.MONDAY)).bits
            recurrencePausedUntil = LocalDateTime.of(2030, 6, 10, 0, 0)
        }
        val now = LocalDateTime.of(2030, 6, 5, 9, 0)

        RecurringEventVisibility.isListable(event, now) shouldBe false
        RecurringEventVisibility.nextOccurrenceStart(event, now) shouldBe null
    }

    test("recurring event is listable again after pause ends") {
        val anchorStart = LocalDateTime.of(2030, 6, 3, 10, 0)
        val event = Event().apply {
            startTime = anchorStart
            recurrenceFrequency = RecurrenceFrequency.WEEKLY
            recurrenceDaysMask = RecurrenceDaysMask.fromDayOfWeekSet(setOf(DayOfWeek.MONDAY)).bits
            recurrencePausedUntil = LocalDateTime.of(2030, 6, 10, 0, 0)
        }
        val now = LocalDateTime.of(2030, 6, 10, 9, 0)

        RecurringEventVisibility.isListable(event, now) shouldBe true
    }
})
