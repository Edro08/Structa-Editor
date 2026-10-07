package com.edro08.structa.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.edro08.structa.ui.theme.StructaSizes

@Composable
fun ActionIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable () -> Unit) {
    OutlinedIconButton(onClick = onClick, modifier = modifier.size(StructaSizes.compactIconButton),
        enabled = enabled, content = content)
}
