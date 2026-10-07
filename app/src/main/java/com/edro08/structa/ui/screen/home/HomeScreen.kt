package com.edro08.structa.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edro08.structa.R
import com.edro08.structa.domain.document.DocumentId
import com.edro08.structa.domain.workspace.WorkspaceShortcut
import com.edro08.structa.ui.component.WorkspaceBadge
import com.edro08.structa.ui.component.WorkspacePanel
import com.edro08.structa.ui.component.WorkspacePrimaryButton
import com.edro08.structa.ui.component.WorkspaceSectionTitle
import com.edro08.structa.ui.theme.ScreenStyle
import java.util.Calendar

@Composable
fun HomeScreen(state: HomeUiState, onOpenWorkspace: (DocumentId) -> Unit, onChooseFolder: () -> Unit,
    onToggleFavorite: (DocumentId) -> Unit, onRemoveFromHistory: (DocumentId) -> Unit) {
    var expandedFavorites by rememberSaveable { mutableStateOf(false) }
    var expandedRecent by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<WorkspaceShortcut?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(
            start = ScreenStyle.pagePadding, end = ScreenStyle.pagePadding, top = 12.dp,
            bottom = ScreenStyle.sectionGap), verticalArrangement = Arrangement.spacedBy(ScreenStyle.sectionGap)) {
            item {
                Column(Modifier.padding(start = 6.dp, top = 8.dp, bottom = 6.dp)) {
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                HomeSection(stringResource(R.string.home_favorites), stringResource(R.string.home_no_favorites), true,
                    state.favorites, expandedFavorites, { expandedFavorites = !expandedFavorites }, onOpenWorkspace,
                    onToggleFavorite, { pendingRemoval = it })
            }
            item {
                HomeSection(stringResource(R.string.home_recent), stringResource(R.string.home_recent_hint), false,
                    state.recent, expandedRecent, { expandedRecent = !expandedRecent }, onOpenWorkspace,
                    onToggleFavorite, { pendingRemoval = it })
            }
        }
        WorkspacePrimaryButton(stringResource(R.string.home_open_workspace), Icons.Filled.FolderOpen,
            onChooseFolder, Modifier.fillMaxWidth().padding(
                start = ScreenStyle.pagePadding, end = ScreenStyle.pagePadding, bottom = 10.dp))
    }

    pendingRemoval?.let { entry ->
        AlertDialog(onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.home_remove_title)) },
            text = { Text(stringResource(R.string.home_remove_description, entry.name)) },
            confirmButton = {
                TextButton(onClick = { onRemoveFromHistory(entry.id); pendingRemoval = null }) {
                    Text(stringResource(R.string.home_remove_confirm))
                }
            }, dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(R.string.common_cancel)) }
            })
    }
}

@Composable
private fun HomeSection(title: String, description: String, favorites: Boolean,
    entries: List<WorkspaceShortcut>, expanded: Boolean, onToggleExpanded: () -> Unit,
    onOpen: (DocumentId) -> Unit, onFavorite: (DocumentId) -> Unit, onRemove: (WorkspaceShortcut) -> Unit) {
    WorkspacePanel(Modifier.fillMaxWidth()) {
        WorkspaceSectionTitle(if (favorites) Icons.Filled.Star else Icons.Filled.History, title, description,
            if (entries.size > 3) {{
                TextButton(onClick = onToggleExpanded) {
                    Text(stringResource(if (expanded) R.string.home_show_less else R.string.home_show_all))
                }
            }} else null)
        Spacer(Modifier.height(10.dp))
        val shown = if (expanded) entries else entries.take(3)
        if (shown.isEmpty()) {
            Text(stringResource(if (favorites) R.string.home_empty_favorites else R.string.home_no_recent),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        shown.forEachIndexed { index, entry ->
            if (index != 0) Spacer(Modifier.height(ScreenStyle.tileGap))
            WorkspaceCard(entry, !favorites, onOpen, onFavorite) { onRemove(entry) }
        }
    }
}

@Composable
private fun WorkspaceCard(entry: WorkspaceShortcut, recent: Boolean, onOpen: (DocumentId) -> Unit,
    onFavorite: (DocumentId) -> Unit, onRemove: () -> Unit) {
    val openDescription = stringResource(R.string.home_open_named_workspace, entry.name)
    WorkspacePanel(Modifier.fillMaxWidth(), compact = true) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(onClickLabel = openDescription) { onOpen(entry.id) },
                verticalAlignment = Alignment.CenterVertically) {
                WorkspaceBadge()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    val path = entry.path?.let { stringResource(R.string.home_workspace_path, it) }
                    val subtitle = if (recent) {
                        val opened = openedLabel(entry.lastOpenedAt)
                        if (path == null) opened else stringResource(R.string.home_recent_detail, opened, path)
                    } else path
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { onFavorite(entry.id) }, modifier = Modifier.size(40.dp)) {
                Icon(if (entry.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(if (entry.favorite) R.string.home_unfavorite else R.string.home_favorite),
                    tint = if (entry.favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_remove_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun openedLabel(time: Long): String {
    val today = Calendar.getInstance()
    val startToday = (today.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val startYesterday = (startToday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val days = ((startToday.timeInMillis - time) / (24 * 60 * 60 * 1000L)).toInt() + 1
    return when {
        time >= startToday.timeInMillis -> stringResource(R.string.home_opened_today)
        time >= startYesterday.timeInMillis -> stringResource(R.string.home_opened_yesterday)
        else -> pluralStringResource(R.plurals.home_opened_days_ago, days, days)
    }
}
