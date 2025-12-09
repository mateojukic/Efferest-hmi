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
                modifier = Modifier.widthIn(max = 550.dp)
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
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(width = 70.dp, height = 55.dp)
                        ) {
                            Text("-", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "${uiState.globalTemperature}°C",
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = {
                                viewModel.increaseGlobalTemp()
                                SoundEffects.playBeep()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = warmColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(width = 70.dp, height = 55.dp)
                        ) {
                            Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
                    Text("Fan Direction", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))

                    // --- Fan Direction Buttons ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Logic Update: Directions are ONLY active if selected AND Fan Speed > 0
                        val isFanOn = uiState.fanSpeed > 0

                        FanDirectionButton(
                            iconRes = R.drawable.ic_fan_front,
                            isActive = isFanOn && uiState.fanDirection == FanDirection.FRONTAL,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanDirection(FanDirection.FRONTAL); SoundEffects.playBeep() }
                        )
                        FanDirectionButton(
                            iconRes = R.drawable.ic_fan_mix,
                            isActive = isFanOn && uiState.fanDirection == FanDirection.FRONTAL_FEET,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanDirection(FanDirection.FRONTAL_FEET); SoundEffects.playBeep() }
                        )
                        FanDirectionButton(
                            iconRes = R.drawable.ic_fan_feet,
                            isActive = isFanOn && uiState.fanDirection == FanDirection.FEET,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanDirection(FanDirection.FEET); SoundEffects.playBeep() }
                        )
                        FanDirectionButton(
                            iconRes = R.drawable.ic_fan_defrost,
                            isActive = isFanOn && uiState.fanDirection == FanDirection.FEET_WINDSHIELD,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanDirection(FanDirection.FEET_WINDSHIELD); SoundEffects.playBeep() }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Fan Speed", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))

                    // --- Fan Speed Bar ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        FanSpeedSegment(
                            label = "OFF",
                            isActive = uiState.fanSpeed == 0,
                            activeColor = Color(0xFFD32F2F),
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanSpeed(0); SoundEffects.playBeep() },
                            modifier = Modifier.weight(1f)
                        )

                        for (i in 1..5) {
                            FanSpeedSegment(
                                label = if (i == 5) "MAX" else "$i",
                                isActive = uiState.fanSpeed >= i,
                                activeColor = activeHighlight,
                                inactiveColor = inactiveGrey,
                                onClick = { viewModel.setFanSpeed(i); SoundEffects.playBeep() },
                                modifier = Modifier.weight(1f)
                            )
                        }
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) activeColor else inactiveColor)
            .clickable { onClick() }
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (isActive) Color.Black else Color.LightGray),
            modifier = Modifier.size(42.dp)
        )
    }
}

@Composable
fun FanSpeedSegment(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (isActive) activeColor else inactiveColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color.Black else Color.Gray
        )
    }
}