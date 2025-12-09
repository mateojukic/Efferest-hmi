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
import java.lang.System.currentTimeMillis

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

    private var bufferJob: Job? = null
    // New: Track ALL active zones in the current buffer session
    private val bufferActiveZones = mutableSetOf<BodyZone>()
    private var lastClickTime: Long = 0
    private val BUFFER_DURATION = 10000L

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

    // --- Version A Specific Logic (Zone Touches) ---

    fun handleZoneTouch(zone: BodyZone, isWarm: Boolean) {
        val currentTime = currentTimeMillis()
        val isWithinBuffer = (currentTime - lastClickTime) < BUFFER_DURATION
        val action = if (isWarm) ZoneAction.WARM_ACTIVE else ZoneAction.COLD_ACTIVE

        // 1. Accumulate Active Zones
        if (isWithinBuffer) {
            // Add new zone to existing set
            bufferActiveZones.add(zone)
        } else {
            // New session: clear previous and start fresh
            bufferActiveZones.clear()
            bufferActiveZones.add(zone)
        }

        // 2. Determine Fan Direction based on accumulated zones
        var targetDirection = FanDirection.FRONTAL

        // Logic: If multiple zones are active, default to MIX.
        // If only one, map specifically.
        if (bufferActiveZones.size > 1) {
            targetDirection = FanDirection.FRONTAL_FEET
        } else {
            // Single zone logic
            targetDirection = when (bufferActiveZones.first()) {
                BodyZone.UPPER -> FanDirection.FRONTAL
                BodyZone.MIDDLE -> FanDirection.FRONTAL_FEET
                BodyZone.LOWER -> FanDirection.FEET
            }
        }

        // 3. Apply Changes
        setFanDirection(targetDirection)

        val targetTemp = if (isWarm) 24 else 18
        setTargetTemperature(targetTemp)

        // 4. Restart Buffer Timer
        startBufferTimer()

        // 5. Update UI Highlights (Highlight EVERYTHING in the set)
        val newActions = BodyZone.values().associateWith { ZoneAction.NONE }.toMutableMap()
        bufferActiveZones.forEach { activeZone ->
            newActions[activeZone] = action
        }
        _uiState.update { it.copy(zoneActions = newActions) }

        lastClickTime = currentTime
    }

    private fun startBufferTimer() {
        bufferJob?.cancel()
        fanBoostJob?.cancel()

        setFanSpeed(5)

        bufferJob = viewModelScope.launch {
            delay(BUFFER_DURATION)
            // Buffer Ends
            setFanSpeed(2)
            _uiState.update { s ->
                s.copy(zoneActions = BodyZone.values().associateWith { ZoneAction.NONE })
            }
            bufferActiveZones.clear()
            lastClickTime = 0
        }
        fanBoostJob = bufferJob
    }

    // --- Standard Setters ---

    fun setTargetTemperature(temp: Int) {
        repo.setGlobalTemperature(temp)
        pushGlobalTemp()
    }

    fun setFanSpeed(level: Int) {
        val safeLevel = level.coerceIn(0, 5)
        repo.setFanSpeed(safeLevel)
        _uiState.update { it.copy(fanSpeed = repo.getFanSpeed()) }
    }

    fun setFanDirection(direction: FanDirection) {
        repo.setFanDirection(direction)
        _uiState.update { it.copy(fanDirection = direction) }
    }

    fun increaseGlobalTemp() { repo.warm(); pushGlobalTemp() }
    fun decreaseGlobalTemp() { repo.cool(); pushGlobalTemp() }

    fun triggerFanBoost() {
        startBufferTimer()
    }

    fun resetToDefaults() {
        bufferJob?.cancel()
        repo.setGlobalTemperature(21)
        repo.setFanSpeed(2)
        refreshState()
    }

    private fun pushGlobalTemp() {
        _uiState.update { s -> s.copy(globalTemperature = repo.getGlobalTemperature()) }
    }
}