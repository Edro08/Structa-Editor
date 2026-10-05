package com.edro08.structa.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.edro08.structa.R
import com.edro08.structa.ui.component.EmptyScreen
import com.edro08.structa.ui.component.StructaTopBar
import com.edro08.structa.ui.theme.StructaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeUiState, onOpenLastFolder: () -> Unit, onChooseFolder: () -> Unit) {
    Scaffold(topBar = { StructaTopBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        EmptyScreen(stringResource(R.string.common_home_tagline),
            stringResource(R.string.common_home_description), Modifier.padding(padding)) {
            if (state.lastFolder != null) {
                Button(onClick = onOpenLastFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_open_last_folder)) }
                Spacer(Modifier.height(StructaSpacing.compact))
            }
            OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_choose_folder)) }
        }
    }
}
