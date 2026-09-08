package com.vodotxt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.vodotxt.data.DriveServiceHelper
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveFolderPicker(
    onFolderSelected: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    var currentFolderId by remember { mutableStateOf("root") }
    var folderStack by remember { mutableStateOf(listOf<Pair<String, String>>()) } // id, name
    var folders by remember { mutableStateOf<List<com.google.api.services.drive.model.File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val driveHelper = remember {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE)
            ).setSelectedAccount(account.account)

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("vodo.txt").build()
            DriveServiceHelper(driveService)
        } else null
    }

    LaunchedEffect(currentFolderId) {
        if (driveHelper != null) {
            isLoading = true
            try {
                folders = driveHelper.listFolders(currentFolderId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Drive Folder") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                if (folderStack.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val last = folderStack.last()
                                folderStack = folderStack.dropLast(1)
                                currentFolderId = if (folderStack.isEmpty()) "root" else folderStack.last().first
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Back to ${if (folderStack.size == 1) "Root" else folderStack[folderStack.size - 2].second}")
                    }
                    HorizontalDivider()
                }

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(folders) { folder ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentFolderId = folder.id
                                        folderStack = folderStack + (folder.id to folder.name)
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(folder.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = if (currentFolderId == "root") "My Drive" else folderStack.lastOrNull()?.second ?: "Unknown Folder"
                    onFolderSelected(currentFolderId, name)
                },
                enabled = !isLoading
            ) {
                Text("Select Current")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
