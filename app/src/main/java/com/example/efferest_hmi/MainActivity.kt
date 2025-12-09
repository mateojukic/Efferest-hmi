package com.example.efferest_hmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.efferest_hmi.data.CarHvacRepository
import com.example.efferest_hmi.model.UIVersion
import com.example.efferest_hmi.ui.HvacViewModel
import com.example.efferest_hmi.ui.components.VersionAView
import com.example.efferest_hmi.ui.components.VersionBView
import com.example.efferest_hmi.ui.components.VersionCView
import com.example.efferest_hmi.ui.theme.EfferestTheme
import com.example.efferest_hmi.util.SoundEffects

class MainActivity : ComponentActivity() {

    private val viewModel: HvacViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HvacViewModel(CarHvacRepository(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EfferestTheme {
                val uiState by viewModel.uiState.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Main Content Layer
                        when (uiState.version) {
                            UIVersion.VERSION_A -> VersionAView(viewModel)
                            UIVersion.VERSION_B -> VersionBView(viewModel)
                            UIVersion.VERSION_C -> VersionCView(viewModel)
                        }

                        // 2. Floating Action Buttons Layer (Aligned Bottom End)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            // Reset Button
                            SmallFloatingActionButton(
                                onClick = {
                                    viewModel.resetToDefaults()
                                    SoundEffects.playBeep()
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Default Settings",
                                    tint = Color.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Cycle Version Button (2. CHANGE: Icon replaced)
                            SmallFloatingActionButton(
                                onClick = { viewModel.cycleVersion() },
                                containerColor = Color.DarkGray,
                                contentColor = Color.White,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowForward,
                                    contentDescription = "Next Version",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}