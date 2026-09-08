package com.vodotxt.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppTheme { LIGHT, DARK, SYSTEM }
enum class SwipeAction { COMPLETE, POSTPONE, NONE }

class SettingsRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _fontSize = MutableStateFlow(prefs.getInt("font_size", 16))
    val fontSize: StateFlow<Int> = _fontSize

    private val _theme = MutableStateFlow(AppTheme.valueOf(prefs.getString("theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name))
    val theme: StateFlow<AppTheme> = _theme

    private val _showCheckboxes = MutableStateFlow(prefs.getBoolean("show_checkboxes", true))
    val showCheckboxes: StateFlow<Boolean> = _showCheckboxes

    private val _todoFileUri = MutableStateFlow(prefs.getString("todo_file_uri", null))
    val todoFileUri: StateFlow<String?> = _todoFileUri

    private val _archiveFileUri = MutableStateFlow(prefs.getString("archive_file_uri", null))
    val archiveFileUri: StateFlow<String?> = _archiveFileUri

    private val _driveSyncEnabled = MutableStateFlow(prefs.getBoolean("drive_sync_enabled", false))
    val driveSyncEnabled: StateFlow<Boolean> = _driveSyncEnabled

    private val _driveFolderId = MutableStateFlow(prefs.getString("drive_folder_id", null))
    val driveFolderId: StateFlow<String?> = _driveFolderId

    private val _driveFolderName = MutableStateFlow(prefs.getString("drive_folder_name", null))
    val driveFolderName: StateFlow<String?> = _driveFolderName

    private val _lastFilterId = MutableStateFlow(prefs.getString("last_filter_id", null))
    val lastFilterId: StateFlow<String?> = _lastFilterId

    private val _swipeRightAction = MutableStateFlow(SwipeAction.valueOf(prefs.getString("swipe_right_action", SwipeAction.NONE.name) ?: SwipeAction.NONE.name))
    val swipeRightAction: StateFlow<SwipeAction> = _swipeRightAction

    private val _doubleTapToComplete = MutableStateFlow(prefs.getBoolean("double_tap_to_complete", false))
    val doubleTapToComplete: StateFlow<Boolean> = _doubleTapToComplete

    private val _keepLastTag = MutableStateFlow(prefs.getBoolean("keep_last_tag", true))
    val keepLastTag: StateFlow<Boolean> = _keepLastTag

    private val _addCreationDate = MutableStateFlow(prefs.getBoolean("add_creation_date", false))
    val addCreationDate: StateFlow<Boolean> = _addCreationDate

    private val _conflictFolderUri = MutableStateFlow(prefs.getString("conflict_folder_uri", null))
    val conflictFolderUri: StateFlow<String?> = _conflictFolderUri

    // Sync Conflict Info
    private val _syncConflictFile = MutableStateFlow(prefs.getString("sync_conflict_file", null))
    val syncConflictFile: StateFlow<String?> = _syncConflictFile

    private val _syncConflictTime = MutableStateFlow(prefs.getString("sync_conflict_time", null))
    val syncConflictTime: StateFlow<String?> = _syncConflictTime

    // Sync Anchors: The hashes of the files at the moment of the last successful sync
    var anchorTodoLocalHash: String
        get() = prefs.getString("anchor_todo_local_hash", "") ?: ""
        private set(value) = prefs.edit().putString("anchor_todo_local_hash", value).apply()

    var anchorTodoCloudHash: String
        get() = prefs.getString("anchor_todo_cloud_hash", "") ?: ""
        private set(value) = prefs.edit().putString("anchor_todo_cloud_hash", value).apply()

    var anchorArchiveLocalHash: String
        get() = prefs.getString("anchor_archive_local_hash", "") ?: ""
        private set(value) = prefs.edit().putString("anchor_archive_local_hash", value).apply()

    var anchorArchiveCloudHash: String
        get() = prefs.getString("anchor_archive_cloud_hash", "") ?: ""
        private set(value) = prefs.edit().putString("anchor_archive_cloud_hash", value).apply()

    fun setTodoAnchors(localHash: String, cloudHash: String) {
        anchorTodoLocalHash = localHash
        anchorTodoCloudHash = cloudHash
    }

    fun setArchiveAnchors(localHash: String, cloudHash: String) {
        anchorArchiveLocalHash = localHash
        anchorArchiveCloudHash = cloudHash
    }

    fun setFontSize(size: Int) {
        _fontSize.value = size
        prefs.edit().putInt("font_size", size).apply()
    }

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString("theme", theme.name).apply()
    }

    fun setShowCheckboxes(show: Boolean) {
        _showCheckboxes.value = show
        prefs.edit().putBoolean("show_checkboxes", show).apply()
    }

    fun setTodoFileUri(uri: String?) {
        _todoFileUri.value = uri
        prefs.edit().putString("todo_file_uri", uri).apply()
    }

    fun setArchiveFileUri(uri: String?) {
        _archiveFileUri.value = uri
        prefs.edit().putString("archive_file_uri", uri).apply()
    }

    fun setDriveSyncEnabled(enabled: Boolean) {
        _driveSyncEnabled.value = enabled
        prefs.edit().putBoolean("drive_sync_enabled", enabled).apply()
    }

    fun setDriveFolder(id: String?, name: String?) {
        _driveFolderId.value = id
        _driveFolderName.value = name
        prefs.edit().putString("drive_folder_id", id).putString("drive_folder_name", name).apply()
    }

    fun setLastFilterId(id: String?) {
        _lastFilterId.value = id
        prefs.edit().putString("last_filter_id", id).apply()
    }

    fun setSwipeRightAction(action: SwipeAction) {
        _swipeRightAction.value = action
        prefs.edit().putString("swipe_right_action", action.name).apply()
    }

    fun setDoubleTapToComplete(enabled: Boolean) {
        _doubleTapToComplete.value = enabled
        prefs.edit().putBoolean("double_tap_to_complete", enabled).apply()
    }

    fun setKeepLastTag(enabled: Boolean) {
        _keepLastTag.value = enabled
        prefs.edit().putBoolean("keep_last_tag", enabled).apply()
    }

    fun setAddCreationDate(enabled: Boolean) {
        _addCreationDate.value = enabled
        prefs.edit().putBoolean("add_creation_date", enabled).apply()
    }

    fun setConflictFolderUri(uri: String?) {
        _conflictFolderUri.value = uri
        prefs.edit().putString("conflict_folder_uri", uri).apply()
    }

    fun setSyncConflict(fileName: String?, time: String?) {
        _syncConflictFile.value = fileName
        _syncConflictTime.value = time
        prefs.edit()
            .putString("sync_conflict_file", fileName)
            .putString("sync_conflict_time", time)
            .apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
