package com.edro08.structa.ui.screen.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R

/** Keyboard and touch share selection; Escape dismisses without changing the document. */
@Composable
fun ProductivityPicker(title: String, query: String, onQuery: (String) -> Unit,
    labels: List<Pair<String, String>>, message: String?, loading: Boolean,
    onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(query, labels) { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val scroll = rememberLazyListState()
    LaunchedEffect(selected) { if (selected in labels.indices) scroll.animateScrollToItem(selected) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
            Column(Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.Escape -> { onDismiss(); true }
                    Key.DirectionDown -> { selected = (selected + 1).coerceAtMost((labels.size - 1).coerceAtLeast(0)); true }
                    Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                    Key.Enter, Key.NumPadEnter -> { if (selected in labels.indices) onChoose(selected); true }
                    else -> false
                }
            }) {
                 TextField(query, onQuery, label = { Text(stringResource(R.string.editor_filter)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                 if (labels.isEmpty() && !loading) Text(stringResource(R.string.editor_no_results))
                LazyColumn(Modifier.heightIn(max = 320.dp), state = scroll) {
                    itemsIndexed(labels) { index, (label, detail) ->
                        Surface(color = if (index == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().clickable { onChoose(index) }.padding(8.dp)) {
                                Text(label)
                                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
         }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } })
}
