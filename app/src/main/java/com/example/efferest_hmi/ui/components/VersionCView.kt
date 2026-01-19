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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.efferest_hmi.R
import com.example.efferest_hmi.ui.FanDirection
import com.example.efferest_hmi.ui.HvacViewModel
import com.example.efferest_hmi.util.SoundEffects
import kotlinx.coroutines.launch

// Define a constant size for all square buttons
private val buttonSize = 150.dp

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

    // Calculate font size relative to button size
    val density = LocalDensity.current
    val tempButtonFontSize = with(density) { (buttonSize / 3).toSp() }

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
                modifier = Modifier.widthIn(max = 600.dp)
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
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Text("-", fontSize = tempButtonFontSize, fontWeight = FontWeight.Bold)
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
                            modifier = Modifier.size(buttonSize)
                        ) {
                            Text("+", fontSize = tempButtonFontSize, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. FAN DIRECTION CONTAINER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(panelBackground)
                        .padding(24.dp)
                ) {

                    // --- Fan Direction Buttons ---
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
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. FAN SPEED CONTAINER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(panelBackground)
                        .padding(24.dp)
                ) {

                    // --- Fan Speed Buttons ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FanSpeedButton(
                            iconRes = R.drawable.fan_low, // Using user-provided PNG
                            isActive = uiState.fanSpeed == 2,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanSpeed(2); SoundEffects.playBeep() }
                        )

                        FanSpeedButton(
                            iconRes = R.drawable.fan_mid, // Using user-provided PNG
                            isActive = uiState.fanSpeed == 4,
                            activeColor = activeHighlight,
                            inactiveColor = inactiveGrey,
                            onClick = { viewModel.setFanSpeed(4); SoundEffects.playBeep() }
                        )

                        FanSpeedButton(
                            iconRes = R.drawable.fan_high, // Using user-provided PNG
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(buttonSize)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isActive) activeColor else inactiveColor)
            .clickable { onClick() }
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (isActive) Color.Black else Color.LightGray),
            modifier = Modifier.size(80.dp)
        )
    }
}

@Composable
fun FanSpeedButton(
    iconRes: Int, // Changed to accept icon resource ID
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isActive) activeColor else inactiveColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            // Assuming white/light grey icons on dark background, or tinted like direction buttons
            // If the PNGs are colored, remove colorFilter.
            // Assuming they are monochrome/stencil style:
            colorFilter = ColorFilter.tint(if (isActive) Color.Black else Color.LightGray),
            modifier = Modifier.size(80.dp)
        )
    }
}