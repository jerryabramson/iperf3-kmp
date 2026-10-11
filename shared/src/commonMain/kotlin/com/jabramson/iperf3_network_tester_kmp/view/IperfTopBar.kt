package com.jabramson.iperf3_network_tester_kmp.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
//import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
//import com.jabramson.iperf3_network_tester_kmp.R
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiExecutionData

/**
 * Top bar for the UI.
 * @param buttonAction the action to be performed when the button is clicked
 * @param hasHistory whether at least one result has been saved -- the History
 *   button is only rendered when true; there's nothing to browse otherwise
 * @param onHistoryClick the action to navigate to the History screen
 * @param onTitleTapped hidden developer-mode unlock hook -- called on every
 *   tap of the title text; see Iperf3RunViewModel.onAppTitleTapped() for
 *   what it actually does after enough taps. Deliberately not indicated
 *   visually (no ripple -- see the title Text's clickable modifier below):
 *   a visible tap affordance would defeat the point of it being hidden.
 * @uiState the current state of the UI
 */
@Preview(name ="Iperf Top Bar",showSystemUi = true)
@Composable
fun IperfTopBar(
    currentRunning: Boolean = false,
    hasHistory: Boolean = false,
    buttonAction: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onTitleTapped: () -> Unit = {},
    isCompact: Boolean = false,
) {
    // CenterAlignedTopAppBar reserves ~64dp regardless of content size, so it
    // needs an explicit override to actually shrink when vertical space is tight.
    val barModifier = if (isCompact) Modifier.height(55.dp) else Modifier
    CenterAlignedTopAppBar(
        modifier = barModifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),


        navigationIcon = {
            if (hasHistory) {
                HistoryButton(
                    onClick = onHistoryClick,
                    isCompact = isCompact,
                    enabled = !currentRunning)
            }
        },
        title = {
            Row(
                   //.background(color = MaterialTheme.colorScheme.onPrimaryContainer)
                    //.padding(start = 20.dp, end = 20.dp),
            )
            {
                Text(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // no ripple -- see onTitleTapped's doc
                        onClick = onTitleTapped
                    ),
                    style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    text =  "iperf3 Network Tester",
                    maxLines = 1,
                    //text = stringResource(
                    //    id = Res.string.app_name,
                    //    resource = TODO()
                    //),
                    //color = MaterialTheme.colorScheme.surface
                )
            }
        },
        actions = {
            RunButton(
                isRunning = currentRunning,
                buttonAction = buttonAction,
                isCompact = isCompact
            )
        }


    )
}