package com.swordfish.lemuroid.app.mobile.feature.covermanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameTexts
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidSmallGameImage
import com.swordfish.lemuroid.lib.library.db.entity.Game

/** Lists the games without a working cover. Tapping one opens its menu, which has every way to fix a cover. */
@Composable
fun CoverManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: CoverManagerViewModel,
    onGameClick: (Game) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val summary =
                when {
                    !state.done -> stringResource(R.string.cover_manager_checking, state.checked, state.total)
                    state.missing.isEmpty() -> stringResource(R.string.cover_manager_all_good)
                    else -> stringResource(R.string.cover_manager_missing, state.missing.size)
                }
            Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            if (!state.done && state.total > 0) {
                LinearProgressIndicator(
                    progress = { state.checked.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (state.missing.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = stringResource(R.string.cover_manager_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.missing, key = { it.id }) { game ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onGameClick(game) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LemuroidSmallGameImage(modifier = Modifier.width(40.dp).height(40.dp), game = game)
                    LemuroidGameTexts(modifier = Modifier.weight(1f).padding(start = 8.dp), game = game)
                }
            }
        }
    }
}
