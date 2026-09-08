package com.vodotxt.domain

import com.vodotxt.data.AppTheme
import com.vodotxt.data.SwipeAction

data class AppConfig(
    val fontSize: Int?,
    val theme: AppTheme?,
    val showCheckboxes: Boolean?,
    val swipeRightAction: SwipeAction?,
    val savedFilters: List<TodoFilter>?,
    val invertAdHocProjects: Boolean? = false,
    val invertAdHocContexts: Boolean? = false,
    val keepLastTag: Boolean? = true,
    val addCreationDate: Boolean? = false
)
