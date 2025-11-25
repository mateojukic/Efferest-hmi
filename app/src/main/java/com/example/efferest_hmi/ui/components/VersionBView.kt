package com.example.efferest_hmi.ui.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.efferest_hmi.R
import com.example.efferest_hmi.model.BodyZone
import com.example.efferest_hmi.model.ZoneAction
import com.example.efferest_hmi.ui.HvacViewModel
import com.example.efferest_hmi.util.HVAC_TAG
import com.example.efferest_hmi.util.SoundEffects
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VersionBView(
    viewModel: HvacViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SoundEffects.ensureInit(context)
    }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    fun showStatus(msg: String) {
        statusMessage = msg
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
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        BodyZone.values().forEach { zone ->
            val action = uiState.zoneActions[zone] ?: ZoneAction.NONE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WarmIconButton(
                    zone = zone,
                    active = action == ZoneAction.WARM_ACTIVE,
                    onClick = {
                        viewModel.toggleWarm(zone)
                        SoundEffects.playBeep()
                        val msg = "Warm toggled ${zoneLabel(zone)}"
                        Log.d(HVAC_TAG, msg)
                        showStatus(msg)
                    },
                    modifier = Modifier.weight(1f)
                )
                ColdIconButton(
                    zone = zone,
                    active = action == ZoneAction.COLD_ACTIVE,
                    onClick = {
                        viewModel.toggleCold(zone)
                        SoundEffects.playBeep()
                        val msg = "Cold toggled ${zoneLabel(zone)}"
                        Log.d(HVAC_TAG, msg)
                        showStatus(msg)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
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

@Composable
private fun WarmIconButton(
    zone: BodyZone,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFFB71C1C) else Color(0xFFE57373),
            contentColor = Color.White
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_warm),
                contentDescription = "Warm icon"
            )
            Text(if (active) "WARM (active)\n${zoneLabel(zone)}" else "Warm\n${zoneLabel(zone)}")
        }
    }
}

@Composable
private fun ColdIconButton(
    zone: BodyZone,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF0D47A1) else Color(0xFF64B5F6),
            contentColor = Color.White
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_cold),
                contentDescription = "Cold icon"
            )
            Text(if (active) "COLD (active)\n${zoneLabel(zone)}" else "Cold\n${zoneLabel(zone)}")
        }
    }
}

private fun zoneLabel(zone: BodyZone): String = when (zone) {
    BodyZone.UPPER -> "Windshield"
    BodyZone.MIDDLE -> "Front"
    BodyZone.LOWER -> "Feet"
}