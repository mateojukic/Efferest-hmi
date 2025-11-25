package com.example.efferest_hmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
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
import com.example.efferest_hmi.data.InMemoryHvacRepository
import com.example.efferest_hmi.model.UIVersion
import com.example.efferest_hmi.ui.HvacViewModel
import com.example.efferest_hmi.ui.components.VersionAView
import com.example.efferest_hmi.ui.components.VersionBView
import com.example.efferest_hmi.ui.components.VersionCView
import com.example.efferest_hmi.ui.theme.EfferestTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HvacViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HvacViewModel(InMemoryHvacRepository()) as T
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
                        when (uiState.version) {
                            UIVersion.VERSION_A -> VersionAView(viewModel)
                            UIVersion.VERSION_B -> VersionBView(viewModel)
                            UIVersion.VERSION_C -> VersionCView(viewModel)
                        }

                        SmallFloatingActionButton(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp),
                            onClick = { viewModel.cycleVersion() },
                            containerColor = Color.DarkGray,
                            contentColor = Color.White
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cached,
                                contentDescription = "Cycle Version"
                            )
                        }
                    }
                }
            }
        }
    }
}
