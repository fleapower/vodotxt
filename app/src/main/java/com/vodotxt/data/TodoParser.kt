package com.vodotxt.data

import com.vodotxt.domain.TodoItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

object TodoParser {
    private val dateRegex = Regex("""^(\d{4}-\d{2}-\d{2})(?:\s|$)""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun parse(line: String): TodoItem {
        var remaining = line.trim()
        var completed = false
        var priority: Char? = null
        var completionDate: LocalDate? = null
        var creationDate: LocalDate? = null

        // 1. Check completion
        if (remaining.startsWith("x ")) {
            completed = true
            remaining = remaining.substring(2).trim()

            // If completed, the first date is completion date
            dateRegex.find(remaining)?.let {
                completionDate = safeParseDate(it.groupValues[1])
                remaining = remaining.substring(it.range.last + 1).trim()
            }
        }

        // 2. Check priority
        if (remaining.startsWith("(") && remaining.length >= 3 && remaining[2] == ')' && remaining[1].isUpperCase()) {
            priority = remaining[1]
            remaining = remaining.substring(3).trim()
        }

        // 3. Check dates (Creation date)
        dateRegex.find(remaining)?.let {
            creationDate = safeParseDate(it.groupValues[1])
            remaining = remaining.substring(it.range.last + 1).trim()
        }

        // 4. Word-by-word extraction for tags
        // This is much safer for emails (user@gmail.com) than regex
        val words = remaining.split(WHITESPACE_REGEX)
        val projects = mutableListOf<String>()
        val contexts = mutableListOf<String>()
        val metadata = mutableMapOf<String, String>()
        val descriptionWords = mutableListOf<String>()

        for (word in words) {
            when {
                word.startsWith("@") && word.length > 1 -> {
                    contexts.add(word.substring(1))
                }
                word.startsWith("+") && word.length > 1 -> {
                    projects.add(word.substring(1))
                }
                // Specifically check for standard metadata tags to avoid stripping URLs
                word.startsWith("due:") && word.length > 4 -> {
                    metadata["due"] = word.substring(4)
                }
                word.startsWith("last:") && word.length > 5 -> {
                    metadata["last"] = word.substring(5)
                }
                word.startsWith("r:") && word.length > 2 -> {
                    metadata["r"] = word.substring(2)
                }
                else -> {
                    descriptionWords.add(word)
                }
            }
        }

        val cleanDescription = descriptionWords.joinToString(" ")

        return TodoItem(
            completed = completed,
            priority = priority,
            completionDate = completionDate,
            creationDate = creationDate,
            description = cleanDescription,
            projects = projects.distinct(),
            contexts = contexts.distinct(),
            metadata = metadata,
            rawLine = line
        )
    }

    fun toLine(item: TodoItem): String {
        return buildString {
            if (item.completed) {
                append("x ")
                if (item.completionDate != null) append("${item.completionDate} ")
            }
            if (item.priority != null) append("(${item.priority}) ")
            if (item.creationDate != null) append("${item.creationDate} ")
            append(item.description)
            
            item.projects.forEach { append(" +$it") }
            item.contexts.forEach { append(" @$it") }
            item.metadata.forEach { (key, value) -> append(" $key:$value") }
        }.trim().replace(WHITESPACE_REGEX, " ")
    }

    fun safeParseDate(dateStr: String?): LocalDate? {
        if (dateStr == null) return null
        return try {
            LocalDate.parse(dateStr)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun parseNaturalLanguageDate(input: String): LocalDate? {
        val today = LocalDate.now()
        val normalized = input.lowercase().trim()
        
        return when (normalized) {
            "today" -> today
            "tomorrow" -> today.plusDays(1)
            "monday" -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            "tuesday" -> today.with(TemporalAdjusters.next(DayOfWeek.TUESDAY))
            "wednesday" -> today.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY))
            "thursday" -> today.with(TemporalAdjusters.next(DayOfWeek.THURSDAY))
            "friday" -> today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
            "saturday" -> today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
            "sunday" -> today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
            else -> null
        }
    }
}
