package com.jabramson.iperf3_network_tester_kmp.view
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.jabramson.iperf3_network_tester_kmp.ui.theme.mesloFontBoldFamily
import com.jabramson.iperf3_network_tester_kmp.ui.theme.mesloFontFamily
import com.jabramson.iperf3_network_tester_kmp.ui.theme.mesloFontItalicFamily

@Composable
fun mesloMonoTextStyle(): TextStyle = TextStyle(
    fontFamily = mesloFontFamily(),
    fontSize = 14.sp,
    letterSpacing = 0.2.sp,
    color = MaterialTheme.colorScheme.onSurface
)

@Composable
fun mesloMonoULTextStyle(): TextStyle = TextStyle(
    fontFamily = mesloFontFamily(),
    textDecoration = TextDecoration.Underline,
    fontSize = 14.sp,
    letterSpacing = 0.2.sp,
    color = MaterialTheme.colorScheme.onSurface
)

@Composable
fun mesloMonoItalicTextStyle(): TextStyle = TextStyle(
    fontFamily = mesloFontItalicFamily(),
    fontSize = 14.sp,
    letterSpacing = 0.2.sp,
    color = MaterialTheme.colorScheme.onSurface
)

@Composable
fun mesloMonoBoldTextStyle(): TextStyle = TextStyle(
    fontFamily = mesloFontBoldFamily(),
    fontSize = 14.sp,
    letterSpacing = 0.2.sp,
    color = MaterialTheme.colorScheme.onSurface
)
