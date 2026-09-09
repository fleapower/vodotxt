package com.vodotxt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vodotxt.data.AppTheme
import com.vodotxt.data.SwipeAction
import com.vodotxt.data.FilterRepository
import com.vodotxt.data.SettingsRepository
import com.vodotxt.data.TodoRepository
import com.vodotxt.domain.DateFilterType
import com.vodotxt.domain.TodoFilter
import com.vodotxt.domain.TodoItem
import com.vodotxt.domain.AppConfig
import com.vodotxt.domain.SortMode
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vodotxt.data.SyncWorker
import com.vodotxt.data.TodoParser
import com.vodotxt.data.RecurrenceCalculator
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

class TodoListViewModel(
    private val repository: TodoRepository,
    private val filterRepository: FilterRepository,
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager,
) : ViewModel() {
    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val allTodos: StateFlow<List<TodoItem>> = _todos.asStateFlow()


    val fontSize = settingsRepository.fontSize
    val theme = settingsRepository.theme
    val showCheckboxes = settingsRepository.showCheckboxes
    val todoFileUri = settingsRepository.todoFileUri
    val archiveFileUri = settingsRepository.archiveFileUri
    val driveSyncEnabled = settingsRepository.driveSyncEnabled
    val driveFolderName = settingsRepository.driveFolderName
    val swipeRightAction = settingsRepository.swipeRightAction
    val doubleTapToComplete = settingsRepository.doubleTapToComplete
    val keepLastTag = settingsRepository.keepLastTag
    val addCreationDate = settingsRepository.addCreationDate
    val conflictFolderUri = settingsRepository.conflictFolderUri
    val syncConflictFile = settingsRepository.syncConflictFile
    val syncConflictTime = settingsRepository.syncConflictTime

    private val _isSyncing = MutableStateFlow(value = false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    // Unified filter state - This is the single source of truth for all filtering
    private val _currentFilter = MutableStateFlow(TodoFilter(name = "", id = "transient"))
    val currentFilter: StateFlow<TodoFilter> = _currentFilter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _savedFilters = MutableStateFlow<List<TodoFilter>>(emptyList())
    val savedFilters: StateFlow<List<TodoFilter>> = _savedFilters

    private val _selectedSavedFilterId = MutableStateFlow(settingsRepository.lastFilterId.value)
    val selectedSavedFilterId: StateFlow<String?> = _selectedSavedFilterId

    val todos: StateFlow<List<TodoItem>> = combine(
        _todos,
        _currentFilter,
        _searchQuery,
    ) { todos, filter, query ->
        val filtered = todos.filter { item ->
            val dateMatch = when (filter.dateFilter) {
                DateFilterType.ALL -> true
                DateFilterType.TODAY -> {
                    val due = item.metadata["due"]
                    val dueDate = TodoParser.safeParseDate(due)
                    (dueDate == null) || !dueDate.isAfter(LocalDate.now())
                }
                DateFilterType.TOMORROW -> {
                    val due = item.metadata["due"]
                    val dueDate = TodoParser.safeParseDate(due)
                    (dueDate != null) && dueDate.isEqual(LocalDate.now().plusDays(1))
                }
                DateFilterType.CUSTOM -> {
                    val due = item.metadata["due"]
                    val dueDate = TodoParser.safeParseDate(due)
                    (dueDate != null) && (filter.customDate != null) && dueDate.isEqual(filter.customDate)
                }
            }
            
            val projectMatch = if (filter.selectedProjects.isEmpty()) true else {
                val hasMatch = item.projects.any { filter.selectedProjects.contains(it) }
                if (filter.invertProjects) !hasMatch else hasMatch
            }
            
            val contextMatch = if (filter.selectedContexts.isEmpty()) true else {
                val hasMatch = item.contexts.any { filter.selectedContexts.contains(it) }
                if (filter.invertContexts) !hasMatch else hasMatch
            }

            val priorityMatch = (filter.selectedPriorities.isEmpty()) ||
                    (item.priority != null && filter.selectedPriorities.contains(item.priority))

            val completionMatch = when (filter.completionFilter ?: com.vodotxt.domain.CompletionFilter.ALL) {
                com.vodotxt.domain.CompletionFilter.ALL -> true
                com.vodotxt.domain.CompletionFilter.INCOMPLETE -> !item.completed
                com.vodotxt.domain.CompletionFilter.COMPLETED -> item.completed
            }

            val searchMatch = query.isEmpty() || item.description.contains(query, ignoreCase = true) ||
                    item.projects.any { it.contains(query, ignoreCase = true) } ||
                    item.contexts.any { it.contains(query, ignoreCase = true) }
            
            dateMatch && projectMatch && contextMatch && priorityMatch && completionMatch && searchMatch
        }

        val sortMode = filter.sortMode
        when (sortMode) {
            SortMode.FILE -> filtered
            SortMode.DUE_DATE -> 
                filtered.sortedWith(
                    compareBy<TodoItem> { it.completed }
                        .thenBy { it.metadata["due"] ?: "9999-99-99" }
                        .thenBy { it.priority ?: 'Z' },
                )
            SortMode.PRIORITY -> 
                filtered.sortedWith(
                    compareBy<TodoItem> { it.completed }
                        .thenBy { it.priority ?: 'Z' }
                        .thenBy { it.metadata["due"] ?: "9999-99-99" },
                )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadFilters()
        
        // Persist the last used filter ID (skip the initial emission to avoid overwriting restoration)
        viewModelScope.launch {
            _selectedSavedFilterId.drop(1).collect { id ->
                settingsRepository.setLastFilterId(id)
            }
        }

        // Reload todos whenever the file URI changes
        viewModelScope.launch {
            settingsRepository.todoFileUri.collect {
                refreshTodos()
            }
        }

        // Observe repository file changes
        viewModelScope.launch {
            repository.onTodoFileChanged.collect { _ ->
                // Reload whenever the file changes, regardless of origin (User or Remote)
                // This ensures separate activities (like QuickAdd) stay in sync.
                reloadData()
            }
        }

        // Observe sync status
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("drive_sync").collect { workInfos ->
                // Only show spinning when state is actively RUNNING
                val isActivelySyncing = workInfos.any { it.state == WorkInfo.State.RUNNING }
                _isSyncing.value = isActivelySyncing
            }
        }
    }

    private var syncJob: Job? = null

    private fun scheduleSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(3.seconds)
            triggerSync(delaySeconds = 0)
        }
    }

    private fun triggerSync(mode: String? = null, delaySeconds: Long = 0) {
        if (!driveSyncEnabled.value) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
        mode?.let { inputData.putString("initial_sync_mode", it) }

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(inputData.build())
            .build()
            
        // Use APPEND policy to ensure every sync finishes without cancellation
        workManager.enqueueUniqueWork(
            "drive_sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest,
        )
    }

    private suspend fun reloadData() {
        _todos.value = repository.loadTodos(settingsRepository.todoFileUri.value)
    }

    private suspend fun refreshTodos() {
        reloadData()
        scheduleSync()
    }

    fun triggerRemoteCheck() {
        viewModelScope.launch {
            reloadData() // Refresh local view immediately when returning to app
            syncJob?.cancel()
            triggerSync(null, 0)
        }
    }

    fun resolveConflict(useRemote: Boolean) {
        viewModelScope.launch {
            // 1. Clear the conflict state first
            settingsRepository.setSyncConflict(null, null)
            
            // 2. Trigger sync with chosen winner
            triggerSync(if (useRemote) "REMOTE_WINS" else "LOCAL_WINS", 0)
        }
    }

    fun requestSync() {
        scheduleSync()
    }

    private fun loadFilters() {
        viewModelScope.launch {
            val filters = filterRepository.loadFilters()
            _savedFilters.value = filters

            // Restore last used filter
            val lastId = settingsRepository.lastFilterId.value
            if (lastId != null) {
                filters.find { it.id == lastId }?.let {
                    setActiveFilter(it)
                }
            }
        }
    }

    fun saveFilter(filter: TodoFilter) {
        viewModelScope.launch {
            val current = _savedFilters.value.toMutableList()
            current.add(filter)
            _savedFilters.value = current
            filterRepository.saveFilters(current)
        }
    }

    fun deleteFilter(filter: TodoFilter) {
        viewModelScope.launch {
            val current = _savedFilters.value.toMutableList()
            current.removeAll { it.id == filter.id }
            _savedFilters.value = current
            filterRepository.saveFilters(current)
            if (_selectedSavedFilterId.value == filter.id) {
                _selectedSavedFilterId.value = null
            }
        }
    }

    fun reorderFilters(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = _savedFilters.value.toMutableList()
            if (fromIndex in current.indices && toIndex in current.indices) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                _savedFilters.value = current
                filterRepository.saveFilters(current)
            }
        }
    }

    fun setActiveFilter(filter: TodoFilter?) {
        if (filter == null) {
            _currentFilter.value = TodoFilter(name = "", id = "transient")
            _selectedSavedFilterId.value = null
        } else {
            _currentFilter.value = filter
            _selectedSavedFilterId.value = filter.id
        }
    }

    fun toggleProject(project: String) {
        val current = _currentFilter.value
        val newList = if (current.selectedProjects.contains(project)) {
            current.selectedProjects - project
        } else {
            current.selectedProjects + project
        }
        _currentFilter.value = current.copy(selectedProjects = newList)
        _selectedSavedFilterId.value = null // Changing selection breaks the preset
    }

    fun toggleContext(context: String) {
        val current = _currentFilter.value
        val newList = if (current.selectedContexts.contains(context)) {
            current.selectedContexts - context
        } else {
            current.selectedContexts + context
        }
        _currentFilter.value = current.copy(selectedContexts = newList)
        _selectedSavedFilterId.value = null
    }

    fun setInvertProjects(invert: Boolean) {
        _currentFilter.value = _currentFilter.value.copy(invertProjects = invert)
        _selectedSavedFilterId.value = null
    }

    fun setInvertContexts(invert: Boolean) {
        _currentFilter.value = _currentFilter.value.copy(invertContexts = invert)
        _selectedSavedFilterId.value = null
    }

    fun clearAllFilters() {
        _currentFilter.value = TodoFilter(name = "", id = "transient")
        _selectedSavedFilterId.value = null
    }

    fun setSortMode(mode: SortMode) {
        _currentFilter.value = _currentFilter.value.copy(sortMode = mode)
        _selectedSavedFilterId.value = null 
    }

    fun cyclePriorityFilter() {
        val currentPriorities = _currentFilter.value.selectedPriorities
        val nextPriorities = when {
            currentPriorities.isEmpty() -> listOf('A')
            currentPriorities == listOf('A') -> listOf('B')
            currentPriorities == listOf('B') -> listOf('C')
            currentPriorities == listOf('C') -> listOf('D')
            else -> emptyList()
        }
        _currentFilter.value = _currentFilter.value.copy(selectedPriorities = nextPriorities)
        _selectedSavedFilterId.value = null
    }

    fun cycleDateFilter() {
        val nextDateFilter = when (_currentFilter.value.dateFilter) {
            DateFilterType.ALL -> DateFilterType.TODAY
            DateFilterType.TODAY -> DateFilterType.TOMORROW
            DateFilterType.TOMORROW -> DateFilterType.ALL
            DateFilterType.CUSTOM -> DateFilterType.ALL
        }
        _currentFilter.value = _currentFilter.value.copy(
            dateFilter = nextDateFilter,
            customDate = null,
        )
        _selectedSavedFilterId.value = null
    }

    fun cycleCompletionFilter() {
        val nextFilter = when (_currentFilter.value.completionFilter ?: com.vodotxt.domain.CompletionFilter.ALL) {
            com.vodotxt.domain.CompletionFilter.ALL -> com.vodotxt.domain.CompletionFilter.INCOMPLETE
            com.vodotxt.domain.CompletionFilter.INCOMPLETE -> com.vodotxt.domain.CompletionFilter.COMPLETED
            com.vodotxt.domain.CompletionFilter.COMPLETED -> com.vodotxt.domain.CompletionFilter.ALL
        }
        _currentFilter.value = _currentFilter.value.copy(completionFilter = nextFilter)
        _selectedSavedFilterId.value = null
    }

    fun moveTodo(fromId: String, toId: String) {
        viewModelScope.launch {
            val current = _todos.value.toMutableList()
            val fromIndex = current.indexOfFirst { it.id == fromId }
            val toIndex = current.indexOfFirst { it.id == toId }

            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                _todos.value = current
                repository.saveTodos(current, settingsRepository.todoFileUri.value)
            }
        }
    }

    fun addTodo(line: String) {
        viewModelScope.launch {
            repository.addTodo(line, settingsRepository.todoFileUri.value)
            refreshTodos()
        }
    }

    fun updateTodo(item: TodoItem) {
        viewModelScope.launch {
            _todos.update { current ->
                val list = current.toMutableList()
                val index = list.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    list[index] = item
                }
                list
            }
            repository.saveTodos(_todos.value, settingsRepository.todoFileUri.value)
            refreshTodos()
        }
    }

    fun toggleTodo(item: TodoItem) {
        val updated = handleCompletion(item)
        updateTodo(updated)
    }

    fun postponeTodo(item: TodoItem) {
        val currentDue = item.metadata["due"]
        val baseDate = TodoParser.safeParseDate(currentDue) ?: LocalDate.now()
        val nextDate = baseDate.plusDays(1)

        val newMetadata = item.metadata.toMutableMap()
        newMetadata["due"] = nextDate.toString()

        val updated = item.copy(metadata = newMetadata)
        updateTodo(updated.copy(rawLine = TodoParser.toLine(updated)))
    }

    fun updateTodosDate(ids: Set<String>, date: String?) {
        viewModelScope.launch {
            val current = _todos.value.toMutableList()
            var changed = false
            ids.forEach { id ->
                val index = current.indexOfFirst { it.id == id }
                if (index != -1) {
                    val item = current[index]
                    val newMetadata = item.metadata.toMutableMap()
                    if (date != null) newMetadata["due"] = date else newMetadata.remove("due")
                    val newItem = item.copy(metadata = newMetadata)
                    current[index] = newItem.copy(rawLine = TodoParser.toLine(newItem))
                    changed = true
                }
            }
            if (changed) {
                repository.saveTodos(current, settingsRepository.todoFileUri.value)
                refreshTodos()
            }
        }
    }

    fun updateTodosPriority(ids: Set<String>, priority: Char?) {
        viewModelScope.launch {
            val current = _todos.value.toMutableList()
            var changed = false
            ids.forEach { id ->
                val index = current.indexOfFirst { it.id == id }
                if (index != -1) {
                    val item = current[index]
                    val newItem = item.copy(priority = priority)
                    current[index] = newItem.copy(rawLine = TodoParser.toLine(newItem))
                    changed = true
                }
            }
            if (changed) {
                repository.saveTodos(current, settingsRepository.todoFileUri.value)
                refreshTodos()
            }
        }
    }

    fun updateTodosTags(ids: Set<String>, contextsToAdd: Set<String>, contextsToRemove: Set<String>, projectsToAdd: Set<String>, projectsToRemove: Set<String>) {
        viewModelScope.launch {
            val current = _todos.value.toMutableList()
            var changed = false
            ids.forEach { id ->
                val index = current.indexOfFirst { it.id == id }
                if (index != -1) {
                    val item = current[index]
                    val newContexts = (item.contexts.toSet() + contextsToAdd - contextsToRemove).toList().sorted()
                    val newProjects = (item.projects.toSet() + projectsToAdd - projectsToRemove).toList().sorted()
                    
                    if (newContexts != item.contexts || newProjects != item.projects) {
                        val newItem = item.copy(contexts = newContexts, projects = newProjects)
                        current[index] = newItem.copy(rawLine = TodoParser.toLine(newItem))
                        changed = true
                    }
                }
            }
            if (changed) {
                repository.saveTodos(current, settingsRepository.todoFileUri.value)
                refreshTodos()
            }
        }
    }

    fun toggleTodosCompletion(ids: Set<String>) {
        viewModelScope.launch {
            val current = _todos.value.toMutableList()
            var changed = false
            ids.forEach { id ->
                val index = current.indexOfFirst { it.id == id }
                if (index != -1) {
                    current[index] = handleCompletion(current[index])
                    changed = true
                }
            }
            if (changed) {
                repository.saveTodos(current, settingsRepository.todoFileUri.value)
                refreshTodos()
            }
        }
    }

    fun deleteTodos(ids: Set<String>) {
        viewModelScope.launch {
            val current = _todos.value.toMutableList()
            val initialSize = current.size
            current.removeAll { ids.contains(it.id) }
            if (current.size != initialSize) {
                repository.saveTodos(current, settingsRepository.todoFileUri.value)
                refreshTodos()
            }
        }
    }

    private val _archiveEvent = MutableSharedFlow<Int>()
    val archiveEvent: SharedFlow<Int> = _archiveEvent

    fun archiveCompletedTasks() {
        viewModelScope.launch {
            val completedTasks = _todos.value.filter { it.completed }
            if (completedTasks.isNotEmpty()) {
                val success = repository.archiveTasks(
                    completedTasks,
                    settingsRepository.todoFileUri.value,
                    settingsRepository.archiveFileUri.value
                )
                if (success) {
                    _archiveEvent.emit(completedTasks.size)
                    refreshTodos()
                }
            }
        }
    }

    fun setTodoFileUri(uri: String?) {
        settingsRepository.setTodoFileUri(uri)
    }

    fun setupStorageFolder(context: Context, folderUri: Uri) {
        viewModelScope.launch {
            try {
                val rootFolder = DocumentFile.fromTreeUri(context, folderUri) ?: return@launch
                
                // 1. Create or find vodo.txt subfolder
                val folder = rootFolder.findFile("vodo.txt") ?: rootFolder.createDirectory("vodo.txt")
                if (folder == null) return@launch

                // 2. Create or find todo.txt
                val todoFile = folder.findFile("todo.txt") ?: folder.createFile("text/plain", "todo.txt")
                
                // 3. Create or find done.txt
                val doneFile = folder.findFile("done.txt") ?: folder.createFile("text/plain", "done.txt")
                
                if (todoFile != null && doneFile != null) {
                    // 4. Migration: If internal file has content, copy it to the new external file
                    val internalFile = File(context.filesDir, "todo.txt")
                    if (internalFile.exists()) {
                        val content = internalFile.readText()
                        if (content.isNotBlank()) {
                            context.contentResolver.openOutputStream(todoFile.uri)?.use { 
                                it.write(content.toByteArray())
                            }
                        }
                        internalFile.delete()
                    }

                    // 5. Save URIs to settings
                    settingsRepository.setTodoFileUri(todoFile.uri.toString())
                    settingsRepository.setArchiveFileUri(doneFile.uri.toString())
                    settingsRepository.setConflictFolderUri(folder.uri.toString())
                    
                    // 6. Refresh
                    refreshTodos()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setArchiveFileUri(uriString: String?) {
        settingsRepository.setArchiveFileUri(uriString)
    }

    fun setConflictFolderUri(uriString: String?) {
        settingsRepository.setConflictFolderUri(uriString)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFontSize(size: Int) {
        settingsRepository.setFontSize(size)
    }

    fun setTheme(theme: AppTheme) {
        settingsRepository.setTheme(theme)
    }

    fun setShowCheckboxes(show: Boolean) {
        settingsRepository.setShowCheckboxes(show)
    }

    fun setSwipeRightAction(action: SwipeAction) {
        settingsRepository.setSwipeRightAction(action)
    }

    fun setDoubleTapToComplete(enabled: Boolean) {
        settingsRepository.setDoubleTapToComplete(enabled)
    }

    fun setKeepLastTag(enabled: Boolean) {
        settingsRepository.setKeepLastTag(enabled)
    }

    fun setAddCreationDate(enabled: Boolean) {
        settingsRepository.setAddCreationDate(enabled)
    }

    fun setDriveSyncEnabled(enabled: Boolean) {
        settingsRepository.setDriveSyncEnabled(enabled)
        if (enabled) triggerSync(delaySeconds = 0)
    }

    fun performInitialSync(useRemote: Boolean, folderId: String, folderName: String) {
        viewModelScope.launch {
            // 1. Set the folder first
            settingsRepository.setDriveFolder(folderId, folderName)

            // 2. Trigger the SyncWorker with the specific choice
            triggerSync(if (useRemote) "REMOTE_WINS" else "LOCAL_WINS", delaySeconds = 0)
        }
    }

    fun exportConfigJson(): String {
        val config = AppConfig(
            fontSize = fontSize.value,
            theme = theme.value,
            showCheckboxes = showCheckboxes.value,
            swipeRightAction = swipeRightAction.value,
            savedFilters = savedFilters.value,
            invertAdHocProjects = _currentFilter.value.invertProjects,
            invertAdHocContexts = _currentFilter.value.invertContexts,
            keepLastTag = keepLastTag.value,
            addCreationDate = addCreationDate.value
        )
        return Gson().toJson(config)
    }

    fun importConfigJson(json: String) {
        viewModelScope.launch {
            try {
                val config = Gson().fromJson(json, AppConfig::class.java) ?: return@launch

                // Disable Drive sync on import as requested
                setDriveSyncEnabled(false)

                config.fontSize?.let { setFontSize(it) }
                config.theme?.let { setTheme(it) }
                config.showCheckboxes?.let { setShowCheckboxes(it) }
                config.swipeRightAction?.let { setSwipeRightAction(it) }
                
                config.invertAdHocProjects?.let { setInvertProjects(it) }
                config.invertAdHocContexts?.let { setInvertContexts(it) }
                config.keepLastTag?.let { setKeepLastTag(it) }
                config.addCreationDate?.let { setAddCreationDate(it) }

                config.savedFilters?.let { filters ->
                    _savedFilters.value = filters
                    filterRepository.saveFilters(filters)
                }

                // Refresh list based on existing file URIs
                refreshTodos()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleCompletion(item: TodoItem): TodoItem {
        val becomingCompleted = !item.completed
        val rTag = item.metadata["r"]
        val today = LocalDate.now()

        val updatedItem = if (becomingCompleted && rTag != null) {
            val nextDate = RecurrenceCalculator.calculateNextDate(rTag, today)
            val newMetadata = item.metadata.toMutableMap()
            newMetadata["due"] = nextDate.toString()

            if (keepLastTag.value) {
                newMetadata["last"] = today.toString()
            } else {
                newMetadata.remove("last")
            }

            item.copy(
                completed = false,
                completionDate = null,
                metadata = newMetadata,
            )
        } else {
            item.copy(
                completed = becomingCompleted,
                completionDate = if (becomingCompleted) today else null
            )
        }
        return updatedItem.copy(rawLine = TodoParser.toLine(updatedItem))
    }

    class Factory(
        private val repository: TodoRepository,
        private val filterRepository: FilterRepository,
        private val settingsRepository: SettingsRepository,
        private val workManager: WorkManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TodoListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TodoListViewModel(repository, filterRepository, settingsRepository, workManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
