package com.edro08.structa.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R
import com.edro08.structa.ui.theme.StructaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructaTopBar(title: @Composable () -> Unit, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = title, navigationIcon = {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
        }
    }, actions = actions, colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background))
}

@Composable
fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ScreenSection(title: String, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(StructaSpacing.compact)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
fun EmptyScreen(title: String, description: String? = null, modifier: Modifier = Modifier,
    actions: (@Composable ColumnScope.() -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(StructaSpacing.section),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (description != null) {
            Spacer(Modifier.height(StructaSpacing.compact))
            SupportingText(description)
        }
        if (actions != null) {
            Spacer(Modifier.height(StructaSpacing.section))
            actions()
        }
    }
}
