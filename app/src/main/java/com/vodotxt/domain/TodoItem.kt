package com.vodotxt.domain

import java.time.LocalDate

data class TodoItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val completed: Boolean = false,
    val priority: Char? = null,
    val completionDate: LocalDate? = null,
    val creationDate: LocalDate? = null,
    val description: String,
    val projects: List<String> = emptyList(),
    val contexts: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val rawLine: String
)
