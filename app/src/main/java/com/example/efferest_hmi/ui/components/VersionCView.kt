package com.example.efferest_hmi.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.efferest_hmi.R
import com.example.efferest_hmi.ui.FanDirection
import com.example.efferest_hmi.ui.HvacViewModel
import com.example.efferest_hmi.util.SoundEffects
import kotlinx.coroutines.launch

@Composable
fun VersionCView(
    viewModel: HvacViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // Colors
    val warmColor = Color(0xFFE57373)
    val coldColor = Color(0xFF64B5F6)
    val panelBackground = Color(0xFF2C2C2C)
    val activeHighlight = Color(0xFF80CBC4)
    val inactiveGrey = Color(0xFF424242)

    Row(modifier = Modifier.fillMaxSize()) {

        // LEFT HALF (Controls)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 600.dp) // Slightly wider for 150dp buttons
            ) {

                // 1. TEMPERATURE CONTAINER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(panelBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel.decreaseGlobalTemp()
                                SoundEffects.playBeep()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = coldColor),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.size(width = 120.dp, height = 120.dp)
                        ) {
                            Text("-", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "${uiState.globalTemperature}°C",
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = {
                                viewModel.increaseGlobalTemp()
                                SoundEffects.playBeep()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = warmColor),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.size(width = 120.dp, height = 120.dp)
                        ) {
                            Text("+", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. FAN CONTROL CONTAINER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(panelBackground)
                        .padding(24.dp)
                ) {
                    // --- Fan Direction Header ---
                    Text("Fan Direction", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

                    // --- Fan Direction Buttons (Row of 3 - Removed Defrost) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FanDirectionButton(
                            iconRes = R.drawable.ic_fan_front,
                            isActive = uiState.fanDirection == FanDirection.FRONTAL,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanDirection(FanDirection.FRONTAL); SoundEffects.playBeep() }
                        )
                        FanDirectionButton(
                            iconRes = R.drawable.ic_fan_mix,
                            isActive = uiState.fanDirection == FanDirection.FRONTAL_FEET,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanDirection(FanDirection.FRONTAL_FEET); SoundEffects.playBeep() }
                        )
                        FanDirectionButton(
                            iconRes = R.drawable.ic_fan_feet,
                            isActive = uiState.fanDirection == FanDirection.FEET,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanDirection(FanDirection.FEET); SoundEffects.playBeep() }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- Fan Speed Header ---
                    Text("Fan Speed", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

                    // --- Fan Speed Buttons (Low, Mid, High) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // LOW = Level 2
                        FanSpeedButton(
                            label = "LOW",
                            isActive = uiState.fanSpeed == 2,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanSpeed(2); SoundEffects.playBeep() }
                        )

                        // MID = Level 4
                        FanSpeedButton(
                            label = "MID",
                            isActive = uiState.fanSpeed == 4,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanSpeed(4); SoundEffects.playBeep() }
                        )

                        // HIGH = Level 6 (or Max)
                        // Note: If car only supports up to 5, this might clamp to 5 in ViewModel
                        FanSpeedButton(
                            label = "HIGH",
                            isActive = uiState.fanSpeed >= 5,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanSpeed(6); SoundEffects.playBeep() }
                        )
                    }
                }
            }
        }

        // RIGHT HALF (Empty)
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun FanDirectionButton(
    iconRes: Int,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit
) {
    // Large Square Button: 150dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(120.dp) // SIZE CHANGE
            .clip(RoundedCornerShape(24.dp))
            .background(if (isActive) activeColor else inactiveColor)
            .clickable { onClick() }
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (isActive) Color.Black else Color.LightGray),
            modifier = Modifier.size(80.dp) // Scaled up icon
        )
    }
}

@Composable
fun FanSpeedButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit
) {
    // Large Square Button: 150dp
    Box(
        modifier = Modifier
            .size(120.dp) // SIZE CHANGE
            .clip(RoundedCornerShape(24.dp))
            .background(if (isActive) activeColor else inactiveColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color.Black else Color.LightGray
        )
    }
}