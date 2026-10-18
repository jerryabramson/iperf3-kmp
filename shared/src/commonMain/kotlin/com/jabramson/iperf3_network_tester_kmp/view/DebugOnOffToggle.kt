/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGesturesPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData

//import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData

@Preview(name ="DebugOnOffRadioButtonPreview", showBackground = true, device = Devices.PIXEL_9, showSystemUi = true)
@Composable
fun DebugOnOffToggle(
    currentDebugging: Boolean = false, //getSampleInputData().isDebugging,
    toggleDebug: () -> Unit = {},
    isWide: Boolean = false
) {
    val buttonColor = if (currentDebugging) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val text = if (currentDebugging) "output OFF" else "output ON"
    if (!isWide) {
        Column(horizontalAlignment = Alignment.End, modifier =   Modifier.padding(end =10.dp)) {
            Button(onClick = toggleDebug, colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            ) {
                Text(text = text)
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = toggleDebug,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                // Button's default min-height (40dp) doesn't shrink to fit a tight
                // parent -- it just gets clipped, text and all. Override both the
                // padding and the height explicitly so the text has room to render.
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                //modifier = Modifier.height(48.dp)
            ) {
                Text(text = text)
            }
        }
    }
}
