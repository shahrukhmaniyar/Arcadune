package com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R

@Composable
fun GameMenuCheatsScreen(viewModel: GameMenuCheatsViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { viewModel.findCheatsOnline() },
                enabled = !state.downloading,
            ) {
                Text(
                    stringResource(
                        if (state.downloading) R.string.cheats_searching else R.string.cheats_find_online,
                    ),
                )
            }
            OutlinedButton(onClick = { showAddDialog = true }) {
                Text(stringResource(R.string.cheats_add))
            }
        }

        Text(
            text = stringResource(R.string.cheats_take_effect_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (state.cheats.isEmpty()) {
            Text(
                text = stringResource(R.string.cheats_empty),
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(state.cheats) { index, cheat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = cheat.description, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text =
                                viewModel.unsupportedReason(cheat)?.let { stringResource(it) }
                                    ?: cheat.code,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Switch(
                        checked = cheat.enabled && viewModel.isSupported(cheat),
                        enabled = viewModel.isSupported(cheat),
                        onCheckedChange = { viewModel.setEnabled(index, it) },
                    )
                    IconButton(onClick = { viewModel.deleteCheat(index) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cheats_delete),
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.cheats_credit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }

    if (showAddDialog) {
        AddCheatDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, code ->
                viewModel.addCheat(name, code)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddCheatDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, code: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        title = { Text(stringResource(R.string.cheats_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.cheats_add_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.cheats_add_code)) },
                    supportingText = { Text(stringResource(R.string.cheats_add_code_hint)) },
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(name, code) }, enabled = code.isNotBlank()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
