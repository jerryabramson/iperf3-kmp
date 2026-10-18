/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view

//import android.R
//import android.util.Log.e
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabramson.iperf3_network_tester_kmp.utils.formatTime
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
//import java.nio.file.Files.size


/**
 * Bottom bar for the UI.
 */
@Composable
@Preview(name ="Project Bottom Bar", showBackground = true, showSystemUi = true)
fun ProjectBottomBar(currentDebugging: Boolean = getSampleInputData().isDebugging,
                     elapsedTime: Double = getSampleUiMicroState().elapsedTime,
                     isWide: Boolean = false,
                     preview: Boolean = true,
                     toggleDebug: () -> Unit = {}
) {


    Row(verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(start = 2.dp, end =2.dp)
            .fillMaxWidth()) {
        if (elapsedTime != 0.toDouble()) {
            val time = formatTime(elapsedTime, preview)
            Column(modifier = Modifier.padding(end = 2.dp)) {
                Text(
                    text = if (!isWide) "Elapsed Test Time" else "Elapsed Test Time: $time",
                    style = if (!isWide) mesloMonoULTextStyle() else mesloMonoBoldTextStyle(),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                if (!isWide) {
                    Text(
                        text = time,
                        style = mesloMonoBoldTextStyle(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        } else {
            Text(
                text = "iperf3 Network Tester",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )

        }
        DebugOnOffToggle(currentDebugging, toggleDebug, isWide)
    }
}