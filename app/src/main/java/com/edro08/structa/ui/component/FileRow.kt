package com.edro08.structa.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R
import com.edro08.structa.domain.filesystem.FileEntry
import com.edro08.structa.ui.theme.StructaSpacing

@Composable
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> stringResource(R.string.common_size_bytes, bytes)
    bytes < 1024 * 1024 -> stringResource(R.string.common_size_kilobytes, bytes / 1024)
    else -> stringResource(R.string.common_size_megabytes, bytes / (1024 * 1024))
}

@Composable
fun FileRow(entry: FileEntry, onClick: () -> Unit, modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = StructaSpacing.compact),
                verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (entry.isDirectory) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small) {
                    Text(stringResource(if (entry.isDirectory) R.string.common_folder_badge else R.string.common_file_badge), Modifier.padding(horizontal = StructaSpacing.compact, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.isDirectory) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(StructaSpacing.compact))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(if (entry.isDirectory) stringResource(R.string.common_folder) else formatBytes(entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        actions()
    }
    HorizontalDivider()
}
