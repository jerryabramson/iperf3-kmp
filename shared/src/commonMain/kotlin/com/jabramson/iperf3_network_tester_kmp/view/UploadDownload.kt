package com.jabramson.iperf3_network_tester_kmp.view


//import android.R.attr.onClick
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton


import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiExecutionData
//import java.util.Locale.getDefault
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UploadDownload.DOWNLOAD
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UploadDownload.UPLOAD
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UploadDownload.isDownload


/**
 * Upload/Download radio buttons for the UI
 * @param viewModel the view model for the UI
 */
@Preview("Upload/Download Radio Button", showBackground = true, device = "id:pixel_9",
    showSystemUi = true)
@Composable
fun UploadDownload(
    currentReverse: Boolean = true,
    isRunning: Boolean = false,
    setUploadDownload: (String) -> Unit = {})
{
    val color = if (isRunning) RadioButtonDefaults.colors().disabledSelectedColor else RadioButtonDefaults.colors().selectedColor
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.selectableGroup().wrapContentWidth()
    )
    {
        listOf(DOWNLOAD, UPLOAD).forEach { text ->
            SelectableOption(
                enabled = !isRunning,
                selected = currentReverse == isDownload(text),
                onClick = { if (!isRunning) setUploadDownload(text) }
            ) {
                val textColor =
                    if (isRunning && currentReverse == isDownload(text))
                        MaterialTheme.colorScheme.error
                    else
                        color

                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun SelectableOption(enabled: Boolean = true, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val selectedColor = if (!enabled) RadioButtonDefaults.colors().disabledSelectedColor else RadioButtonDefaults.colors().selectedColor
    val unSelectedColor = if (!enabled) RadioButtonDefaults.colors().disabledUnselectedColor else RadioButtonDefaults.colors().unselectedColor
    Row(
        Modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.Checkbox
        ),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        RadioButton(selected = selected,
            onClick = onClick,
            modifier = Modifier.height(25.dp),
            colors = if (selected) RadioButtonDefaults.colors(selectedColor = selectedColor) else RadioButtonDefaults.colors(unselectedColor = unSelectedColor)
        )
        content()
    }
}
