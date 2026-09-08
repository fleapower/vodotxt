package com.vodotxt.data

import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DriveServiceHelper(private val mDriveService: Drive) {

    suspend fun createFile(name: String, parentId: String? = null): String? = withContext(Dispatchers.IO) {
        val metadata = File()
            .setName(name)
            .setMimeType("text/plain")
        if (parentId != null) {
            metadata.parents = listOf(parentId)
        }
        val googleFile = mDriveService.files().create(metadata).execute()
        googleFile.id
    }

    suspend fun searchFile(name: String, parentId: String? = null): String? = withContext(Dispatchers.IO) {
        var query = "name = '$name' and trashed = false"
        if (parentId != null) {
            query += " and '$parentId' in parents"
        }
        val result = mDriveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .execute()
        result.files.firstOrNull()?.id
    }

    suspend fun readFile(fileId: String): String = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        mDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        outputStream.toString()
    }

    suspend fun saveFile(fileId: String, content: String) = withContext(Dispatchers.IO) {
        val metadata = File()
        val contentStream = InputStreamContent(
            "text/plain",
            ByteArrayInputStream(content.toByteArray())
        )
        mDriveService.files().update(fileId, metadata, contentStream).execute()
    }

    suspend fun listFolders(parentId: String? = "root"): List<File> = withContext(Dispatchers.IO) {
        val query = "mimeType = 'application/vnd.google-apps.folder' and trashed = false and '$parentId' in parents"
        val result = mDriveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
        result.files ?: emptyList()
    }

    suspend fun getFileMetadata(fileId: String): File = withContext(Dispatchers.IO) {
        mDriveService.files().get(fileId)
            .setFields("id, name, modifiedTime, size, md5Checksum")
            .execute()
    }
}
