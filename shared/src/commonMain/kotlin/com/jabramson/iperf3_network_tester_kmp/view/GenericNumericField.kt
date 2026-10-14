/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view

//import android.R.attr.defaultValue
//import android.R.attr.enabled
//import android.R.attr.label
import androidx.collection.emptyLongSet
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults.textFieldColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview


/**
 * Used for numeric fields in the UI. Expected to be part of a row or column.

 * @param onValueChange the callback to be called when the value changes
 * @param enabled whether the field is enabled
 * @param label the label text to be displayed next to the field
 * @param modifier the modifier to be applied to the field
 * @param colors the colors for the text field
 *
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Generic Numeric Input Field", device = "spec:width=411dp,height=891dp", showSystemUi = true)
@Composable
fun GenericNumericField(
    modifier: Modifier = Modifier,
    currentValue: Int = -1,
    onValueChange: (String) -> Unit = {},
    enabled: Boolean = true,
    defaultValue: Int = 123,
    label: String = "Numeric Label",
    colors: TextFieldColors = textFieldColors())
{
    val placeHolder = if (currentValue == -1) defaultValue.toString() else currentValue.toString()
    val valString = if (currentValue == -1) "" else currentValue.toString()
    TextField(
        value = valString,
        onValueChange = onValueChange,
        enabled = enabled,
        placeholder = {
            Text(text = placeHolder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
        modifier = modifier,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        colors = if (enabled) colors else colors.copy(disabledTextColor = MaterialTheme.colorScheme.error),
        singleLine = true
    )
}




