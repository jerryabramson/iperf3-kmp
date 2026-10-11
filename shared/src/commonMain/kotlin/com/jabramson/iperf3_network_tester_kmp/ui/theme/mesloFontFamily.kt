package com.jabramson.iperf3_network_tester_kmp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import iperf3kmp.shared.generated.resources.Res

import iperf3kmp.shared.generated.resources.meslonerdfont
import iperf3kmp.shared.generated.resources.meslonerdfontbold
import iperf3kmp.shared.generated.resources.meslonerdfontitalic

import org.jetbrains.compose.resources.Font


@Composable
fun mesloFontFamily(): FontFamily = FontFamily(Font(Res.font.meslonerdfont))

@Composable
fun mesloFontItalicFamily(): FontFamily = FontFamily(Font(Res.font.meslonerdfontitalic))

@Composable
fun mesloFontBoldFamily(): FontFamily = FontFamily(Font(Res.font.meslonerdfontbold))
