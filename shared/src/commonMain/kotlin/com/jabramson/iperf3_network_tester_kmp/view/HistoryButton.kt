package com.jabramson.iperf3_network_tester_kmp.view


import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices.PIXEL_6
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top-bar button that navigates to the History screen. Only ever rendered
 * by [IperfTopBar] once [Iperf3RunViewModel.hasHistoryStateFlow] is true --
 * there's nothing to browse before the first result is saved, so the caller
 * omits this composable entirely rather than passing a disabled state.
 *
 * Disabled (not omitted) while a test is running -- see [IperfTopBar]'s
 * `currentRunning` -- so it stays visible but greyed out rather than
 * flickering in and out of the top bar mid-run.
 */
@Preview("History Button Preview", showBackground = true,
    device = PIXEL_6, showSystemUi = true
)
@Composable
fun HistoryButton(
    onClick: () -> Unit = {},
    isCompact: Boolean = false,
    enabled: Boolean = true,
) {
    val style = if (isCompact) mesloMonoTextStyle().copy(fontSize = 8.sp) else MaterialTheme.typography.titleMedium
    Button(
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        // Button's default min-height (40dp) doesn't shrink to fit a tight top
        // bar -- it just gets clipped, text and all. Override explicitly.
        contentPadding = if (isCompact) PaddingValues(horizontal = 2.dp, vertical = 2.dp) else ButtonDefaults.ContentPadding,
        modifier = if (isCompact) Modifier.height(28.dp) else Modifier.padding(end = 10.dp),
    ) {
        Text(text = "History", color = MaterialTheme.colorScheme.primaryContainer, style = style)
    }
}
