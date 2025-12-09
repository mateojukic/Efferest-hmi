package com.example.efferest_hmi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.efferest_hmi.data.HvacRepository
import com.example.efferest_hmi.model.BodyZone
import com.example.efferest_hmi.model.UIVersion
import com.example.efferest_hmi.model.ZoneAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val fanSpeed: Int = 0,
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
            zoneActions = BodyZone.values().associateWith { ZoneAction.NONE },
            fanSpeed = repo.getFanSpeed()
        )
    )
    val uiState: StateFlow<HvacUiState> = _uiState

    private var fanBoostJob: Job? = null

    init {
        (repo as? com.example.efferest_hmi.data.CarHvacRepository)?.let { carRepo ->
            viewModelScope.launch {
                carRepo.connect()
                refreshState()
            }
        }
    }

    private fun refreshState() {
        _uiState.update { s ->
            s.copy(
                globalTemperature = repo.getGlobalTemperature(),
                zoneTemperatures = BodyZone.values().associateWith { repo.getZoneTemperature(it) },
                minTemp = repo.minTemp,
                maxTemp = repo.maxTemp,
                fanSpeed = repo.getFanSpeed()
            )
        }
    }

    fun cycleVersion() {
        _uiState.update { s -> s.copy(version = s.version.next()) }
    }

    // Temperature Controls
    fun toggleWarm(zone: BodyZone) { setTargetTemperature(24) }
    fun toggleCold(zone: BodyZone) { setTargetTemperature(18) }
    fun increaseGlobalTemp() { repo.warm(); pushGlobalTemp() }
    fun decreaseGlobalTemp() { repo.cool(); pushGlobalTemp() }

    fun setTargetTemperature(temp: Int) {
        repo.setGlobalTemperature(temp)
        pushGlobalTemp()
    }

    private fun pushGlobalTemp() {
        _uiState.update { s -> s.copy(globalTemperature = repo.getGlobalTemperature()) }
    }

    // Fan Controls
    fun setFanSpeed(level: Int) {
        val safeLevel = level.coerceIn(0, 5)
        repo.setFanSpeed(safeLevel)
        _uiState.update { it.copy(fanSpeed = repo.getFanSpeed()) }
        fanBoostJob?.cancel()
    }

    fun setFanDirection(direction: FanDirection) {
        _uiState.update { state ->
            val newSpeed = if (state.fanSpeed == 0) 1 else state.fanSpeed
            if (newSpeed != state.fanSpeed) {
                repo.setFanSpeed(newSpeed)
            }
            // IMPORTANT: Call Repo to set physical direction
            repo.setFanDirection(direction)

            state.copy(fanDirection = direction, fanSpeed = newSpeed)
        }
    }

    fun triggerFanBoost() {
        fanBoostJob?.cancel()
        fanBoostJob = viewModelScope.launch {
            setFanSpeed(5)
            delay(5000L)
            setFanSpeed(2)
        }
    }

    fun resetToDefaults() {
        fanBoostJob?.cancel()
        repo.setGlobalTemperature(21)
        repo.setFanSpeed(2)
        refreshState()
    }
}