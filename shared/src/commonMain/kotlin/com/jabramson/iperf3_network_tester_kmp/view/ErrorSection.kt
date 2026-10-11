package com.jabramson.iperf3_network_tester_kmp.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiExecutionData

@Preview(name = "Error Section Preview", showBackground = true,
    device = "spec:width=411dp,height=891dp"
)
@Composable
fun ErrorSection(
    errorLines: List<String> = getSampleUiState(true).errorLines,
    monoStyle: TextStyle = mesloMonoTextStyle(),
    isWide: Boolean = false,
    isTall: Boolean = false,
    isTiny: Boolean = false
) {
    if (errorLines.isEmpty()) return
    val fontSize = if (isTiny) 12.sp
    else if (isTall || isWide) 20.sp
    else 15.sp
    val style = monoStyle.copy(fontSize =  fontSize)
    var totalOutput: Int = 0
    errorLines.forEach  { l -> totalOutput += l.length }


    //if (errorLines.size > 2 || totalOutput > 80) style = monoStyle.copy(fontSize =  12.sp)
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.padding(start = 10.dp, end =  10.dp).fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.height(3.dp))
        HorizontalDivider(
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(3.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(errorLines.size) { index ->
                ErrorLineItem(errorLines[index], style)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun ErrorLineItem(text: String, style: TextStyle) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Left,
            style = style,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

