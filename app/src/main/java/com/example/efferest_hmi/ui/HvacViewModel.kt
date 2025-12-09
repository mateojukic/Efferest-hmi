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

    // Job to handle the temporary fan boost so we can cancel it if needed
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

    // --- Temperature Controls ---
    fun toggleWarm(zone: BodyZone) {
        repo.warm()
        pushGlobalTemp()
    }

    fun toggleCold(zone: BodyZone) {
        repo.cool()
        pushGlobalTemp()
    }

    fun increaseGlobalTemp() { repo.warm(); pushGlobalTemp() }
    fun decreaseGlobalTemp() { repo.cool(); pushGlobalTemp() }

    fun setTargetTemperature(temp: Int) {
        repo.setGlobalTemperature(temp)
        pushGlobalTemp()
    }

    private fun pushGlobalTemp() {
        _uiState.update { s -> s.copy(globalTemperature = repo.getGlobalTemperature()) }
    }

    // --- Fan Controls ---
    fun setFanSpeed(level: Int) {
        val safeLevel = level.coerceIn(0, 5)
        repo.setFanSpeed(safeLevel)
        _uiState.update { it.copy(fanSpeed = repo.getFanSpeed()) }

        // If the user manually changes fan speed, we should probably cancel any auto-boost
        fanBoostJob?.cancel()
    }

    fun setFanDirection(direction: FanDirection) {
        _uiState.update { state ->
            val newSpeed = if (state.fanSpeed == 0) 1 else state.fanSpeed
            if (newSpeed != state.fanSpeed) {
                repo.setFanSpeed(newSpeed)
            }
            state.copy(fanDirection = direction, fanSpeed = newSpeed)
        }
    }

    // --- Temporary Fan Boost Logic ---
    fun triggerFanBoost() {
        // Cancel any existing boost job to restart the timer
        fanBoostJob?.cancel()

        fanBoostJob = viewModelScope.launch {
            // 1. Boost to Level 5
            setFanSpeed(5)

            // 2. Wait for 5 seconds
            delay(5000L)

            // 3. Revert to Default Level 2
            setFanSpeed(2)
        }
    }

    // --- Reset Logic ---
    fun resetToDefaults() {
        // Cancel boost if running so it doesn't override the reset later
        fanBoostJob?.cancel()

        repo.setGlobalTemperature(21)
        repo.setFanSpeed(1)
        refreshState()
    }
}