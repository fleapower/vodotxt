package com.vodotxt.data

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object RecurrenceCalculator {

    fun calculateNextDate(recurrence: String, completionDate: LocalDate): LocalDate {
        if (recurrence.length < 3) return completionDate

        return when {
            // r:##x (e.g., r:01d, r:02w)
            recurrence[2].isLetter() -> {
                val amount = recurrence.substring(0, 2).toLongOrNull() ?: 1L
                val unit = recurrence[2].lowercaseChar()
                when (unit) {
                    'd' -> completionDate.plusDays(amount)
                    'w' -> completionDate.plusWeeks(amount)
                    'm' -> completionDate.plusMonths(amount)
                    'y' -> completionDate.plusYears(amount)
                    else -> completionDate
                }
            }

            // r:x## (e.g., r:w03, r:m10)
            recurrence[0].isLetter() && recurrence.length == 3 -> {
                val type = recurrence[0].lowercaseChar()
                val value = recurrence.substring(1, 3).toIntOrNull() ?: 1
                when (type) {
                    'w' -> {
                        // User: 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
                        // java.time DayOfWeek: 1=Mon, 7=Sun
                        val targetJodaDay = when (value) {
                            1 -> 7 // Sunday
                            2 -> 1 // Monday
                            3 -> 2 // Tuesday
                            4 -> 3 // Wednesday
                            5 -> 4 // Thursday
                            6 -> 5 // Friday
                            7 -> 6 // Saturday
                            else -> 7
                        }
                        completionDate.with(TemporalAdjusters.next(java.time.DayOfWeek.of(targetJodaDay)))
                    }
                    'm' -> {
                        val lastDayThisMonth = completionDate.with(TemporalAdjusters.lastDayOfMonth()).dayOfMonth
                        val dateThisMonth = completionDate.withDayOfMonth(value.coerceAtMost(lastDayThisMonth))
                        if (dateThisMonth.isAfter(completionDate)) {
                            dateThisMonth
                        } else {
                            val nextMonth = completionDate.plusMonths(1)
                            val lastDayNextMonth = nextMonth.with(TemporalAdjusters.lastDayOfMonth()).dayOfMonth
                            nextMonth.withDayOfMonth(value.coerceAtMost(lastDayNextMonth))
                        }
                    }
                    else -> completionDate
                }
            }

            // r:yMMDD (e.g., r:y0304)
            recurrence.startsWith("y") && recurrence.length == 5 -> {
                val month = recurrence.substring(1, 3).toIntOrNull() ?: 1
                val day = recurrence.substring(3, 5).toIntOrNull() ?: 1
                
                val currentYearDate = try {
                    val lastDay = completionDate.withYear(completionDate.year).withMonth(month).with(TemporalAdjusters.lastDayOfMonth()).dayOfMonth
                    completionDate.withYear(completionDate.year).withMonth(month).withDayOfMonth(day.coerceAtMost(lastDay))
                } catch (_: Exception) {
                    null
                }

                if (currentYearDate != null && currentYearDate.isAfter(completionDate)) {
                    currentYearDate
                } else {
                    val nextYear = completionDate.year + 1
                    val lastDay = completionDate.withYear(nextYear).withMonth(month).with(TemporalAdjusters.lastDayOfMonth()).dayOfMonth
                    completionDate.withYear(nextYear).withMonth(month).withDayOfMonth(day.coerceAtMost(lastDay))
                }
            }

            else -> completionDate
        }
    }
}
