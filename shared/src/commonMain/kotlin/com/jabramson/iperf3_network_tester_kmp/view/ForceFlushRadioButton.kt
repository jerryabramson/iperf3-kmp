package com.jabramson.iperf3_network_tester_kmp.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData

@Preview(name ="forceFlushRadioButtonPreview", showBackground = true,
    device = "spec:width=411dp,height=891dp"
)
@Composable
fun ForceFlushRadioButton(uiState: UiInputData = getSampleInputData(),
                          toggleForceFlush: () -> Unit = {}
) {

    val buttonColor =
        if (uiState.isForceFlush) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    // Note that Modifier.selectableGroup() is essential to ensure correct accessibility behavior
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "Force Flush",
            modifier = Modifier.padding(end = 10.dp),
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = toggleForceFlush, // 1. Flip state on click
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(1.dp)//.width(240.dp).height(50.dp)
        ) {
            if (uiState.isForceFlush) {
                Text(
                    text = "Turn Off",
                    color = MaterialTheme.colorScheme.surface,
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Text(
                    text = "Turn On",
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
    toggleForceFlush()
}

