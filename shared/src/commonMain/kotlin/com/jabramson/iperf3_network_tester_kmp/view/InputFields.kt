/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view


//import android.R.attr.label
//import android.R.attr.singleLine
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation.Companion.keyboardOptions
//import androidx.compose.foundation.text.input.InputTransformation.Companion.keyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults.textFieldColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField



import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass


import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiExecutionData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.DefaultInputData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.Iperf3RunViewModel


/**
 * Host input row for the UI.
 * Consists of:
 *   1. Text field for the host name,
 *   2. Numeric field for the duration
 *   3. Settings section - upload/download

  * @param colors the colors for the text fields
 * @param style the style for the monospaced text
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Host Input Row", showBackground = true, device = "spec:width=411dp,height=891dp", showSystemUi = true)
@Composable
fun InputFields(
    currentHostName: String = getSampleInputData().hostName,
    currentPort: Int = getSampleInputData().portNumber,
    currentDuration: Int = getSampleInputData().durationSecs,
    currentParallelStreams: Int = getSampleInputData().parallelStreams,
    currentSkip: Int = getSampleInputData().skip,
    currentReverse: Boolean = getSampleInputData().isReverse,
    isRunning: Boolean = getSampleUiState(true).isRunning,
    isWide: Boolean = false,
    updateHostName: (String) -> Unit = {},
    uploadDownload: (String) -> Unit = {},
    launch: () -> Unit = {},
    setDuration: (String) -> Unit = {},
    setPortNumber: (String) -> Unit = {},
    setParallelStreams: (String) -> Unit = {},
    setSkip: (String) -> Unit = {},
    colors: androidx.compose.material3.TextFieldColors = textFieldColors(),
    style: TextStyle = MaterialTheme.typography.bodySmall)
{


    val valString: String
    val placeHolder: String


    if (currentHostName.trim().isEmpty()) {
        valString = ""
        placeHolder = DefaultInputData.HOST_NAME.trim()
    } else {
        valString = currentHostName.trim()
        placeHolder = currentHostName.trim()
    }
    // A single row of fields is cheaper on vertical space than two stacked rows,
    // so use it whenever there's either width to spare (isWide) or height is
    // tight (isCompact, e.g. a phone in landscape) -- not just on wide tablets.
    if (!isWide) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp)
        )
        {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                HostField(
                    valString = valString, placeHolder = placeHolder,
                    isRunning = isRunning, colors = colors,
                    updateHostName = updateHostName,
                    launch = launch)
                PortField(currentPort = currentPort, isRunning = isRunning, colors = colors, setPortNumber = setPortNumber)
                DurationField(currentDuration = currentDuration, isRunning = isRunning, colors = colors, setDuration = setDuration)
            }
            Row(modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)) {
                StreamsAndSkip(
                    currentParallelStreams = currentParallelStreams,
                    currentSkip = currentSkip, currentReverse = currentReverse,
                    colors = colors,
                    isRunning = isRunning,
                    uploadDownload = uploadDownload, setSkip = setSkip, setParallelStreams = setParallelStreams
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp).fillMaxWidth()
        ) {
            HostField(
                valString = valString, placeHolder = placeHolder,
                isRunning = isRunning, colors = colors,
                updateHostName = updateHostName,
                isWide = true,
                launch = launch)
            PortField(currentPort = currentPort, isRunning = isRunning, colors = colors, setPortNumber = setPortNumber)
            DurationField(currentDuration = currentDuration, setDuration = setDuration, isRunning = isRunning, colors = colors)
            StreamsAndSkip(
                currentParallelStreams = currentParallelStreams,
                currentSkip = currentSkip,
                currentReverse = currentReverse,
                isRunning = isRunning,
                uploadDownload = uploadDownload,
                setSkip = setSkip,
                colors = colors,
                setParallelStreams = setParallelStreams
            )
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamsAndSkip(currentParallelStreams: Int, currentSkip: Int, currentReverse: Boolean, isRunning: Boolean,
                   uploadDownload: (String) -> Unit,
                   setSkip: (String) -> Unit,
                   setParallelStreams: (String) -> Unit,
                   colors: androidx.compose.material3.TextFieldColors = textFieldColors()) {
    GenericNumericField(
        currentValue = currentParallelStreams,
        onValueChange = setParallelStreams,
        enabled = !isRunning,
        defaultValue = DefaultInputData.PARALLEL_STREAMS,
        label = "Streams",
        modifier = Modifier.width(width = 110.dp).padding(end = 8.dp),
        colors = colors
    )
    GenericNumericField(
        currentValue = currentSkip,
        onValueChange = setSkip,
        enabled = !isRunning,
        defaultValue = DefaultInputData.SKIP,
        label = "Omit",
        modifier = Modifier.width(width = 80.dp),
        colors = colors
    )
    UploadDownload(currentReverse, isRunning = isRunning, setUploadDownload = uploadDownload)

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostField(valString: String = "", placeHolder: String = "",
              colors: androidx.compose.material3.TextFieldColors = textFieldColors(),
              isRunning: Boolean = true,
              isWide: Boolean = false,
              updateHostName: (String) -> Unit = {},
              launch: () -> Unit = {})
{
    TextField(
        value = valString,
        onValueChange = updateHostName,
        enabled = !isRunning,
        placeholder = {
            Text(
                text = placeHolder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.width(width = if (!isWide) 180.dp else 210.dp)
            .padding(end = 8.dp),
        label = {
            Text(
                text = "iPerf3 Server",
                style = MaterialTheme.typography.bodySmall
            )
        },
        colors = if (!isRunning) colors else colors.copy(disabledTextColor = MaterialTheme.colorScheme.error),
        singleLine = true,
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { launch() }),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortField(currentPort: Int = 0, isRunning: Boolean = true, colors: androidx.compose.material3.TextFieldColors = textFieldColors(),
              setPortNumber: (String) -> Unit = {}, )
{
    GenericNumericField(
        currentValue = currentPort,
        onValueChange = setPortNumber,
        enabled = !isRunning,
        defaultValue = DefaultInputData.PORT_NUMBER,
        label = "Port",
        modifier = Modifier
            .width(width = 80.dp)
            .padding(end = 8.dp),
        colors = colors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationField(currentDuration: Int = 0, isRunning: Boolean = true,
                  colors: androidx.compose.material3.TextFieldColors = textFieldColors(),
                  setDuration: (String) -> Unit = {}, ) {
    GenericNumericField(
        currentValue = currentDuration,
        onValueChange = setDuration,
        enabled = !isRunning,
        defaultValue = DefaultInputData.DURATION,
        label = "Duration",
        modifier = Modifier.width(width = 110.dp).padding(end = 8.dp),
        //.padding(end = .dp),
        colors = colors
    )

}