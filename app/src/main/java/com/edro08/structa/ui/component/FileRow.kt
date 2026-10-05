package com.edro08.structa.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R
import com.edro08.structa.domain.filesystem.FileEntry

@Composable
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> stringResource(R.string.common_size_bytes, bytes)
    bytes < 1024 * 1024 -> stringResource(R.string.common_size_kilobytes, bytes / 1024)
    else -> stringResource(R.string.common_size_megabytes, bytes / (1024 * 1024))
}

@Composable
fun FileRow(entry: FileEntry, onClick: () -> Unit, modifier: Modifier = Modifier,
    detail: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    WorkspacePanel(modifier.fillMaxWidth(), compact = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
                WorkspaceBadge(label = if (entry.isDirectory) null else entry.name.substringAfterLast('.', "TXT")
                    .uppercase().take(4))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(detail ?: if (entry.isDirectory) stringResource(R.string.common_folder) else formatBytes(entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            actions()
        }
    }
}
