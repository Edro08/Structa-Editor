package com.edro08.structa.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edro08.structa.ui.theme.ScreenStyle

@Composable
fun WorkspacePanel(modifier: Modifier = Modifier, compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(if (compact) ScreenStyle.tileRadius else ScreenStyle.panelRadius)
    val colors = MaterialTheme.colorScheme
    Column(modifier.clip(shape).background(Brush.linearGradient(
        listOf(colors.surfaceContainerLow, colors.surfaceContainerHigh)))
        .border(1.dp, colors.outlineVariant, shape)
        .padding(if (compact) 10.dp else 14.dp), content = content)
}

@Composable
fun WorkspaceBadge(modifier: Modifier = Modifier, label: String? = null) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(ScreenStyle.badgeSize).clip(RoundedCornerShape(13.dp))
        .background(Brush.linearGradient(listOf(colors.surfaceContainerHigh, colors.surfaceContainerHighest))),
        contentAlignment = Alignment.Center) {
        if (label == null) Icon(Icons.Filled.Folder, contentDescription = null,
            modifier = Modifier.size(27.dp), tint = colors.primary)
        else Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
    }
}

@Composable
fun WorkspaceSectionTitle(icon: ImageVector, title: String, description: String? = null,
    action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
    if (description != null) Text(description, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
        overflow = TextOverflow.Ellipsis)
}

@Composable
fun WorkspaceActionChip(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, maxLines = 1)
    }
}

@Composable
fun WorkspaceSelection(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp),
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 10.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
            Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun WorkspacePrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.height(48.dp).clip(RoundedCornerShape(26.dp)).background(Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer)))
        .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall)
    }
}
