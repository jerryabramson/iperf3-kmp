/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jabramson.iperf3_network_tester_kmp.utils.getDest
import com.jabramson.iperf3_network_tester_kmp.utils.getSource


/**
 * Results row for the UI.
 * @param monoStyle the style for the monospaced text
 */
@Preview(name = "Results Row Preview", showBackground = true, showSystemUi = true,
    device = "spec:width=411dp,height=891dp"
)
@Composable
fun ResultsRow(
    results: MutableList<String> = getSampleUiState(true).results,
    src: String = getSampleUiState(true).iperf3RunningState.localHostDetails,
    dest: String = getSampleUiState(true).iperf3RunningState.remoteHostDetails,
    returnCode: Int = getSampleUiState(true).returnCode,
    isRunning: Boolean = false,
    isFinished: Boolean = true,
    monoStyle: TextStyle = mesloMonoTextStyle(),
    isWide: Boolean = false,
    isTall: Boolean = false,
    isTiny: Boolean = false
) {
    if (isRunning || !isFinished) return
    if (results.isEmpty()) return
    val thick = if (returnCode != 0) 3.dp else 2.dp
    val resultColor =
        if (returnCode != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    if (results.isNotEmpty()) {
        if ((!isWide && isTall) || isTiny) {
            val style = if (!isTiny) monoStyle.copy(fontSize = 15.sp) else monoStyle.copy(fontSize = 9.sp)
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = thick,
                color = MaterialTheme.colorScheme.tertiary
            )
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp).fillMaxWidth()
            ) {
                Text(
                    text = src,
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Left,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dest,
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Left,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = thick,
                    color = resultColor
                )
                if (returnCode != 0) {
                    Text(
                        text = "     RC: $returnCode",
                        style = style,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Left,
                        color = resultColor
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(results.size) { index ->
                        Text(
                            text = results[index],
                            style = style,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Left,
                            color = resultColor
                        )
                    }
                }
            }
        } else {
            val style = if (!isWide) monoStyle.copy(fontSize = 12.sp) else monoStyle.copy(fontSize = 13.sp)

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp)
            ) {
                Text(
                    text = src.trim(),
                    style = if (isTall) monoStyle.copy(fontSize = 16.sp) else monoStyle.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                if (returnCode != 0) {
                    Text(
                        text = "RC: $returnCode",
                        style = style,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = dest.trim(),
                    style = style,
                    color = MaterialTheme.colorScheme.primary
                )


            }
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp),
                thickness = 1.dp,
                color = resultColor
            )

            if (results.size >= 6) {
                val div = results.size / 3
                val firstHalf = results.subList(0, div)
                val secondHalf = results.subList(div, div+2)
                val thirdHalf = results.subList(div+2, results.size)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp)
                ) {
                    LazyColumn() {
                        items(firstHalf.size) { index ->
                            Text(
                                text = firstHalf[index],
                                style = style,
                                color = resultColor
                            )
                        }
                    }
                    LazyColumn() {
                        items(secondHalf.size) { index ->
                            Text(
                                text = secondHalf[index],
                                style = style,
                                //modifier = Modifier.fillMaxWidth(),
                                color = resultColor
                            )
                        }
                    }
                    LazyColumn() {
                        items(thirdHalf.size) { index ->
                            Text(
                                text = thirdHalf[index],
                                style = style,
                                //modifier = Modifier.fillMaxWidth(),
                                color = resultColor
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.padding(start = 10.dp, end = 10.dp).fillMaxWidth()) {
                    items(results.size) { index ->
                        Text(
                            text = results[index],
                            style = style,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Left,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}



