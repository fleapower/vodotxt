package com.vodotxt.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import java.util.*

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository.getInstance(applicationContext)
        val todoRepo = TodoRepository.getInstance(applicationContext)
        
        val folderId = settingsRepo.driveFolderId.value ?: return Result.failure()
        val todoFileUri = settingsRepo.todoFileUri.value
        val archiveFileUri = settingsRepo.archiveFileUri.value
        
        val initialSyncMode = inputData.getString("initial_sync_mode")

        // 1. Authenticate
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext) ?: return Result.failure()
        val credential = GoogleAccountCredential.usingOAuth2(
            applicationContext, Collections.singleton(DriveScopes.DRIVE)
        ).setSelectedAccount(account.account)

        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("vodo.txt").build()

        val driveHelper = DriveServiceHelper(driveService)

        try {
            // 2. Sync main todo file
            val todoFileName = getFileNameFromUri(todoFileUri, "todo.txt")
            val todoUpdated = syncFile(
                todoFileName, 
                todoFileUri, 
                folderId, 
                todoRepo, 
                settingsRepo, 
                driveHelper, 
                initialSyncMode,
                isArchive = false
            )
            
            // 3. Sync archive file
            var archiveUpdated = false
            if (archiveFileUri != null) {
                val archiveFileName = getFileNameFromUri(archiveFileUri, "done.txt")
                archiveUpdated = syncFile(
                    archiveFileName, 
                    archiveFileUri, 
                    folderId, 
                    todoRepo, 
                    settingsRepo, 
                    driveHelper, 
                    initialSyncMode,
                    isArchive = true
                )
            }

            val outputData = androidx.work.Data.Builder()
                .putBoolean("file_updated", todoUpdated || archiveUpdated)
                .build()

            return Result.success(outputData)
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            return Result.retry()
        }
    }

    private fun getFileNameFromUri(uriString: String?, default: String): String {
        if (uriString == null) return default
        return try {
            val uri = Uri.parse(uriString)
            val cursor = applicationContext.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) it.getString(index) else default
                } else default
            } ?: default
        } catch (e: Exception) {
            default
        }
    }

    private suspend fun syncFile(
        fileName: String,
        localUri: String?,
        folderId: String,
        todoRepo: TodoRepository,
        settingsRepo: SettingsRepository,
        driveHelper: DriveServiceHelper,
        forceMode: String?,
        isArchive: Boolean
    ): Boolean {
        val driveFileId = driveHelper.searchFile(fileName, folderId) ?: driveHelper.createFile(fileName, folderId)
        
        if (driveFileId != null) {
            // 1. Get CURRENT CLOUD HASH
            val remoteMeta = driveHelper.getFileMetadata(driveFileId)
            val currentCloudHash = remoteMeta.md5Checksum ?: ""

            // 2. Get CURRENT LOCAL HASH
            val currentLocalHash = todoRepo.getLocalHash(localUri)

            // 3. Get ANCHOR HASHES (from last successful sync)
            val anchorLocalHash = if (isArchive) settingsRepo.anchorArchiveLocalHash else settingsRepo.anchorTodoLocalHash
            val anchorCloudHash = if (isArchive) settingsRepo.anchorArchiveCloudHash else settingsRepo.anchorTodoCloudHash

            // 4. DETECT CHANGES
            val localChanged = currentLocalHash != anchorLocalHash
            val cloudChanged = currentCloudHash != anchorCloudHash

            Log.d("SyncWorker", "Sync - Cloud Hash Pair: $anchorCloudHash")
            Log.d("SyncWorker", "Sync - Local Hash Pair: $anchorLocalHash")
            Log.d("SyncWorker", "Sync - Current Cloud Hash: $currentCloudHash")
            Log.d("SyncWorker", "Sync - Current Local Hash: $currentLocalHash")
            Log.d("SyncWorker", "Syncing $fileName: localChanged=$localChanged, cloudChanged=$cloudChanged")

            // EARLY EXIT: If hashes are identical, just update anchors and finish
            if (currentLocalHash == currentCloudHash && currentLocalHash.isNotEmpty()) {
                if (isArchive) settingsRepo.setArchiveAnchors(currentLocalHash, currentCloudHash)
                else settingsRepo.setTodoAnchors(currentLocalHash, currentCloudHash)
                return false
            }

            if (forceMode == "REMOTE_WINS" || (cloudChanged && !localChanged)) {
                // Cloud wins: Download
                val remoteContent = driveHelper.readFile(driveFileId)
                if (remoteContent.isNotBlank()) {
                    val todos = remoteContent.split("\n").filter { it.isNotBlank() }.map { TodoParser.parse(it) }
                    todoRepo.saveTodos(todos, localUri, ChangeOrigin.REMOTE)
                    
                    // After downloading, calculate the new local hash and update anchors
                    val newLocalHash = todoRepo.getLocalHash(localUri)
                    if (isArchive) {
                        settingsRepo.setArchiveAnchors(newLocalHash, currentCloudHash)
                    } else {
                        settingsRepo.setTodoAnchors(newLocalHash, currentCloudHash)
                    }
                    return true
                }
            } else if (forceMode == "LOCAL_WINS" || (localChanged && !cloudChanged)) {
                // Local wins: Upload
                val localContentList = todoRepo.loadTodos(localUri)
                val localContent = localContentList.joinToString("\n") { TodoParser.toLine(it) }
                
                driveHelper.saveFile(driveFileId, localContent)
                
                // Get the new hash from Google
                val newCloudHash = driveHelper.getFileMetadata(driveFileId).md5Checksum ?: currentCloudHash
                
                if (isArchive) {
                    settingsRepo.setArchiveAnchors(currentLocalHash, newCloudHash)
                } else {
                    settingsRepo.setTodoAnchors(currentLocalHash, newCloudHash)
                }
            } else if (localChanged && cloudChanged) {
                // CONFLICT!
                Log.w("SyncWorker", "CONFLICT in $fileName. Creating local backup.")
                
                // Load LOCAL version for backup as requested
                val todos = todoRepo.loadTodos(localUri)
                
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val displayTimestamp = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                // 1. Mark conflict in settings for UI to show dialog
                settingsRepo.setSyncConflict(fileName, displayTimestamp)

                val conflictFolder = settingsRepo.conflictFolderUri.value
                if (conflictFolder != null) {
                    val baseName = fileName.substringBeforeLast('.')
                    val extension = fileName.substringAfterLast('.', "txt")
                    val conflictName = "${baseName}_$timestamp.$extension"
                    todoRepo.saveConflict(todos, conflictFolder, conflictName)
                } else {
                    // Fallback to internal storage if no folder selected
                    todoRepo.saveTodos(todos, "${localUri ?: applicationContext.filesDir.absolutePath + "/" + fileName}.conflict", ChangeOrigin.USER)
                }
            }
        }
        return false
    }
}
