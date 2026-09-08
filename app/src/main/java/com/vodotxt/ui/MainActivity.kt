package com.vodotxt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.vodotxt.data.AppTheme
import com.vodotxt.data.FilterRepository
import com.vodotxt.data.SettingsRepository
import com.vodotxt.data.TodoRepository

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

enum class AppScreen { TODO_LIST, SETTINGS }

class MainActivity : ComponentActivity() {
    private val repository by lazy { TodoRepository.getInstance(applicationContext) }
    private val filterRepository by lazy { FilterRepository(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository.getInstance(applicationContext) }
    private val viewModel: TodoListViewModel by viewModels {
        TodoListViewModel.Factory(repository, filterRepository, settingsRepository, androidx.work.WorkManager.getInstance(applicationContext))
    }

    private val todoFilePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setTodoFileUri(it.toString())
        }
    }

    private val archiveFilePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setArchiveFileUri(it.toString())
        }
    }

    private val conflictFolderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setConflictFolderUri(it.toString())
        }
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.setDriveSyncEnabled(enabled = true)
        }
    }

    private val setupFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setupStorageFolder(applicationContext, it)
        }
    }

    private fun signInToGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE))
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.triggerRemoteCheck()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val themeSetting by viewModel.theme.collectAsState()
            var currentScreen by remember { mutableStateOf(AppScreen.TODO_LIST) }
            
            val darkTheme = when (themeSetting) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            val colorScheme = if (darkTheme) {
                darkColorScheme(
                    primary = Color(0xFF81C784),
                    secondary = Color(0xFF4CAF50),
                    tertiary = Color(0xFF388E3C),
                    onPrimary = Color.Black,
                    surface = Color(0xFF1C1B1F),
                    onSurface = Color.White
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF4CAF50),
                    secondary = Color(0xFF388E3C),
                    tertiary = Color(0xFF81C784),
                    onPrimary = Color.White,
                    surface = Color.White,
                    onSurface = Color.Black
                )
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    @Suppress("DEPRECATION")
                    window.statusBarColor = colorScheme.background.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                }
            }
            
            MaterialTheme(colorScheme = colorScheme) {
                val todoFileUri by viewModel.todoFileUri.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (todoFileUri == null) {
                        SetupScreen(onSelectFolder = { setupFolderLauncher.launch(null) })
                    } else {
                        when (currentScreen) {
                            AppScreen.TODO_LIST -> {
                                TodoScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = {
                                        currentScreen = AppScreen.SETTINGS
                                    }
                                )
                            }
                            AppScreen.SETTINGS -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onPickTodoFile = {
                                        todoFilePickerLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                                    },
                                    onPickArchiveFile = {
                                        archiveFilePickerLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                                    },
                                    onPickConflictFolder = {
                                        conflictFolderPickerLauncher.launch(null)
                                    },
                                    onSignIn = {
                                        signInToGoogle()
                                    },
                                    onBack = {
                                        currentScreen = AppScreen.TODO_LIST
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(onSelectFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Welcome to VoDo.txt",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "To get started, please choose a folder where your task files will be stored. This makes them accessible to other apps and for syncing.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onSelectFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Storage Folder")
        }
    }
}
