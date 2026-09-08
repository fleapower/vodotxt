package com.vodotxt.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.vodotxt.data.SwipeAction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vodotxt.data.TodoParser
import com.vodotxt.domain.DateFilterType
import com.vodotxt.domain.TodoFilter
import com.vodotxt.domain.TodoItem
import com.vodotxt.domain.CompletionFilter
import com.vodotxt.domain.SortMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoListViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val todos by viewModel.todos.collectAsState()
    val allTodos by viewModel.allTodos.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val savedFilters by viewModel.savedFilters.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val selectedSavedFilterId by viewModel.selectedSavedFilterId.collectAsState()
    val fontSizeSetting by viewModel.fontSize.collectAsState()
    val showCheckboxesSetting by viewModel.showCheckboxes.collectAsState()
    val doubleTapToCompleteSetting by viewModel.doubleTapToComplete.collectAsState()
    val swipeActionSetting by viewModel.swipeRightAction.collectAsState()
    val addCreationDate by viewModel.addCreationDate.collectAsState()
    val syncConflictFile by viewModel.syncConflictFile.collectAsState()
    val syncConflictTime by viewModel.syncConflictTime.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var isSearchVisible by rememberSaveable { mutableStateOf(value = false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            searchFocusRequester.requestFocus()
        }
    }

    val sortMode = currentFilter.sortMode
    val priorityFilter = currentFilter.selectedPriorities.firstOrNull().takeIf { currentFilter.selectedPriorities.size == 1 }

    var showAddDialog by rememberSaveable { mutableStateOf(value = false) }
    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var showFilterDialog by rememberSaveable { mutableStateOf(value = false) }
    var selectionMode by rememberSaveable { mutableStateOf(value = false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkPriorityDialog by rememberSaveable { mutableStateOf(value = false) }
    var showBulkDatePicker by rememberSaveable { mutableStateOf(value = false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(value = false) }
    var archivedCount by remember { mutableIntStateOf(0) }
    var showArchiveSuccess by remember { mutableStateOf(value = false) }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    LaunchedEffect(Unit) {
        viewModel.archiveEvent.collect { count ->
            archivedCount = count
            showArchiveSuccess = true
        }
    }

    val allProjects = remember(allTodos) {
        allTodos.asSequence().flatMap { it.projects }.distinct().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }).toList()
    }
    val allContexts = remember(allTodos) {
        allTodos.asSequence().flatMap { it.contexts }.distinct().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }).toList()
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Drag and Drop state
    val lazyListState = rememberLazyListState()
    val draggedItemId = remember { mutableStateOf<String?>(null) }
    var hasMovedDuringDrag by remember { mutableStateOf(value = false) }
    var lastPointerY by remember { mutableStateOf<Float?>(null) }
    
    // Absolute tracking to prevent jumping
    var dragStartPointerY by remember { mutableFloatStateOf(0f) }
    var dragInitialPointerOffsetWithinItem by remember { mutableFloatStateOf(0f) }

    // Auto-scroll logic
    LaunchedEffect(draggedItemId.value) {
        if (draggedItemId.value != null) {
            while (true) {
                val pointerY = lastPointerY
                if ((pointerY != null) && hasMovedDuringDrag) {
                    val threshold = 60f
                    val columnHeight = lazyListState.layoutInfo.viewportSize.height.toFloat()
                    if (columnHeight > 100f) {
                        var scrollDelta = 0f
                        // Only scroll if the finger is actually near the viewport edges and moving towards that edge
                        if ((pointerY < threshold) && (pointerY < dragStartPointerY - 5f)) {
                            scrollDelta = -10f // Slower, more controlled scroll
                        } else if ((pointerY > columnHeight - threshold) && (pointerY > dragStartPointerY + 5f)) {
                            scrollDelta = 10f
                        }
                        
                        if (scrollDelta != 0f) {
                            val canScrollUp = lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
                            val canScrollDown = lazyListState.canScrollForward
                            
                            if ((scrollDelta < 0 && canScrollUp) || (scrollDelta > 0 && canScrollDown)) {
                                lazyListState.scrollBy(scrollDelta)
                            }
                        }
                    }
                }
                delay(20.milliseconds) // Slightly slower update for stability
            }
        }
    }

    // Reorder/Swap logic
    var lastSwapTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(draggedItemId.value, lastPointerY, todos) {
        val id = draggedItemId.value ?: return@LaunchedEffect
        val pointerY = lastPointerY ?: return@LaunchedEffect
        if (sortMode != SortMode.FILE) return@LaunchedEffect
        
        // Prevent rapid-fire swaps (feedback loop)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSwapTime < 150) return@LaunchedEffect
        
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentIndex = todos.indexOfFirst { it.id == id }
        if (currentIndex == -1) return@LaunchedEffect
        
        val targetItem = visibleItems.firstOrNull { item ->
            // Header spacer is at index 0, tasks start at index 1
            val taskIndex = item.index - 1
            if (taskIndex < 0 || taskIndex >= todos.size) return@firstOrNull false
            if (item.key == id) return@firstOrNull false

            val itemCenter = item.offset + item.size / 2f
            val isNeighbor = taskIndex == currentIndex - 1 || taskIndex == currentIndex + 1
            if (!isNeighbor) return@firstOrNull false

            if (currentIndex < taskIndex) {
                pointerY > itemCenter
            } else {
                pointerY < itemCenter
            }
        }
        
        if (targetItem != null) {
            val taskIndex = targetItem.index - 1
            val isFirstVisible = targetItem.index == lazyListState.firstVisibleItemIndex
            val isMovingDown = taskIndex > currentIndex
            
            lastSwapTime = currentTime
            viewModel.moveTodo(id, targetItem.key as String)
            
            if (isFirstVisible && isMovingDown) {
                lazyListState.scrollBy(targetItem.size.toFloat())
            } else if (targetItem.index == lazyListState.firstVisibleItemIndex && !isMovingDown) {
                lazyListState.scrollBy(-targetItem.size.toFloat())
            }
        }
    }

    // Calculate current translation based on absolute pointer position (only in FILE sort mode)
    val dragTranslationY by remember {
        derivedStateOf {
            if (draggedItemId.value != null && lastPointerY != null && sortMode == SortMode.FILE) {
                val currentItem = lazyListState.layoutInfo.visibleItemsInfo.find { it.key == draggedItemId.value }
                if (currentItem != null) {
                    (lastPointerY!! - dragInitialPointerOffsetWithinItem) - currentItem.offset
                } else 0f
            } else 0f
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(modifier = Modifier.fillMaxHeight().width(300.dp)) {
                        FilterDrawerContent(
                            savedFilters = savedFilters,
                            activeFilterId = selectedSavedFilterId,
                            onSelectFilter = {
                                viewModel.setActiveFilter(it)
                                scope.launch { drawerState.close() }
                            },
                            onAddFilter = { showFilterDialog = true },
                            onDeleteFilter = { viewModel.deleteFilter(it) },
                            onReorder = { from, to -> viewModel.reorderFilters(from, to) },
                            allProjects = allProjects,
                            selectedProjects = currentFilter.selectedProjects.toSet(),
                            onToggleProject = { viewModel.toggleProject(it) },
                            allContexts = allContexts,
                            selectedContexts = currentFilter.selectedContexts.toSet(),
                            onToggleContext = { viewModel.toggleContext(it) },
                            invertProjects = currentFilter.invertProjects,
                            onToggleInvertProjects = viewModel::setInvertProjects,
                            invertContexts = currentFilter.invertContexts,
                            onToggleInvertContexts = viewModel::setInvertContexts,
                            onClearAll = viewModel::clearAllFilters,
                        )
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { 
                                if (isSearchVisible) {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        placeholder = { Text("Search tasks...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(searchFocusRequester),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent,
                                        ),
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(onClick = { 
                                                viewModel.setSearchQuery("")
                                                isSearchVisible = false 
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "Close Search")
                                            }
                                        }
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val activeFilterName = savedFilters.find { it.id == selectedSavedFilterId }?.name
                                        Text(
                                            text = if (selectionMode) {
                                                "${selectedIds.size} Selected"
                                            } else {
                                                if (activeFilterName != null) "VoDo.txt - $activeFilterName" else "VoDo.txt"
                                            },
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (activeFilterName != null && !selectionMode) {
                                            IconButton(
                                                onClick = { viewModel.setActiveFilter(null) },
                                                modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Clear Filter",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (isSyncing) {
                                            Spacer(Modifier.width(8.dp))
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            },
                            actions = {
                                if (!isSearchVisible) {
                                    Text(
                                        text = todos.size.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(onClick = { isSearchVisible = true }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        BottomAppBar(
                            modifier = Modifier.imePadding(),
                            actions = {
                                IconButton(onClick = onNavigateToSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }

                                Spacer(Modifier.weight(1f))
                                
                                Row(
                                    modifier = Modifier.fillMaxHeight().horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectionMode) {
                                        IconButton(onClick = { 
                                            showDeleteConfirmation = true
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Bulk Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                        IconButton(onClick = { 
                                            selectedIds = todos.asSequence().map { it.id }.toSet()
                                        }) {
                                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                                        }
                                        IconButton(onClick = { showBulkPriorityDialog = true }) {
                                            Icon(Icons.Default.Flag, contentDescription = "Bulk Priority")
                                        }
                                        IconButton(onClick = { showBulkDatePicker = true }) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = "Bulk Change Date")
                                        }
                                        IconButton(onClick = { 
                                            selectionMode = false
                                            selectedIds = emptySet()
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                                        }
                                        IconButton(onClick = { 
                                            viewModel.toggleTodosCompletion(selectedIds)
                                            selectionMode = false
                                            selectedIds = emptySet()
                                        }) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Bulk Toggle Complete")
                                        }
                                    } else {
                                        IconButton(onClick = { viewModel.cycleCompletionFilter() }) {
                                            val (icon, color) = when (currentFilter.completionFilter ?: CompletionFilter.ALL) {
                                                CompletionFilter.ALL -> Icons.Default.Visibility to Color(0xFF4CAF50)
                                                CompletionFilter.INCOMPLETE -> Icons.Default.VisibilityOff to LocalContentColor.current
                                                CompletionFilter.COMPLETED -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = "Cycle Completion Filter",
                                                tint = color
                                            )
                                        }

                                        IconButton(onClick = { viewModel.cycleDateFilter() }) {
                                            val color = if (currentFilter.dateFilter == DateFilterType.ALL) 
                                                LocalContentColor.current 
                                            else 
                                                Color(0xFF4CAF50)

                                            Box(contentAlignment = Alignment.Center) {
                                                when (currentFilter.dateFilter) {
                                                    DateFilterType.TODAY -> {
                                                        Icon(Icons.Default.Today, contentDescription = "Today", tint = color)
                                                    }
                                                    DateFilterType.TOMORROW -> {
                                                        // Calendar icon with a small arrow overlay
                                                        Icon(Icons.Default.Event, contentDescription = "Tomorrow", tint = color)
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                            contentDescription = null,
                                                            tint = color,
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .offset(x = 6.dp, y = 6.dp)
                                                                .background(MaterialTheme.colorScheme.surface, shape = androidx.compose.foundation.shape.CircleShape)
                                                        )
                                                    }
                                                    else -> {
                                                        Icon(Icons.Default.CalendarMonth, contentDescription = "All", tint = color)
                                                    }
                                                }
                                            }
                                        }

                                        IconButton(onClick = { viewModel.cyclePriorityFilter() }) {
                                            val color = when (priorityFilter) {
                                                'A' -> Color(0xFFF44336)
                                                'B' -> Color(0xFFFBC02D)
                                                'C' -> Color(0xFF4CAF50)
                                                'D' -> Color(0xFF9C27B0)
                                                else -> Color.Gray
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(color, shape = androidx.compose.foundation.shape.CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = priorityFilter?.toString() ?: "",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        IconButton(onClick = { 
                                            val nextMode = when (sortMode) {
                                                SortMode.FILE -> SortMode.DUE_DATE
                                                SortMode.DUE_DATE -> SortMode.PRIORITY
                                                SortMode.PRIORITY -> SortMode.FILE
                                            }
                                            viewModel.setSortMode(nextMode)
                                        }) {
                                            Icon(
                                                imageVector = when (sortMode) {
                                                    SortMode.FILE -> Icons.Default.SwapVert
                                                    SortMode.DUE_DATE -> Icons.Default.Event
                                                    SortMode.PRIORITY -> Icons.Default.Flag
                                                }, 
                                                contentDescription = "Sort Mode: ${sortMode.name}"
                                            )
                                        }
                                    }
                                }
                            },
                            floatingActionButton = {
                                if (!selectionMode) {
                                    FloatingActionButton(
                                        onClick = { showAddDialog = true }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Task")
                                    }
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding)) {
                        PullToRefreshBox(
                            isRefreshing = isSyncing,
                            onRefresh = { viewModel.triggerRemoteCheck() },
                            modifier = Modifier.fillMaxSize(),
                            indicator = {}
                        ) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                item(key = "task_list_top_buffer") {
                                    Spacer(Modifier.height(2.dp))
                                }

                                itemsIndexed(todos, key = { _, it -> it.id }) { _, todo ->
                                    val isDragging = todo.id == draggedItemId.value
                                    val isSelected = selectedIds.contains(todo.id)
                                    
                                    val currentTodo by rememberUpdatedState(todo)
                                    val offsetX = remember { Animatable(0f) }
                                    val scope = rememberCoroutineScope()
                                    
                                    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                                    val threshold = with(LocalDensity.current) { (screenWidth / 2).toPx() }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (!isDragging) Modifier.animateItem() else Modifier)
                                            .zIndex(if (isDragging) 10f else 0f)
                                            .graphicsLayer {
                                                translationY = if (isDragging) dragTranslationY else 0f
                                                scaleX = if (isDragging) 1.05f else 1f
                                                scaleY = if (isDragging) 1.05f else 1f
                                                alpha = if (isDragging) 0.9f else 1f
                                            }
                                            .pointerInput(todo.id, sortMode) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        val item = lazyListState.layoutInfo.visibleItemsInfo.find { it.key == todo.id }
                                                        if (item != null) {
                                                            draggedItemId.value = todo.id
                                                            dragInitialPointerOffsetWithinItem = offset.y
                                                            dragStartPointerY = item.offset.toFloat() + offset.y
                                                            lastPointerY = item.offset.toFloat() + offset.y
                                                            hasMovedDuringDrag = false
                                                        }
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (sortMode == SortMode.FILE) {
                                                            lastPointerY = (lastPointerY ?: 0f) + dragAmount.y
                                                        }
                                                        if (abs(dragAmount.y) > 2f) {
                                                            hasMovedDuringDrag = true
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        val id = draggedItemId.value
                                                        if (id != null) {
                                                            if (!hasMovedDuringDrag) {
                                                                // Selection only happens if released without dragging
                                                                if (!selectionMode) {
                                                                    selectionMode = true
                                                                    selectedIds = setOf(id)
                                                                }
                                                            } else if (sortMode == SortMode.FILE) {
                                                                viewModel.requestSync()
                                                            }
                                                        }
                                                        draggedItemId.value = null
                                                        lastPointerY = null
                                                    },
                                                    onDragCancel = {
                                                        draggedItemId.value = null
                                                        lastPointerY = null
                                                    }
                                                )
                                            }
                                            .pointerInput(todo, selectionMode, doubleTapToCompleteSetting, swipeActionSetting) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val down = awaitFirstDown()
                                                        
                                                        // 1. Wait for movement exceeding touch slop OR a release
                                                        var finalAction: String? = null
                                                        do {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.first()
                                                            val dX = change.position.x - down.position.x
                                                            val dY = change.position.y - down.position.y
                                                            
                                                            if (sqrt(dX * dX + dY * dY) > viewConfiguration.touchSlop) {
                                                                // Angle check: only trigger if it's a right swipe (horizontal-ish)
                                                                if (dX > 0 && abs(dY) < dX * 0.577f) {
                                                                    finalAction = "SWIPE"
                                                                } else {
                                                                    // User swiped left/up/down: NOT a task action
                                                                    finalAction = "CANCEL"
                                                                }
                                                                break
                                                            }
                                                        } while (event.changes.any { it.pressed })

                                                        if (finalAction == null) {
                                                            // Finger was released before touch slop: it's a TAP
                                                            finalAction = "TAP"
                                                        }

                                                        when (finalAction) {
                                                            "SWIPE" -> {
                                                                horizontalDrag(down.id) { change ->
                                                                    val newOffset = (offsetX.value + change.positionChange().x).coerceAtLeast(0f)
                                                                    scope.launch { offsetX.snapTo(newOffset) }
                                                                    change.consume()
                                                                }
                                                                
                                                                if (offsetX.value > threshold) {
                                                                    when (swipeActionSetting) {
                                                                        SwipeAction.COMPLETE -> viewModel.toggleTodo(currentTodo)
                                                                        SwipeAction.POSTPONE -> viewModel.postponeTodo(currentTodo)
                                                                        SwipeAction.NONE -> {}
                                                                    }
                                                                }
                                                                scope.launch { offsetX.animateTo(0f) }
                                                            }
                                                            "TAP" -> {
                                                                if (selectionMode) {
                                                                    val isSelectedNow = selectedIds.contains(todo.id)
                                                                    selectedIds = if (isSelectedNow) selectedIds - todo.id else selectedIds + todo.id
                                                                    if (selectedIds.isEmpty()) selectionMode = false
                                                                } else {
                                                                    // Check for double tap
                                                                    val secondTap = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                                                        awaitFirstDown()
                                                                    }
                                                                    if (secondTap != null && doubleTapToCompleteSetting) {
                                                                        viewModel.toggleTodo(todo)
                                                                    } else {
                                                                        editingTodo = todo
                                                                    }
                                                                }
                                                            }
                                                            "CANCEL" -> {
                                                                // Swipe was in wrong direction (left/up/down): do nothing
                                                                // This allows the list to scroll or drawer to open normally
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        // Action Background
                                        if (offsetX.value > 0 && swipeActionSetting != SwipeAction.NONE) {
                                            val color = if (swipeActionSetting == SwipeAction.COMPLETE) Color(0xFF4CAF50) else Color(0xFF2196F3)
                                            val icon = if (swipeActionSetting == SwipeAction.COMPLETE) Icons.Default.Check else Icons.Default.Schedule
                                            val label = if (swipeActionSetting == SwipeAction.COMPLETE) "Complete" else "Postpone"

                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(color, shape = CardDefaults.shape)
                                                    .padding(start = 16.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(icon, contentDescription = null, tint = Color.White)
                                                    if (offsetX.value > threshold) {
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                }
                                            }
                                        }

                                        TodoRow(
                                            todo = todo,
                                            fontSize = fontSizeSetting,
                                            showCheckbox = showCheckboxesSetting,
                                            onToggle = {
                                                if (selectionMode) {
                                                    val newSelected = if (isSelected) selectedIds - todo.id else selectedIds + todo.id
                                                    selectedIds = newSelected
                                                    if (newSelected.isEmpty()) {
                                                        selectionMode = false
                                                    }
                                                } else {
                                                    viewModel.toggleTodo(todo)
                                                }
                                            },
                                            modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) },
                                            isSelected = isSelected && selectionMode
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddEditTodoDialog(
            allProjects = allProjects,
            allContexts = allContexts,
            currentFilter = currentFilter,
            initialCreationDate = if (addCreationDate) LocalDate.now() else null,
            onDismiss = { showAddDialog = false },
            onConfirm = { item ->
                viewModel.addTodo(item.rawLine)
                showAddDialog = false
            }
        )
    }

    if (editingTodo != null) {
        AddEditTodoDialog(
            initialTodo = editingTodo,
            allProjects = allProjects,
            allContexts = allContexts,
            currentFilter = currentFilter,
            initialCreationDate = null,
            onDismiss = { editingTodo = null },
            onConfirm = { updatedItem ->
                viewModel.updateTodo(updatedItem)
                editingTodo = null
            }
        )
    }

    if (showFilterDialog) {
        CreateFilterDialog(
            initialFilter = currentFilter,
            allProjects = allProjects,
            allContexts = allContexts,
            onDismiss = { showFilterDialog = false },
            onApply = { filter ->
                viewModel.setActiveFilter(filter)
                showFilterDialog = false
                scope.launch { drawerState.close() }
            },
            onSave = { filter ->
                viewModel.saveFilter(filter)
                showFilterDialog = false
            }
        )
    }

    if (showBulkDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showBulkDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        viewModel.updateTodosDate(selectedIds, dateStr)
                        showBulkDatePicker = false
                        selectionMode = false
                        selectedIds = emptySet()
                    }
                }, enabled = datePickerState.selectedDateMillis != null) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.updateTodosDate(selectedIds, null)
                    showBulkDatePicker = false
                    selectionMode = false
                    selectedIds = emptySet()
                }) {
                    Text("Clear Date")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showBulkPriorityDialog) {
        var selectedPriority by remember { mutableStateOf<Char?>(null) }
        AlertDialog(
            onDismissRequest = { showBulkPriorityDialog = false },
            title = { Text("Set Priority") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(null, 'A', 'B', 'C', 'D').forEach { p ->
                        val color = when (p) {
                            'A' -> Color(0xFFF44336)
                            'B' -> Color(0xFFFBC02D)
                            'C' -> Color(0xFF4CAF50)
                            'D' -> Color(0xFF9C27B0)
                            else -> Color.Gray
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selectedPriority = p }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .graphicsLayer {
                                        shape = androidx.compose.foundation.shape.CircleShape
                                        clip = true
                                    }
                                    .background(if (selectedPriority == p) color else color.copy(alpha = 0.2f))
                                    .border(
                                        width = 2.dp,
                                        color = color,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = p?.toString() ?: "None",
                                    color = if (selectedPriority == p) Color.White else color,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTodosPriority(selectedIds, selectedPriority)
                    showBulkPriorityDialog = false
                    selectionMode = false
                    selectedIds = emptySet()
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkPriorityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Tasks") },
            text = { Text("Are you sure you want to delete ${selectedIds.size} task(s)? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTodos(selectedIds)
                        showDeleteConfirmation = false
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
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

    if (syncConflictFile != null && syncConflictTime != null) {
        AlertDialog(
            onDismissRequest = { /* Force user to choose */ },
            title = { Text("Sync Conflict Detected") },
            text = { 
                Text("A conflict occurred with \"$syncConflictFile\" on $syncConflictTime. " +
                     "A local backup has been created in your conflicts folder. " +
                     "Which version should be used to continue syncing?")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveConflict(useRemote = true) }) {
                    Text("Use Cloud Version")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveConflict(useRemote = false) }) {
                    Text("Use Local Version")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterDrawerContent(
    savedFilters: List<TodoFilter>,
    activeFilterId: String?,
    onSelectFilter: (TodoFilter?) -> Unit,
    onAddFilter: () -> Unit,
    onDeleteFilter: (TodoFilter) -> Unit,
    onReorder: (Int, Int) -> Unit,
    allProjects: List<String>,
    selectedProjects: Set<String>,
    onToggleProject: (String) -> Unit,
    allContexts: List<String>,
    selectedContexts: Set<String>,
    onToggleContext: (String) -> Unit,
    invertProjects: Boolean,
    onToggleInvertProjects: (Boolean) -> Unit,
    invertContexts: Boolean,
    onToggleInvertContexts: (Boolean) -> Unit,
    onClearAll: () -> Unit
) {
    var filterToDelete by remember { mutableStateOf<TodoFilter?>(null) }
    
    val lazyListState = rememberLazyListState()
    val draggedFilterId = remember { mutableStateOf<String?>(null) }
    var lastPointerY by remember { mutableStateOf<Float?>(null) }
    var hasMovedDuringDrag by remember { mutableStateOf(value = false) }
    
    var dragStartPointerY by remember { mutableFloatStateOf(0f) }
    var dragInitialPointerOffsetWithinItem by remember { mutableFloatStateOf(0f) }

    // Auto-scroll logic for filters
    LaunchedEffect(draggedFilterId.value) {
        if (draggedFilterId.value != null) {
            while (true) {
                val pointerY = lastPointerY
                if ((pointerY != null) && hasMovedDuringDrag) {
                    val threshold = 60f
                    val viewportHeight = lazyListState.layoutInfo.viewportSize.height.toFloat()
                    if (viewportHeight > 100f) {
                        var scrollDelta = 0f
                        if (pointerY < threshold && pointerY < dragStartPointerY - 5f) {
                            scrollDelta = -10f
                        } else if (pointerY > viewportHeight - threshold && pointerY > dragStartPointerY + 5f) {
                            scrollDelta = 10f
                        }
                        
                        if (scrollDelta != 0f) {
                            val canScrollUp = lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
                            val canScrollDown = lazyListState.canScrollForward
                            if ((scrollDelta < 0 && canScrollUp) || (scrollDelta > 0 && canScrollDown)) {
                                lazyListState.scrollBy(scrollDelta)
                            }
                        }
                    }
                }
                delay(20.milliseconds)
            }
        }
    }

    // Reorder/Swap logic for filters
    var lastSwapTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(draggedFilterId.value, lastPointerY, savedFilters) {
        val id = draggedFilterId.value ?: return@LaunchedEffect
        val pointerY = lastPointerY ?: return@LaunchedEffect
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSwapTime < 150) return@LaunchedEffect
        
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentIndex = savedFilters.indexOfFirst { it.id == id }
        if (currentIndex == -1) return@LaunchedEffect
        
        val targetItem = visibleItems.firstOrNull { item ->
            // In savedFilters list, we need to adjust index because of the "All Tasks" header item
            // Header is at index 0 in the LazyColumn, filters start at index 1
            val filterIndex = item.index - 1
            if (filterIndex < 0 || filterIndex >= savedFilters.size) return@firstOrNull false
            if (item.key == id) return@firstOrNull false

            val itemCenter = item.offset + item.size / 2f
            val isNeighbor = filterIndex == currentIndex - 1 || filterIndex == currentIndex + 1
            if (!isNeighbor) return@firstOrNull false

            if (currentIndex < filterIndex) {
                pointerY > itemCenter
            } else {
                pointerY < itemCenter
            }
        }
        
        if (targetItem != null) {
            val filterIndex = targetItem.index - 1
            val isFirstVisible = targetItem.index == lazyListState.firstVisibleItemIndex
            val isMovingDown = filterIndex > currentIndex
            
            lastSwapTime = currentTime
            onReorder(currentIndex, filterIndex)
            
            if (isFirstVisible && isMovingDown) {
                lazyListState.scrollBy(targetItem.size.toFloat())
            } else if (targetItem.index == lazyListState.firstVisibleItemIndex && !isMovingDown) {
                lazyListState.scrollBy(-targetItem.size.toFloat())
            }
        }
    }

    val dragTranslationY by remember {
        derivedStateOf {
            if (draggedFilterId.value != null && lastPointerY != null) {
                val currentItem = lazyListState.layoutInfo.visibleItemsInfo.find { it.key == draggedFilterId.value }
                if (currentItem != null) {
                    (lastPointerY!! - dragInitialPointerOffsetWithinItem) - currentItem.offset
                } else 0f
            } else 0f
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxHeight()) {
        Text("Saved Filters", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f)
        ) {
            item(key = "header_all") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (activeFilterId == null) 
                        MaterialTheme.colorScheme.secondaryContainer 
                    else 
                        Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable { onSelectFilter(null) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = if (activeFilterId == null)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Slightly less spacing to keep it compact
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All Tasks",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = if (activeFilterId == null)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
            }

            itemsIndexed(savedFilters, key = { _, f -> f.id }) { _, filter ->
                val isDragging = filter.id == draggedFilterId.value
                
                // Using a custom Row instead of NavigationDrawerItem to allow combinedClickable
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (activeFilterId == filter.id) 
                        MaterialTheme.colorScheme.secondaryContainer 
                    else 
                        Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .then(if (!isDragging) Modifier.animateItem() else Modifier)
                        .zIndex(if (isDragging) 10f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragTranslationY else 0f
                            scaleX = if (isDragging) 1.05f else 1f
                            scaleY = if (isDragging) 1.05f else 1f
                            alpha = if (isDragging) 0.9f else 1f
                        }
                        .combinedClickable(
                            onClick = { onSelectFilter(filter) },
                            onLongClick = { 
                                filterToDelete = filter 
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = filter.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = if (activeFilterId == filter.id)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(24.dp)
                                .pointerInput(filter.id) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val item = lazyListState.layoutInfo.visibleItemsInfo
                                                .find { it.key == filter.id }
                                            if (item != null) {
                                                draggedFilterId.value = filter.id
                                                dragInitialPointerOffsetWithinItem = offset.y
                                                dragStartPointerY = item.offset.toFloat() + offset.y
                                                lastPointerY = item.offset.toFloat() + offset.y
                                                hasMovedDuringDrag = false
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            lastPointerY = (lastPointerY ?: 0f) + dragAmount.y
                                            hasMovedDuringDrag = true
                                        },
                                        onDragEnd = {
                                            draggedFilterId.value = null
                                            lastPointerY = null
                                        },
                                        onDragCancel = {
                                            draggedFilterId.value = null
                                            lastPointerY = null
                                        }
                                    )
                                }
                        )
                    }
                }
            }

            item(key = "footer_add") {
                Button(
                    onClick = onAddFilter,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Filter")
                }
                
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ad-hoc Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (selectedProjects.isNotEmpty() || selectedContexts.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Contexts", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(40.dp).clickable { onToggleInvertContexts(!invertContexts) }
                ) {
                    Checkbox(checked = invertContexts, onCheckedChange = onToggleInvertContexts)
                    Text("Invert Contexts", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontStyle = FontStyle.Italic)
                }
            }

            itemsIndexed(allContexts, key = { _, c -> "ctx_$c" }) { _, context ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(40.dp).clickable { onToggleContext(context) }
                ) {
                    Checkbox(
                        checked = selectedContexts.contains(context),
                        onCheckedChange = { onToggleContext(context) }
                    )
                    Text(context, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Projects", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(40.dp).clickable { onToggleInvertProjects(!invertProjects) }
                ) {
                    Checkbox(checked = invertProjects, onCheckedChange = onToggleInvertProjects)
                    Text("Invert Projects", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontStyle = FontStyle.Italic)
                }
            }

            itemsIndexed(allProjects, key = { _, p -> "proj_$p" }) { _, project ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(40.dp).clickable { onToggleProject(project) }
                ) {
                    Checkbox(
                        checked = selectedProjects.contains(project),
                        onCheckedChange = { onToggleProject(project) }
                    )
                    Text(project, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp))
                }
            }
        }
    }

    if (filterToDelete != null) {
        AlertDialog(
            onDismissRequest = { filterToDelete = null },
            title = { Text("Delete Filter") },
            text = { Text("Are you sure you want to delete the filter \"${filterToDelete?.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        filterToDelete?.let { onDeleteFilter(it) }
                        filterToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { filterToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateFilterDialog(
    initialFilter: TodoFilter,
    allProjects: List<String>,
    allContexts: List<String>,
    onDismiss: () -> Unit,
    onApply: (TodoFilter) -> Unit,
    onSave: (TodoFilter) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(initialFilter.dateFilter) }
    var customDate by remember { mutableStateOf(initialFilter.customDate) }
    var selectedProjects by remember { mutableStateOf(initialFilter.selectedProjects.toSet()) }
    var selectedContexts by remember { mutableStateOf(initialFilter.selectedContexts.toSet()) }
    var selectedPriorities by remember { mutableStateOf(initialFilter.selectedPriorities.toSet()) }
    var selectedSortMode by remember { mutableStateOf(initialFilter.sortMode) }
    var completionFilter by remember { mutableStateOf(initialFilter.completionFilter ?: CompletionFilter.ALL) }
    var invertProjects by remember { mutableStateOf(initialFilter.invertProjects) }
    var invertContexts by remember { mutableStateOf(initialFilter.invertContexts) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    
    val filterChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Color(0xFF4CAF50),
        selectedLabelColor = Color.White
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Button Row at the Top
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Row {
                        TextButton(onClick = {
                            onApply(TodoFilter(
                                name = name.ifBlank { "Applied Filter" },
                                dateFilter = dateFilter,
                                customDate = customDate,
                                selectedProjects = selectedProjects.toList(),
                                selectedContexts = selectedContexts.toList(),
                                selectedPriorities = selectedPriorities.toList(),
                                completionFilter = completionFilter,
                                invertProjects = invertProjects,
                                invertContexts = invertContexts,
                                sortMode = selectedSortMode
                            ))
                        }) {
                            Text("Apply")
                        }
                        TextButton(onClick = {
                            if (name.isNotBlank()) {
                                onSave(TodoFilter(
                                    name = name,
                                    dateFilter = dateFilter,
                                    customDate = customDate,
                                    selectedProjects = selectedProjects.toList(),
                                    selectedContexts = selectedContexts.toList(),
                                    selectedPriorities = selectedPriorities.toList(),
                                    completionFilter = completionFilter,
                                    invertProjects = invertProjects,
                                    invertContexts = invertContexts,
                                    sortMode = selectedSortMode
                                ))
                            } else {
                                Toast.makeText(context, "Filter must be given a name before saving.", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Save")
                        }
                    }
                }

                // Title on its own line
                Text(
                    text = "Create Filter",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Filter Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Sort Order", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedSortMode == SortMode.FILE,
                        onClick = { selectedSortMode = SortMode.FILE },
                        label = { Text("File") },
                        colors = filterChipColors
                    )
                    FilterChip(
                        selected = selectedSortMode == SortMode.DUE_DATE,
                        onClick = { selectedSortMode = SortMode.DUE_DATE },
                        label = { Text("Date") },
                        colors = filterChipColors
                    )
                    FilterChip(
                        selected = selectedSortMode == SortMode.PRIORITY,
                        onClick = { selectedSortMode = SortMode.PRIORITY },
                        label = { Text("Priority") },
                        colors = filterChipColors
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Completion Status", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompletionFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = completionFilter == filter,
                            onClick = { completionFilter = filter },
                            label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = filterChipColors
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Priorities", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf('A', 'B', 'C', 'D').forEach { p ->
                        val color = when (p) {
                            'A' -> Color(0xFFF44336)
                            'B' -> Color(0xFFFBC02D)
                            'C' -> Color(0xFF4CAF50)
                            'D' -> Color(0xFF9C27B0)
                            else -> Color.Gray
                        }
                        val isSelected = selectedPriorities.contains(p)
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { 
                                selectedPriorities = if (isSelected) selectedPriorities - p else selectedPriorities + p
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .graphicsLayer {
                                        shape = androidx.compose.foundation.shape.CircleShape
                                        clip = true
                                    }
                                    .background(if (isSelected) color else color.copy(alpha = 0.2f))
                                    .border(
                                        width = 2.dp,
                                        color = color,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = p.toString(),
                                    color = if (isSelected) Color.White else color,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Text("Due Date Filter", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dateFilter == DateFilterType.ALL,
                        onClick = { dateFilter = DateFilterType.ALL },
                        label = { Text("All") },
                        colors = filterChipColors
                    )
                    FilterChip(
                        selected = dateFilter == DateFilterType.TODAY,
                        onClick = { dateFilter = DateFilterType.TODAY },
                        label = { Text("Today") },
                        colors = filterChipColors
                    )
                    FilterChip(
                        selected = dateFilter == DateFilterType.TOMORROW,
                        onClick = { dateFilter = DateFilterType.TOMORROW },
                        label = { Text("Tomorrow") },
                        colors = filterChipColors
                    )
                }
                FilterChip(
                    selected = dateFilter == DateFilterType.CUSTOM,
                    onClick = { showDatePicker = true },
                    label = { Text(if (customDate != null) "Date: $customDate" else "Custom Date") },
                    colors = filterChipColors
                )

                Text("Contexts", style = MaterialTheme.typography.labelLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { invertContexts = !invertContexts }
                ) {
                    Checkbox(checked = invertContexts, onCheckedChange = { invertContexts = it })
                    Text("Invert Contexts (Exclude selected)", style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    allContexts.forEach { context ->
                        FilterChip(
                            selected = selectedContexts.contains(context),
                            onClick = {
                                selectedContexts = if (selectedContexts.contains(context)) {
                                    selectedContexts - context
                                } else {
                                    selectedContexts + context
                                }
                            },
                            label = { Text(context) },
                            colors = filterChipColors
                        )
                    }
                }

                Text("Projects", style = MaterialTheme.typography.labelLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { invertProjects = !invertProjects }
                ) {
                    Checkbox(checked = invertProjects, onCheckedChange = { invertProjects = it })
                    Text("Invert Projects (Exclude selected)", style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    allProjects.forEach { project ->
                        FilterChip(
                            selected = selectedProjects.contains(project),
                            onClick = {
                                selectedProjects = if (selectedProjects.contains(project)) {
                                    selectedProjects - project
                                } else {
                                    selectedProjects + project
                                }
                            },
                            label = { Text(project) },
                            colors = filterChipColors
                        )
                    }
                }
                
                // Extra padding at the bottom ensures the dropdown menus
                // have enough room to expand even if the list is long
                // or if the keyboard is partially covering the dialog.
                Spacer(Modifier.height(120.dp))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        customDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        dateFilter = DateFilterType.CUSTOM
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun TodoRow(
    todo: TodoItem,
    fontSize: Int,
    showCheckbox: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) {
                Checkbox(
                    checked = todo.completed,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column {
                val priorityColor = when (todo.priority) {
                    'A' -> Color(0xFFF44336)
                    'B' -> Color(0xFFFBC02D)
                    'C' -> Color(0xFF4CAF50)
                    'D' -> Color(0xFF9C27B0)
                    else -> MaterialTheme.colorScheme.onSurface
                }

                val urlRegex = Regex("(https?://\\S+)")
                val emailRegex = Regex("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})")
                val phoneRegex = Regex("(\\+?\\d{1,3}[\\s.-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}")

                val urlMatch = remember(todo.description) { urlRegex.find(todo.description) }
                val emailMatch = remember(todo.description) { emailRegex.find(todo.description) }
                val phoneMatch = remember(todo.description) { phoneRegex.find(todo.description) }

                val annotatedText = remember(todo, priorityColor, fontSize) {
                    buildAnnotatedString {
                        if (todo.completed) {
                            append("x ")
                            if (todo.completionDate != null) {
                                withStyle(style = SpanStyle(fontSize = (fontSize - 4).sp, color = Color.Gray)) {
                                    append("${todo.completionDate} ")
                                }
                            }
                        }
                        if (todo.priority != null) {
                            withStyle(style = SpanStyle(color = priorityColor)) {
                                append("(${todo.priority}) ")
                            }
                        }
                        if (todo.creationDate != null) {
                            withStyle(style = SpanStyle(fontSize = (fontSize - 2).sp, color = Color.Gray)) {
                                append("${todo.creationDate} ")
                            }
                        }

                        append(todo.description)
                        
                        withStyle(style = SpanStyle(fontSize = (fontSize - 2).sp)) {
                            todo.projects.forEach { append(" +$it") }
                            todo.contexts.forEach { append(" @$it") }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = annotatedText,
                        textDecoration = if (todo.completed) TextDecoration.LineThrough else null,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.2).sp,
                            color = if (todo.completed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Action Icons Row - Pinned to right of description
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val iconTint = if (todo.completed) 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) 
                        else 
                            MaterialTheme.colorScheme.onSurface

                        if (urlMatch != null) {
                            IconButton(
                                onClick = { uriHandler.openUri(urlMatch.value) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Open Link",
                                    modifier = Modifier.size(24.dp),
                                    tint = iconTint
                                )
                            }
                        }
                        if (emailMatch != null) {
                            IconButton(
                                onClick = { uriHandler.openUri("mailto:${emailMatch.value}") },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Send Email",
                                    modifier = Modifier.size(24.dp),
                                    tint = iconTint
                                )
                            }
                        }
                        if (phoneMatch != null) {
                            IconButton(
                                onClick = { 
                                    val digits = phoneMatch.value.replace(Regex("[^0-9+]"), "")
                                    uriHandler.openUri("tel:$digits") 
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Call Number",
                                    modifier = Modifier.size(24.dp),
                                    tint = iconTint
                                )
                            }
                        }
                    }
                }

                if (todo.metadata.containsKey("due") || todo.metadata.containsKey("last") || todo.metadata.containsKey("r")) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val recurrence = todo.metadata["r"]
                        if (recurrence != null) {
                            Text(
                                text = "r:$recurrence",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = (fontSize - 2).sp,
                                    lineHeight = (fontSize - 2).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (todo.metadata.containsKey("last")) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }

                        val lastDate = todo.metadata["last"]
                        lastDate?.let {
                            Text(
                                text = "last: $it",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = (fontSize - 2).sp,
                                    lineHeight = (fontSize - 2).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        val dueDate = todo.metadata["due"]
                        if (dueDate != null) {
                            val parsedDate = TodoParser.safeParseDate(dueDate)
                            val today = LocalDate.now()
                            val dateColor = when {
                                parsedDate == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                parsedDate.isBefore(today) -> MaterialTheme.colorScheme.error
                                parsedDate.isEqual(today) -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = "due: $dueDate",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = (fontSize - 2).sp,
                                    lineHeight = (fontSize - 2).sp
                                ),
                                color = dateColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTodoDialog(
    initialTodo: TodoItem? = null,
    allProjects: List<String> = emptyList(),
    allContexts: List<String> = emptyList(),
    currentFilter: TodoFilter = TodoFilter(name = "Default"),
    initialCreationDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onConfirm: (TodoItem) -> Unit
) {
    var description by remember(initialTodo) { mutableStateOf(initialTodo?.description ?: "") }
    var selectedPriority by remember(initialTodo) { 
        mutableStateOf(initialTodo?.priority ?: currentFilter.selectedPriorities.firstOrNull()) 
    }
    var projects by remember(initialTodo) { 
        mutableStateOf(initialTodo?.projects?.joinToString(" ") ?: currentFilter.selectedProjects.joinToString(" ")) 
    }
    var contexts by remember(initialTodo) { 
        mutableStateOf(initialTodo?.contexts?.joinToString(" ") ?: currentFilter.selectedContexts.joinToString(" ")) 
    }
    var dueDate by remember(initialTodo) { 
        val filterDate = if (initialTodo == null) {
            when (currentFilter.dateFilter) {
                DateFilterType.TODAY -> LocalDate.now().toString()
                DateFilterType.TOMORROW -> LocalDate.now().plusDays(1).toString()
                DateFilterType.CUSTOM -> currentFilter.customDate?.toString() ?: ""
                else -> ""
            }
        } else ""
        mutableStateOf(initialTodo?.metadata?.get("due") ?: filterDate) 
    }
    var recurrence by remember(initialTodo) { mutableStateOf(initialTodo?.metadata?.get("r") ?: "") }
    var metadata by remember(initialTodo) { 
        mutableStateOf<Map<String, String>>(initialTodo?.metadata ?: if (dueDate.isNotBlank()) mapOf("due" to dueDate) else emptyMap()) 
    }
    var completed by remember(initialTodo) { mutableStateOf(initialTodo?.completed ?: false) }
    var completionDate by remember(initialTodo) { mutableStateOf(initialTodo?.completionDate) }
    var creationDate by remember(initialTodo) { 
        mutableStateOf(initialTodo?.creationDate ?: initialCreationDate) 
    }
    var rawLineValue by remember(initialTodo) {
        val initialText = if (initialTodo != null) {
            initialTodo.rawLine
        } else {
            buildString {
                if (selectedPriority != null) append("($selectedPriority) ")
                if (creationDate != null) append("$creationDate ")
                if (contexts.isNotBlank()) {
                    contexts.split(" ").filter { it.isNotBlank() }.forEach { append("@$it ") }
                }
                if (projects.isNotBlank()) {
                    projects.split(" ").filter { it.isNotBlank() }.forEach { append("+$it ") }
                }
                if (dueDate.isNotBlank()) append("due:$dueDate ")
            }.trimEnd()
        }
        
        val cursorPosition = if (initialTodo == null) {
            // Find priority and date if they exist
            val pMatch = Regex("""^\([A-Z]\)\s""").find(initialText)
            val dMatch = if (creationDate != null) {
                // Creation date follows priority (if present)
                if (pMatch != null) {
                    Regex("""^\([A-Z]\)\s\d{4}-\d{2}-\d{2}\s""").find(initialText)
                } else {
                    Regex("""^\d{4}-\d{2}-\d{2}\s""").find(initialText)
                }
            } else null

            // If we have a date match, place cursor after it, otherwise after priority
            dMatch?.range?.last?.plus(1) ?: pMatch?.range?.last?.plus(1) ?: 0
        } else {
            initialText.length
        }

        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(cursorPosition)
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    val dummyFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (initialTodo == null) {
            delay(100.milliseconds) // Small delay to ensure Dialog content is attached
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    var isUpdatingFromRaw by remember { mutableStateOf(false) }

    fun updateRawFromFields() {
        if (isUpdatingFromRaw) return
        val newMetadata = metadata.toMutableMap()
        if (dueDate.isNotBlank()) newMetadata["due"] = dueDate else newMetadata.remove("due")
        if (recurrence.isNotBlank()) newMetadata["r"] = recurrence else newMetadata.remove("r")

        val tempItem = TodoItem(
            completed = completed,
            completionDate = completionDate,
            creationDate = creationDate,
            description = description,
            priority = selectedPriority,
            projects = projects.split(" ").filter { it.isNotBlank() },
            contexts = contexts.split(" ").filter { it.isNotBlank() },
            metadata = newMetadata,
            rawLine = ""
        )
        val newLine = TodoParser.toLine(tempItem)
        rawLineValue = TextFieldValue(text = newLine, selection = TextRange(newLine.length))
    }

    var showDatePicker by remember(initialTodo) { mutableStateOf(value = false) }
    var showRecurrenceBuilder by remember(initialTodo) { mutableStateOf(value = false) }
    var priorityExpanded by remember(initialTodo) { mutableStateOf(value = false) }
    var projectsExpanded by remember(initialTodo) { mutableStateOf(value = false) }
    var contextsExpanded by remember(initialTodo) { mutableStateOf(value = false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Focus sink to prevent keyboard from popping up after using icons
                Box(Modifier.size(0.dp).focusRequester(dummyFocusRequester).focusable())

                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Task Details", style = MaterialTheme.typography.titleLarge)
                }
                
                TextField(
                    value = rawLineValue,
                    onValueChange = {
                        rawLineValue = it
                        isUpdatingFromRaw = true
                        val parsed = TodoParser.parse(it.text)
                        description = parsed.description
                        selectedPriority = parsed.priority
                        projects = parsed.projects.joinToString(" ")
                        contexts = parsed.contexts.joinToString(" ")
                        dueDate = parsed.metadata["due"] ?: ""
                        recurrence = parsed.metadata["r"] ?: ""
                        metadata = parsed.metadata
                        completed = parsed.completed
                        completionDate = parsed.completionDate
                        creationDate = parsed.creationDate
                        isUpdatingFromRaw = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                // Icons Row for Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority Icon
                    Box {
                        IconButton(onClick = { 
                            dummyFocusRequester.requestFocus()
                            keyboardController?.hide()
                            priorityExpanded = true 
                        }) {
                            Icon(Icons.Default.Flag, contentDescription = "Priority", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(
                            expanded = priorityExpanded,
                            onDismissRequest = { 
                                priorityExpanded = false
                                dummyFocusRequester.requestFocus()
                                keyboardController?.hide()
                            }
                        ) {
                            listOf(null, 'A', 'B', 'C', 'D').forEach { p ->
                                val color = when (p) {
                                    'A' -> Color(0xFFF44336)
                                    'B' -> Color(0xFFFBC02D)
                                    'C' -> Color(0xFF4CAF50)
                                    'D' -> Color(0xFF9C27B0)
                                    else -> Color.Gray
                                }
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(color, shape = androidx.compose.foundation.shape.CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(p?.toString() ?: "", color = Color.White, fontSize = 12.sp)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (p == null) "No Priority" else "Priority $p")
                                        }
                                    },
                                    onClick = {
                                        selectedPriority = p
                                        updateRawFromFields()
                                        priorityExpanded = false
                                        dummyFocusRequester.requestFocus()
                                        keyboardController?.hide()
                                    }
                                )
                            }
                        }
                    }

                    // Date Icon
                    IconButton(onClick = { 
                        dummyFocusRequester.requestFocus()
                        keyboardController?.hide()
                        showDatePicker = true 
                    }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Set Due Date")
                    }

                    // Recurrence Icon
                    IconButton(onClick = { 
                        dummyFocusRequester.requestFocus()
                        keyboardController?.hide()
                        showRecurrenceBuilder = true 
                    }) {
                        Icon(Icons.Default.Repeat, contentDescription = "Set Recurrence")
                    }

                    // Contexts Icon (@)
                    Box {
                        IconButton(onClick = { 
                            dummyFocusRequester.requestFocus()
                            keyboardController?.hide()
                            contextsExpanded = true 
                        }) {
                            Icon(Icons.Default.AlternateEmail, contentDescription = "Add Context")
                        }
                        DropdownMenu(
                            expanded = contextsExpanded,
                            onDismissRequest = { 
                                contextsExpanded = false
                                dummyFocusRequester.requestFocus()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            if (allContexts.isEmpty()) {
                                DropdownMenuItem(text = { Text("No contexts found") }, onClick = { 
                                    contextsExpanded = false
                                    dummyFocusRequester.requestFocus()
                                    keyboardController?.hide()
                                })
                            }
                            allContexts.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = {
                                        val currentList = contexts.split(" ").asSequence().filter { it.isNotBlank() }.toMutableList()
                                        if (!currentList.contains(c)) {
                                            currentList.add(c)
                                            contexts = currentList.joinToString(" ")
                                            updateRawFromFields()
                                        }
                                        contextsExpanded = false
                                        dummyFocusRequester.requestFocus()
                                        keyboardController?.hide()
                                    }
                                )
                            }
                        }
                    }

                    // Projects Icon (+)
                    Box {
                        IconButton(onClick = { 
                            dummyFocusRequester.requestFocus()
                            keyboardController?.hide()
                            projectsExpanded = true 
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Project")
                        }
                        DropdownMenu(
                            expanded = projectsExpanded,
                            onDismissRequest = { 
                                projectsExpanded = false
                                dummyFocusRequester.requestFocus()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            if (allProjects.isEmpty()) {
                                DropdownMenuItem(text = { Text("No projects found") }, onClick = { 
                                    projectsExpanded = false
                                    dummyFocusRequester.requestFocus()
                                    keyboardController?.hide()
                                })
                            }
                            allProjects.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p) },
                                    onClick = {
                                        val currentList = projects.split(" ").asSequence().filter { it.isNotBlank() }.toMutableList()
                                        if (!currentList.contains(p)) {
                                            currentList.add(p)
                                            projects = currentList.joinToString(" ")
                                            updateRawFromFields()
                                        }
                                        projectsExpanded = false
                                        dummyFocusRequester.requestFocus()
                                        keyboardController?.hide()
                                    }
                                )
                            }
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (rawLineValue.text.isNotBlank()) {
                            // Use raw text directly to ensure no links or info are lost
                            val parsed = TodoParser.parse(rawLineValue.text)
                            // Preserve the original ID when editing
                            val finalItem = initialTodo?.let { parsed.copy(id = it.id) } ?: parsed
                            onConfirm(finalItem)
                        }
                    }) {
                        Text("Save")
                    }
                }

                // Extra padding at the bottom ensures the dropdown menus
                // have enough room to expand even if the list is long
                // or if the keyboard is partially covering the dialog.
                Spacer(Modifier.height(120.dp))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        
        LaunchedEffect(datePickerState.selectedDateMillis) {
            datePickerState.selectedDateMillis?.let { millis ->
                val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                dueDate = date.toString()
                updateRawFromFields()
                showDatePicker = false
                dummyFocusRequester.requestFocus()
                keyboardController?.hide()
            }
        }

        DatePickerDialog(
            onDismissRequest = { 
                showDatePicker = false
                dummyFocusRequester.requestFocus()
                keyboardController?.hide()
            },
            confirmButton = { }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showRecurrenceBuilder) {
        RecurrenceBuilderDialog(
            onDismiss = { 
                showRecurrenceBuilder = false
                dummyFocusRequester.requestFocus()
                keyboardController?.hide()
            },
            onConfirm = { code ->
                recurrence = code
                updateRawFromFields()
                showRecurrenceBuilder = false
                dummyFocusRequester.requestFocus()
                keyboardController?.hide()
            },
            onClear = {
                recurrence = ""
                updateRawFromFields()
                showRecurrenceBuilder = false
                dummyFocusRequester.requestFocus()
                keyboardController?.hide()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceBuilderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onClear: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Interval Tab State
    var intervalValue by remember { mutableIntStateOf(1) }
    var intervalUnit by remember { mutableStateOf("d") }

    // Weekly/Monthly Tab State
    var weeklyDay by remember { mutableIntStateOf(2) } // 1=Sun, 2=Mon...
    var monthlyDay by remember { mutableIntStateOf(1) }
    var isMonthlyMode by remember { mutableStateOf(false) }

    // Annual Tab State
    var annualMonth by remember { mutableIntStateOf(1) }
    var annualDay by remember { mutableIntStateOf(1) }

    val filterChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Color(0xFF4CAF50),
        selectedLabelColor = Color.White
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Recurrence") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Interval", fontSize = 12.sp) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Week/Month", fontSize = 12.sp) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Annual", fontSize = 12.sp) })
                }
                
                Spacer(Modifier.height(16.dp))
                
                when (selectedTab) {
                    0 -> { // Interval (##x)
                        Column {
                            Text("Repeat every:", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = intervalValue.toString().padStart(2, '0'),
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.width(60.dp)
                                )
                                Slider(
                                    value = intervalValue.toFloat(),
                                    onValueChange = { intervalValue = it.roundToInt() },
                                    valueRange = 1f..31f,
                                    steps = 30,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("d" to "D", "w" to "W", "m" to "M", "y" to "Y").forEach { (code, label) ->
                                    FilterChip(
                                        selected = intervalUnit == code,
                                        onClick = { intervalUnit = code },
                                        label = { 
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        },
                                        colors = filterChipColors,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    1 -> { // Week/Month (x##)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !isMonthlyMode, onClick = { isMonthlyMode = false })
                                Text("Weekly", modifier = Modifier.clickable { isMonthlyMode = false })
                                Spacer(Modifier.width(16.dp))
                                RadioButton(selected = isMonthlyMode, onClick = { isMonthlyMode = true })
                                Text("Monthly", modifier = Modifier.clickable { isMonthlyMode = true })
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            if (!isMonthlyMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val days = listOf("S", "M", "T", "W", "T", "F", "S")
                                    days.forEachIndexed { index, day ->
                                        val dayValue = index + 1
                                        FilterChip(
                                            selected = weeklyDay == dayValue,
                                            onClick = { weeklyDay = dayValue },
                                            label = { 
                                                Text(
                                                    text = day,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            },
                                            colors = filterChipColors,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else {
                                Column {
                                    Text("Day: $monthlyDay", style = MaterialTheme.typography.labelMedium)
                                    Slider(
                                        value = monthlyDay.toFloat(),
                                        onValueChange = { monthlyDay = it.roundToInt() },
                                        valueRange = 1f..31f,
                                        steps = 30
                                    )
                                }
                            }
                        }
                    }
                    2 -> { // Annual (yMMDD)
                        Column {
                            Text("Month:", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 4) {
                                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                                months.forEachIndexed { index, m ->
                                    val mValue = index + 1
                                    FilterChip(
                                        selected = annualMonth == mValue,
                                        onClick = { annualMonth = mValue },
                                        label = { Text(m, fontSize = 10.sp) },
                                        colors = filterChipColors
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Day: $annualDay", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = annualDay.toFloat(),
                                onValueChange = { annualDay = it.roundToInt() },
                                valueRange = 1f..31f,
                                steps = 30
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val code = when (selectedTab) {
                    0 -> intervalValue.toString().padStart(2, '0') + intervalUnit
                    1 -> if (isMonthlyMode) "m" + monthlyDay.toString().padStart(2, '0') else "w" + weeklyDay.toString().padStart(2, '0')
                    else -> "y" + annualMonth.toString().padStart(2, '0') + annualDay.toString().padStart(2, '0')
                }
                onConfirm(code)
            }) {
                Text("Set")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
