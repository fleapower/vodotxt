package com.vodotxt.domain

import java.time.LocalDate

data class TodoFilter(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val dateFilter: DateFilterType = DateFilterType.ALL,
    val customDate: LocalDate? = null,
    val selectedProjects: List<String> = emptyList(),
    val selectedContexts: List<String> = emptyList(),
    val selectedPriorities: List<Char> = emptyList(),
    val completionFilter: CompletionFilter? = CompletionFilter.ALL,
    val invertProjects: Boolean = false,
    val invertContexts: Boolean = false,
    val sortMode: SortMode = SortMode.FILE,
)

enum class SortMode { FILE, DUE_DATE, PRIORITY }

enum class DateFilterType { ALL, TODAY, TOMORROW, CUSTOM }

enum class CompletionFilter { ALL, INCOMPLETE, COMPLETED }
