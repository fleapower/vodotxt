package com.vodotxt.data

import android.content.Context
import androidx.core.net.toUri
import com.vodotxt.domain.TodoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest
import androidx.documentfile.provider.DocumentFile
import android.net.Uri

enum class ChangeOrigin { USER, REMOTE }

class TodoRepository private constructor(private val context: Context) {
    // Keep track of IDs for lines to maintain stability during a session
    private val idMap = mutableMapOf<String, String>()

    private val _onTodoFileChanged = MutableSharedFlow<ChangeOrigin>(replay = 0)
    val onTodoFileChanged = _onTodoFileChanged.asSharedFlow()

    suspend fun loadTodos(uriString: String?): List<TodoItem> = withContext(Dispatchers.IO) {
        val lines = if (uriString != null) {
            try {
                val uri = uriString.toUri()
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readLines()
                reader.close()
                content
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            // Fallback to internal storage if no file selected yet
            val file = java.io.File(context.filesDir, "todo.txt")
            if (file.exists()) file.readLines() else emptyList()
        }

        val filteredLines = lines.asSequence().filter { it.isNotBlank() }
        val occurrenceMap = mutableMapOf<String, Int>()
        
        filteredLines.map { line ->
            val item = TodoParser.parse(line)
            val occurrence = occurrenceMap.getOrDefault(line, 0)
            occurrenceMap[line] = occurrence + 1

            val uniqueLineKey = "$line|$occurrence"
            val id = idMap.getOrPut(uniqueLineKey) { item.id } 
            item.copy(id = id)
        }.toList()
    }

    suspend fun saveTodos(todos: List<TodoItem>, uriString: String?, origin: ChangeOrigin = ChangeOrigin.USER) = withContext(Dispatchers.IO) {
        val lines = todos.asSequence().map { TodoParser.toLine(it) }.toList()
        val content = lines.joinToString("\n")
        
        // Update idMap
        idMap.clear()
        val occurrenceMap = mutableMapOf<String, Int>()
        todos.asSequence().zip(lines.asSequence()).forEach { (item, line) ->
            val occurrence = occurrenceMap.getOrDefault(line, 0)
            occurrenceMap[line] = occurrence + 1
            val uniqueLineKey = "$line|$occurrence"
            idMap[uniqueLineKey] = item.id
        }

        val success = if (uriString != null) {
            try {
                val uri = uriString.toUri()
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(content)
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        } else {
            val file = java.io.File(context.filesDir, "todo.txt")
            file.writeText(content)
            true
        }
        
        if (success) {
            _onTodoFileChanged.emit(origin)
        }
    }

    suspend fun addTodo(todo: String, uriString: String?) = withContext(Dispatchers.IO) {
        val current = loadTodos(uriString).toMutableList()
        current.add(0, TodoParser.parse(todo))
        saveTodos(current, uriString, ChangeOrigin.USER)
    }

    suspend fun archiveTasks(tasks: List<TodoItem>, todoUri: String?, archiveUri: String?): Boolean = withContext(Dispatchers.IO) {
        if (archiveUri == null) return@withContext false

        try {
            // 1. Load existing archive content
            val uri = archiveUri.toUri()
            val archiveLines = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readLines()
            } ?: emptyList()

            // 2. Append new tasks to archive
            val newArchiveLines = archiveLines + tasks.asSequence().map { TodoParser.toLine(it) }.toList()
            val archiveContent = newArchiveLines.joinToString("\n")

            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(archiveContent)
                }
            }

            // 3. Remove from main todo file ONLY after archive write succeeds
            val currentTodos = loadTodos(todoUri).toMutableList()
            val idsToArchive = tasks.asSequence().map { it.id }.toSet()
            currentTodos.removeAll { idsToArchive.contains(it.id) }
            saveTodos(currentTodos, todoUri, ChangeOrigin.USER)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Calculates the MD5 hash of the local file.
     */
    suspend fun getLocalHash(uriString: String?): String = withContext(Dispatchers.IO) {
        try {
            val bytes = if (uriString != null) {
                context.contentResolver.openInputStream(uriString.toUri())?.use { it.readBytes() } ?: ByteArray(0)
            } else {
                val file = java.io.File(context.filesDir, "todo.txt")
                if (file.exists()) file.readBytes() else ByteArray(0)
            }
            if (bytes.isEmpty()) return@withContext ""
            
            val md = MessageDigest.getInstance("MD5")
            val hash = md.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun saveConflict(todos: List<TodoItem>, folderUriString: String, fileName: String) = withContext(Dispatchers.IO) {
        try {
            val treeUri = Uri.parse(folderUriString)
            val parentDir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
            val conflictFile = parentDir.createFile("text/plain", fileName) ?: return@withContext

            val lines = todos.asSequence().map { TodoParser.toLine(it) }.toList()
            val content = lines.joinToString("\n")

            context.contentResolver.openOutputStream(conflictFile.uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(content)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TodoRepository? = null

        fun getInstance(context: Context): TodoRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TodoRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
