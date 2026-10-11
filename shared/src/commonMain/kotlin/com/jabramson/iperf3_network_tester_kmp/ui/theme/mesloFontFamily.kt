package com.jabramson.iperf3_network_tester_kmp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import iperf3kmp.shared.generated.resources.Res

import iperf3kmp.shared.generated.resources.jetbrainsmononerdfont
import iperf3kmp.shared.generated.resources.jetbrainsmononerdfontbold
import iperf3kmp.shared.generated.resources.jetbrainsmononerdfontitalic

import org.jetbrains.compose.resources.Font

// These functions are named "meslo*" for historical reasons: the app originally used
// Meslo Nerd Font, but that variant is patched from Apple's proprietary Menlo and isn't
// freely redistributable. It was swapped for JetBrains Mono Nerd Font (SIL OFL 1.1,
// see THIRD_PARTY_LICENSES/JetBrainsMonoNerdFont-OFL.txt), same glyph set, no license
// restriction on redistribution. Function names kept as-is to avoid touching every call site.

@Composable
fun mesloFontFamily(): FontFamily = FontFamily(Font(Res.font.jetbrainsmononerdfont))

@Composable
fun mesloFontItalicFamily(): FontFamily = FontFamily(Font(Res.font.jetbrainsmononerdfontitalic))

@Composable
fun mesloFontBoldFamily(): FontFamily = FontFamily(Font(Res.font.jetbrainsmononerdfontbold))
