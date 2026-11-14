package com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.achievements.RaGoldColor
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconPill
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsSessionHolder

@Composable
fun GameMenuCheatsScreen(
    viewModel: GameMenuCheatsViewModel,
    addingCheat: Boolean = false,
    onOpenAddCheat: () -> Unit = {},
    onCloseAddCheat: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val achievementsState by RetroAchievementsSessionHolder.state.collectAsState()
    val hardcoreActive = achievementsState.hardcoreActive
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    // Rendered inside the same pause-menu window (not a Dialog) so Android never unhides its
    // system navigation bar, and the shared bottom Back pill handles leaving Add cheat.
    if (addingCheat) {
        AddCheatPage(
            onConfirm = { name, code ->
                viewModel.addCheat(name, code)
                onCloseAddCheat()
            },
        )
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ArcadunePillButton(
                    modifier = Modifier.weight(1.25f),
                    label =
                        stringResource(
                            if (state.downloading) R.string.cheats_searching else R.string.cheats_find_online,
                        ),
                    icon = Icons.Filled.Search,
                    highlighted = true,
                    enabled = !state.downloading,
                    height = 46.dp,
                    onClick = { viewModel.findCheatsOnline() },
                )
                ArcadunePillButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.cheats_add),
                    icon = Icons.Filled.Add,
                    height = 46.dp,
                    onClick = onOpenAddCheat,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hardcoreActive) {
                    item(key = "hardcore_notice") {
                        val bannerShape = RoundedCornerShape(16.dp)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(bannerShape)
                                    .background(RaGoldColor.copy(alpha = 0.14f))
                                    .border(1.dp, RaGoldColor.copy(alpha = 0.48f), bannerShape)
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Whatshot,
                                contentDescription = null,
                                tint = RaGoldColor,
                            )
                            Text(
                                text = stringResource(R.string.ra_cheats_hardcore_banner),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = ArcaduneColors.OnSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item(key = "hint") {
                    val hintShape = RoundedCornerShape(16.dp)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(hintShape)
                                .background(Color(0xE61C1A27))
                                .border(1.dp, ArcaduneColors.GlassEdge, hintShape)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.cheats_take_effect_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = ArcaduneColors.OnSurfaceDim,
                        )
                    }
                }

                if (state.cheats.isEmpty()) {
                    item(key = "empty") {
                        val emptyShape = RoundedCornerShape(20.dp)
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(emptyShape)
                                    .background(Color(0xE61C1A27))
                                    .border(1.dp, ArcaduneColors.GlassEdge, emptyShape)
                                    .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ArcaduneIconBadge(
                                icon = Icons.Filled.AutoFixHigh,
                                tint = ArcaduneColors.Purple,
                                containerColor = ArcaduneColors.Purple.copy(alpha = 0.16f),
                                borderColor = ArcaduneColors.Purple.copy(alpha = 0.45f),
                                size = 44.dp,
                                iconSize = 22.dp,
                            )
                            Text(
                                text = stringResource(R.string.cheats_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ArcaduneColors.OnSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = state.cheats,
                    key = { index, cheat -> "${index}_${cheat.code}_${cheat.description}" },
                ) { index, cheat ->
                    val supported = viewModel.isSupported(cheat)
                    val active = cheat.enabled && supported && !hardcoreActive
                    val cardShape = RoundedCornerShape(18.dp)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(
                                    if (active) {
                                        ArcaduneColors.Purple.copy(alpha = 0.18f)
                                    } else {
                                        Color(0xE61C1A27)
                                    },
                                )
                                .border(
                                    width = 1.dp,
                                    color =
                                        if (active) {
                                            ArcaduneColors.Purple.copy(alpha = 0.55f)
                                        } else {
                                            ArcaduneColors.GlassEdge
                                        },
                                    shape = cardShape,
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = cheat.description,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = ArcaduneColors.OnSurface,
                            )
                            Text(
                                text =
                                    viewModel.unsupportedReason(cheat)?.let { stringResource(it) }
                                        ?: cheat.code,
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                color = ArcaduneColors.OnSurfaceDim,
                                maxLines = 2,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = active,
                            enabled = supported && !hardcoreActive,
                            onCheckedChange = { viewModel.setEnabled(index, it) },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ArcaduneIconPill(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cheats_delete),
                            size = 36.dp,
                            onClick = { viewModel.deleteCheat(index) },
                        )
                    }
                }

                item(key = "credit") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.cheats_credit),
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcaduneColors.OnSurfaceDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCheatPage(onConfirm: (name: String, code: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Color(0xE61C1A27))
                    .border(1.dp, ArcaduneColors.GlassEdge, cardShape)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.cheats_add_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.cheats_add_code)) },
                supportingText = { Text(stringResource(R.string.cheats_add_code_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ArcadunePillButton(
                    label = stringResource(R.string.cheats_add),
                    icon = Icons.Filled.Check,
                    highlighted = true,
                    enabled = code.isNotBlank(),
                    height = 44.dp,
                    onClick = { onConfirm(name, code) },
                )
            }
        }
    }
}
