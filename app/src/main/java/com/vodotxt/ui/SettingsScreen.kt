package com.vodotxt.ui

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.vodotxt.data.AppTheme
import com.vodotxt.data.SwipeAction
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TodoListViewModel,
    onPickTodoFile: () -> Unit,
    onPickArchiveFile: () -> Unit,
    onPickConflictFolder: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit
) {
    val fontSize by viewModel.fontSize.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val showCheckboxes by viewModel.showCheckboxes.collectAsState()
    val doubleTapToComplete by viewModel.doubleTapToComplete.collectAsState()
    val keepLastTag by viewModel.keepLastTag.collectAsState()
    val addCreationDate by viewModel.addCreationDate.collectAsState()
    val todoFileUri by viewModel.todoFileUri.collectAsState()
    val archiveFileUri by viewModel.archiveFileUri.collectAsState()
    val driveSyncEnabled by viewModel.driveSyncEnabled.collectAsState()
    val swipeRightAction by viewModel.swipeRightAction.collectAsState()

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    
    val packageInfo = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "Unknown"

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(viewModel.exportConfigJson())
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    viewModel.importConfigJson(reader.readText())
                }
            } catch (_: Exception) {}
        }
    }

    var fontSizeText by remember { mutableStateOf(fontSize.toString()) }
    
    var showFolderPicker by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var selectedFolderInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    
    var archivedCount by remember { mutableIntStateOf(0) }
    var showArchiveSuccess by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        viewModel.archiveEvent.collect { count ->
            archivedCount = count
            showArchiveSuccess = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Google Drive Sync Section
            Column {
                Text("Google Drive Sync", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { 
                        if (!driveSyncEnabled) onSignIn() else viewModel.setDriveSyncEnabled(false)
                    }
                ) {
                    Checkbox(
                        checked = driveSyncEnabled,
                        onCheckedChange = { if (it) onSignIn() else viewModel.setDriveSyncEnabled(false) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Enable Google Drive Sync")
                }

                if (driveSyncEnabled) {
                    val folderName by viewModel.driveFolderName.collectAsState()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Folder: ${folderName ?: "None selected"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showFolderPicker = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Change Drive Folder")
                    }
                }
            }

            HorizontalDivider()

            // File Section
            Column {
                Text("Files", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                // Todo File
                Column {
                    Text("Todo File", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = if (todoFileUri != null) {
                            Uri.parse(todoFileUri).path?.split("/")?.lastOrNull() ?: "Custom File"
                        } else "Default (Internal Storage)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onPickTodoFile,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Choose todo.txt file")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Archive File
                Column {
                    Text("Archive File", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = if (archiveFileUri != null) {
                            Uri.parse(archiveFileUri).path?.split("/")?.lastOrNull() ?: "Custom File"
                        } else "None Selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onPickArchiveFile,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Choose archive file (done.txt)")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Conflicts Folder
                Column {
                    val conflictFolderUri by viewModel.conflictFolderUri.collectAsState()
                    Text("Conflicts Folder", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = if (conflictFolderUri != null) {
                            Uri.decode(Uri.parse(conflictFolderUri).lastPathSegment)?.removePrefix("primary:") ?: "Custom Folder"
                        } else "Default (Internal App Storage)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onPickConflictFolder,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Choose conflicts folder")
                    }
                    Text(
                        text = "If a sync conflict occurs, a copy of the local version will be saved here as a backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            HorizontalDivider()

            // Gestures Section
            Column {
                Text("Gestures", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                Column {
                    Text("Swipe Right Action", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Choose what happens when you swipe right on a task.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val filterChipColors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )

                        SwipeAction.entries.forEach { action ->
                            val label = when(action) {
                                SwipeAction.COMPLETE -> "Complete"
                                SwipeAction.POSTPONE -> "Postpone"
                                SwipeAction.NONE -> "Do Nothing"
                            }
                            FilterChip(
                                selected = swipeRightAction == action,
                                onClick = { viewModel.setSwipeRightAction(action) },
                                label = { Text(label) },
                                colors = filterChipColors
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Archiving Section
            Column {
                Text("Archiving", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Move all completed tasks to your archive file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.archiveCompletedTasks() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = archiveFileUri != null
                ) {
                    Text("Archive Completed Tasks")
                }
                if (archiveFileUri == null) {
                    Text(
                        text = "Please select an archive file above first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            HorizontalDivider()
            Column {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                // Font Size
                Column {
                    Text("Task Font Size", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fontSizeText,
                        onValueChange = {
                            fontSizeText = it
                            it.toIntOrNull()?.let { size -> viewModel.setFontSize(size) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Checkboxes toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setShowCheckboxes(!showCheckboxes) }
                ) {
                    Checkbox(
                        checked = showCheckboxes,
                        onCheckedChange = { viewModel.setShowCheckboxes(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show completion boxes in list")
                }

                Spacer(Modifier.height(16.dp))

                // Double tap to complete toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setDoubleTapToComplete(!doubleTapToComplete) }
                ) {
                    Checkbox(
                        checked = doubleTapToComplete,
                        onCheckedChange = { viewModel.setDoubleTapToComplete(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Double tap task to complete")
                }

                Spacer(Modifier.height(16.dp))

                // Keep last tag toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setKeepLastTag(!keepLastTag) }
                ) {
                    Checkbox(
                        checked = keepLastTag,
                        onCheckedChange = { viewModel.setKeepLastTag(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Maintain 'last' tag for recurring tasks")
                }

                Spacer(Modifier.height(16.dp))

                // Add creation date toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setAddCreationDate(!addCreationDate) }
                ) {
                    Checkbox(
                        checked = addCreationDate,
                        onCheckedChange = { viewModel.setAddCreationDate(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Automatically add creation date to new tasks")
                }

                Spacer(Modifier.height(16.dp))

                // Theme
                Column {
                    Text("Theme", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val filterChipColors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )

                        AppTheme.entries.forEach { t ->
                            FilterChip(
                                selected = theme == t,
                                onClick = { viewModel.setTheme(t) },
                                label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                colors = filterChipColors
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Backup Section
            Column {
                Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Export or import your settings and saved filters to a JSON file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { exportLauncher.launch("vodotxt_config.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export Setup")
                    }
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import Setup")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GitHub Repository",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/fleapower/vodotxt")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showFolderPicker) {
        DriveFolderPicker(
            onFolderSelected = { id, name ->
                selectedFolderInfo = id to name
                showFolderPicker = false
                showConflictDialog = true
            },
            onDismiss = { showFolderPicker = false }
        )
    }

    if (showConflictDialog && selectedFolderInfo != null) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = { Text("Initial Sync") },
            text = { Text("How would you like to handle the first sync with this folder?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.performInitialSync(useRemote = true, selectedFolderInfo!!.first, selectedFolderInfo!!.second)
                    showConflictDialog = false
                }) {
                    Text("Use Drive Version")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.performInitialSync(useRemote = false, selectedFolderInfo!!.first, selectedFolderInfo!!.second)
                    showConflictDialog = false
                }) {
                    Text("Upload Local Version")
                }
            }
        )
    }

    if (showArchiveSuccess) {
        AlertDialog(
            onDismissRequest = { showArchiveSuccess = false },
            title = { Text("Success") },
            text = { Text("Successfully archived $archivedCount task(s) to your archive file.") },
            confirmButton = {
                TextButton(onClick = { showArchiveSuccess = false }) {
                    Text("OK")
                }
            }
        )
    }
}
