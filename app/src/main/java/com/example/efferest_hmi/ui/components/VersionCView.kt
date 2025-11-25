package com.example.efferest_hmi.ui.components

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.efferest_hmi.ui.HvacViewModel
import com.example.efferest_hmi.util.HVAC_TAG
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VersionCView(
    viewModel: HvacViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun showStatus(msg: String) {
        statusMessage = msg
        Log.d(HVAC_TAG, msg)
        coroutineScope.launch {
            delay(2000L)
            if (statusMessage == msg) statusMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 480.dp)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Global Temperature: ${uiState.globalTemperature}°C", color = Color.White)

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OutlinedButton(onClick = {
                viewModel.decreaseGlobalTemp()
                showStatus("Global temp decreased to -> ${viewModel.uiState.value.globalTemperature}°C")
            }) { Text("-") }
            OutlinedButton(onClick = {
                viewModel.increaseGlobalTemp()
                showStatus("Global temp increased to -> ${viewModel.uiState.value.globalTemperature}°C")
            }) { Text("+") }
        }

        if (statusMessage != null) {
            Text(
                text = statusMessage!!,
                color = Color.LightGray,
                modifier = Modifier.align(Alignment.Start)
            )
        }
    }
}