package com.jabramson.iperf3_network_tester_kmp.view

import androidx.compose.foundation.layout.height
import androidx.compose.material3.BottomAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData

@Composable
fun IperfBottomBar(isDebugging: Boolean = false,
                   isWide: Boolean = false,
                   elapsedTime: Double = 0.toDouble(),
                   preview: Boolean = true,
                   buttonAction: () -> Unit) {
    // BottomAppBar reserves ~80dp regardless of content size, so it needs an
    // explicit override to actually shrink when vertical space is tight.
    val modifier = if (isWide) Modifier.height(48.dp) else Modifier
    BottomAppBar(modifier = modifier) {
        ProjectBottomBar(currentDebugging = isDebugging, isWide = isWide, preview = preview, elapsedTime = elapsedTime, toggleDebug =  buttonAction)
    }
}