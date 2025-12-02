package com.example.efferest_hmi.ui.components

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.efferest_hmi.R
import com.example.efferest_hmi.model.BodyZone
import com.example.efferest_hmi.model.ZoneAction
import com.example.efferest_hmi.ui.HvacViewModel
import com.example.efferest_hmi.util.SoundEffects
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VersionAView(
    viewModel: HvacViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SoundEffects.ensureInit(context)
    }

    var statusState by remember { mutableStateOf<Pair<String, Color>?>(null) }

    fun showStatus(msg: String, color: Color) {
        statusState = msg to color
        coroutineScope.launch {
            delay(5000L)
            if (statusState?.first == msg) statusState = null
        }
    }

    val warmColor = Color(0xFFE57373) // Red-ish
    val coldColor = Color(0xFF64B5F6) // Blue-ish

    Row(modifier = Modifier.fillMaxSize()) {

        // LEFT HALF (Content with Dark Grey Background)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Mir ist...",
                    color = Color.LightGray,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // --- WARM MANNEQUIN (Left Visual) ---
                    // LOGIC FLIP: Clicking this now triggers COLD action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((-25).dp) // Increased negative space for larger images
                    ) {
                        Text("WARM", color = warmColor, fontSize = 14.sp, modifier = Modifier.padding(bottom = 30.dp))

                        MannequinPart(
                            imageResId = R.drawable.img_warm_head,
                            isActive = uiState.zoneActions[BodyZone.UPPER] == ZoneAction.WARM_ACTIVE,
                            height = 126.dp, // +20% Size (was 105)
                            onClick = {
                                // FLIP: Warm visual -> Toggle Cold
                                viewModel.toggleCold(BodyZone.UPPER)
                                SoundEffects.playBeep()
                                showStatus("Kühle Kopf...", coldColor)
                            }
                        )
                        MannequinPart(
                            imageResId = R.drawable.img_warm_body,
                            isActive = uiState.zoneActions[BodyZone.MIDDLE] == ZoneAction.WARM_ACTIVE,
                            height = 162.dp, // +20% Size (was 135)
                            onClick = {
                                // FLIP: Warm visual -> Toggle Cold
                                viewModel.toggleCold(BodyZone.MIDDLE)
                                SoundEffects.playBeep()
                                showStatus("Kühle Oberkörper...", coldColor)
                            }
                        )
                        MannequinPart(
                            imageResId = R.drawable.img_warm_feet,
                            isActive = uiState.zoneActions[BodyZone.LOWER] == ZoneAction.WARM_ACTIVE,
                            height = 150.dp, // +20% Size (was 125)
                            onClick = {
                                // FLIP: Warm visual -> Toggle Cold
                                viewModel.toggleCold(BodyZone.LOWER)
                                SoundEffects.playBeep()
                                showStatus("Kühle Füße...", coldColor)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(64.dp))

                    // --- COLD MANNEQUIN (Right Visual) ---
                    // LOGIC FLIP: Clicking this now triggers WARM action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((-25).dp)
                    ) {
                        Text("KALT", color = coldColor, fontSize = 14.sp, modifier = Modifier.padding(bottom = 30.dp))

                        MannequinPart(
                            imageResId = R.drawable.img_cold_head,
                            isActive = uiState.zoneActions[BodyZone.UPPER] == ZoneAction.COLD_ACTIVE,
                            height = 126.dp, // +20% Size
                            onClick = {
                                // FLIP: Cold visual -> Toggle Warm
                                viewModel.toggleWarm(BodyZone.UPPER)
                                SoundEffects.playBeep()
                                showStatus("Beheize Kopf...", warmColor)
                            }
                        )
                        MannequinPart(
                            imageResId = R.drawable.img_cold_body,
                            isActive = uiState.zoneActions[BodyZone.MIDDLE] == ZoneAction.COLD_ACTIVE,
                            height = 162.dp, // +20% Size
                            onClick = {
                                // FLIP: Cold visual -> Toggle Warm
                                viewModel.toggleWarm(BodyZone.MIDDLE)
                                SoundEffects.playBeep()
                                showStatus("Beheize Oberkörper...", warmColor)
                            }
                        )
                        MannequinPart(
                            imageResId = R.drawable.img_cold_feet,
                            isActive = uiState.zoneActions[BodyZone.LOWER] == ZoneAction.COLD_ACTIVE,
                            height = 150.dp, // +20% Size
                            onClick = {
                                // FLIP: Cold visual -> Toggle Warm
                                viewModel.toggleWarm(BodyZone.LOWER)
                                SoundEffects.playBeep()
                                showStatus("Beheize Füße...", warmColor)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = statusState?.first ?: "",
                    color = statusState?.second ?: MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        // RIGHT HALF (Empty)
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun MannequinPart(
    imageResId: Int,
    isActive: Boolean,
    height: Dp,
    onClick: () -> Unit
) {
    var isHighlighted by remember { mutableStateOf(false) }

    val isFullyVisible = isActive || isHighlighted

    val alpha by animateFloatAsState(
        targetValue = if (isFullyVisible) 1.0f else 0.4f,
        label = "alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isFullyVisible) 1.1f else 1.0f,
        label = "scale"
    )

    // 1. SIZE INCREASE: Width 150 -> 180
    val width = 180.dp
    val shape = RoundedCornerShape(12.dp)

    fun handleClick() {
        onClick()
        isHighlighted = true
    }

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            delay(5000)
            isHighlighted = false
        }
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .scale(scale)
            .alpha(alpha)
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { handleClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = imageResId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}