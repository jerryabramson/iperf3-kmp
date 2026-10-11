package com.jabramson.iperf3_network_tester_kmp

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.jabramson.iperf3_network_tester_kmp.ui.theme.Iperf3NetworkTesterTheme
import com.jabramson.iperf3_network_tester_kmp.view.RunIperf3Screen

/**
 * Root composable function that serves as the entry point for the application UI.
 * It applies the Iperf3NetworkTesterTheme to establish consistent styling and typography,
 * then renders the RunIperf3Screen composable to display the main network testing interface.
 */
@Composable
@Preview(name = "App",
    showBackground = true,
    device =  Devices.PIXEL_6,
    showSystemUi = true)

fun App() {
    Iperf3NetworkTesterTheme {
        RunIperf3Screen()
    }
}