package com.vodotxt.data

import android.content.Context
import com.vodotxt.domain.TodoFilter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FilterRepository(private val context: Context) {
    private val fileName = "filters.json"
    private val filterFile: File get() = File(context.filesDir, fileName)
    private val gson = Gson()

    suspend fun loadFilters(): List<TodoFilter> = withContext(Dispatchers.IO) {
        if (!filterFile.exists()) return@withContext emptyList()
        try {
            val json = filterFile.readText()
            val type = object : TypeToken<List<TodoFilter>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveFilters(filters: List<TodoFilter>) = withContext(Dispatchers.IO) {
        val json = gson.toJson(filters)
        filterFile.writeText(json)
    }
}
