package com.example.efferest_hmi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.efferest_hmi.data.HvacRepository
import com.example.efferest_hmi.model.BodyZone
import com.example.efferest_hmi.model.UIVersion
import com.example.efferest_hmi.model.ZoneAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FanDirection {
    FRONTAL,
    FRONTAL_FEET,
    FEET,
    FEET_WINDSHIELD
}

data class HvacUiState(
    val version: UIVersion = UIVersion.VERSION_A,
    val zoneTemperatures: Map<BodyZone, Int> = emptyMap(),
    val globalTemperature: Int = 22,
    val minTemp: Int = 16,
    val maxTemp: Int = 28,
    val zoneActions: Map<BodyZone, ZoneAction> = BodyZone.values().associateWith { ZoneAction.NONE },
    val fanSpeed: Int = 0, // 0 = Off, 1-5 = Levels
    val fanDirection: FanDirection = FanDirection.FRONTAL
)

class HvacViewModel(
    private val repo: HvacRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HvacUiState(
            version = UIVersion.VERSION_A,
            zoneTemperatures = BodyZone.values().associateWith { repo.getZoneTemperature(it) },
            globalTemperature = repo.getGlobalTemperature(),
            minTemp = repo.minTemp,
            maxTemp = repo.maxTemp,
            zoneActions = BodyZone.values().associateWith { ZoneAction.NONE }
        )
    )
    val uiState: StateFlow<HvacUiState> = _uiState

    init {
        (repo as? com.example.efferest_hmi.data.CarHvacRepository)?.let { carRepo ->
            viewModelScope.launch {
                carRepo.connect()
                _uiState.update { s ->
                    s.copy(
                        globalTemperature = repo.getGlobalTemperature(),
                        zoneTemperatures = BodyZone.values().associateWith { repo.getZoneTemperature(it) },
                        minTemp = repo.minTemp,
                        maxTemp = repo.maxTemp
                    )
                }
            }
        }
    }

    fun cycleVersion() {
        _uiState.update { s -> s.copy(version = s.version.next()) }
    }

    fun toggleWarm(zone: BodyZone) {
        repo.warm()
        pushGlobalTemp()
    }

    fun toggleCold(zone: BodyZone) {
        repo.cool()
        pushGlobalTemp()
    }

    fun increaseGlobalTemp() {
        repo.warm()
        pushGlobalTemp()
    }

    fun decreaseGlobalTemp() {
        repo.cool()
        pushGlobalTemp()
    }

    // --- Fan Controls ---

    fun setFanSpeed(level: Int) {
        _uiState.update { it.copy(fanSpeed = level.coerceIn(0, 5)) }
    }

    fun setFanDirection(direction: FanDirection) {
        _uiState.update { state ->
            // Logic: If fan was OFF (0), jump to Level 1 when direction is pressed.
            val newSpeed = if (state.fanSpeed == 0) 1 else state.fanSpeed
            state.copy(fanDirection = direction, fanSpeed = newSpeed)
        }
    }

    private fun pushGlobalTemp() {
        _uiState.update { s -> s.copy(globalTemperature = repo.getGlobalTemperature()) }
    }
}