package com.vodotxt.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.vodotxt.data.AppTheme
import com.vodotxt.data.FilterRepository
import com.vodotxt.data.SettingsRepository
import com.vodotxt.data.TodoRepository
import java.time.LocalDate

class QuickAddActivity : ComponentActivity() {
    private val repository by lazy { TodoRepository.getInstance(applicationContext) }
    private val filterRepository by lazy { FilterRepository(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository.getInstance(applicationContext) }
    private val viewModel: TodoListViewModel by viewModels {
        TodoListViewModel.Factory(repository, filterRepository, settingsRepository, androidx.work.WorkManager.getInstance(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val themeSetting by viewModel.theme.collectAsState()
            val allTodos by viewModel.allTodos.collectAsState()
            val currentFilter by viewModel.currentFilter.collectAsState()
            val addCreationDate by viewModel.addCreationDate.collectAsState()

            val allProjects = remember(allTodos) {
                allTodos.asSequence().flatMap { it.projects }.distinct().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }).toList()
            }
            val allContexts = remember(allTodos) {
                allTodos.asSequence().flatMap { it.contexts }.distinct().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }).toList()
            }

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
                    onSurface = Color.White,
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF4CAF50),
                    secondary = Color(0xFF388E3C),
                    tertiary = Color(0xFF81C784),
                    onPrimary = Color.White,
                    surface = Color.White,
                    onSurface = Color.Black,
                )
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    @Suppress("DEPRECATION")
                    window.statusBarColor = Color.Transparent.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AddEditTodoDialog(
                            allProjects = allProjects,
                            allContexts = allContexts,
                            currentFilter = currentFilter,
                            initialCreationDate = if (addCreationDate) LocalDate.now() else null,
                            onDismiss = {
                                finish()
                            }
                        ) { item ->
                            viewModel.addTodo(item.rawLine)
                            finish()
                        }
                    }
                }
            }
        }
    }
}
