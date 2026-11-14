package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton

@Composable
fun CustomNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable(initialName) { mutableStateOf(initialName) }

    ArcaduneDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Default.Edit,
        title = { Text(text = stringResource(id = R.string.game_context_menu_custom_name_dialog_title)) },
        dismissButton = {
            ArcadunePillButton(
                label = stringResource(id = android.R.string.cancel),
                onClick = onDismiss,
            )
        },
        confirmButton = {
            ArcadunePillButton(
                label = stringResource(id = R.string.ok),
                highlighted = true,
                enabled = text.isNotBlank(),
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text.trim())
                    }
                },
            )
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
