package com.edro08.structa.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.edro08.structa.ui.theme.StructaSpacing

@Composable
fun MessageOverlay(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(StructaSpacing.content), contentAlignment = Alignment.BottomCenter) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(StructaSpacing.content), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
}
