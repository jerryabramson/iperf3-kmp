/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view

import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.jabramson.iperf3_network_tester_kmp.Iperf3KMPConstants
import kotlinx.coroutines.NonCancellable.start

private val urlRegex = Regex("""https?://\S+""")

/**
 * Renders [text] as a clickable link only when it's one of the exact,
 * deliberately-allowlisted [Iperf3KMPConstants.LinkableMessages.ALL] lines --
 * so a message is never auto-linkified just because it happens to contain
 * URL-looking text.
 */
private fun linkify(text: String, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    if (text !in Iperf3KMPConstants.LinkableMessages.ALL) return AnnotatedString(text)
    return buildAnnotatedString {
        var lastIndex = 0
        for (match in urlRegex.findAll(text)) {
            append(text.substring(lastIndex, match.range.first))
            val url = match.value
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                )
            ) {
                append(url)
            }
            lastIndex = match.range.last + 1
        }
        append(text.substring(lastIndex))
    }
}


@Preview(name = "Iperf Messages Section", showBackground = true,
    device = "spec:width=411dp,height=891dp"
)
@Composable
fun IperfMessagesSection(
    isDebugMode: Boolean = true,
    isRunning: Boolean = true,
    isFinished: Boolean = false,
    latestLine: String = "foobar",
    errorLines: MutableList<String> = getSampleUiState(true).errorLines,
    iperfMessages: MutableList<String> = getSampleUiState(true).iperf3Messages,
    returnCode: Int = 0,
    //uiState: UiExecutionData = getSampleUiState(),
    monoStyle: TextStyle = mesloMonoTextStyle(),
    isWide: Boolean = true,
    isTall: Boolean = false,
    isTiny: Boolean = false
) {
    val isActive = (isRunning && !isFinished && latestLine.isEmpty())
    val isErrors = errorLines.isNotEmpty()

    var fontSize: TextUnit
    val maxHeight: Dp

    if (latestLine.isEmpty()) {
        fontSize = 17.sp
        maxHeight = 160.dp
    } else {
        fontSize = if (isActive || isErrors || isDebugMode) {
            if (isTiny) 12.sp
            else if (isTall || isWide) 17.sp
            else 15.sp

        } else {
            if (isTall) 14.sp else {
                if (isTiny) 9.sp else if (isWide) 10.sp else 11.sp
            }
        }

        if (!isDebugMode) {
            if (isErrors || isActive) {
                maxHeight = 900.dp
            } else {
                maxHeight = 600.dp
            }
        } else {
            if (isActive || isErrors) {
                maxHeight = if (isTall) 160.dp else 100.dp
            } else {
                maxHeight = if (isTall && !isWide && !isTiny) 120.dp else 90.dp
            }
        }
    }
    val style = monoStyle.copy(fontSize = fontSize)

    if (isDebugMode || isActive || isErrors) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 4.dp, end =  10.dp)
        ) {
            Column(
                modifier = Modifier
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.primaryFixedDim)
                    .fillMaxWidth()
                    .height(maxHeight)
            ) {
                Text("iPerf3 Messages",
                    style = style,
                    color = MaterialTheme.colorScheme.tertiaryFixedDim,
                    modifier = Modifier.padding(top = 2.dp, start = 10.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.primaryFixedDim
                )

                if (iperfMessages.isNotEmpty()) {
                    if (isActive && !isErrors) {
                        Row(modifier = Modifier.padding(start = 2.dp)) {
                            Text(
                                text = "Connecting ...",
                                style = mesloMonoItalicTextStyle().copy(fontSize = fontSize),
                                modifier = Modifier.padding(start = 10.dp)
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterVertically).size(14.dp)
                                    .padding(top = 2.dp, start = 10.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }


                    // Capped so this can't silently claim all remaining vertical space
                    // in the non-scrolling parent Column the way an unbounded
                    // LazyColumn otherwise would -- it scrolls within the cap instead.
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(start=10.dp).heightIn(max = maxHeight)) {
                        items(iperfMessages.size) { index ->
                            IperfMessageItem(iperfMessages[index], style)
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun IperfMessageItem(text: String, style: TextStyle) {
    val linkColor = MaterialTheme.colorScheme.primary
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = linkify(text, linkColor),
            textAlign = TextAlign.Left,
            style = style,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


