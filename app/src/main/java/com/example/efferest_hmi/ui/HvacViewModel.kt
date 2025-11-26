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

data class HvacUiState(
    val version: UIVersion = UIVersion.VERSION_A,
    val zoneTemperatures: Map<BodyZone, Int> = emptyMap(),
    val globalTemperature: Int = 22,
    val minTemp: Int = 16,
    val maxTemp: Int = 28,
    val zoneActions: Map<BodyZone, ZoneAction> = BodyZone.values().associateWith { ZoneAction.NONE }
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
                // Refresh UI state after connecting/initial read
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

    // Track per-zone timer jobs for active feedback
    private val zoneTimers: MutableMap<BodyZone, Job?> = mutableMapOf(
        BodyZone.UPPER to null,
        BodyZone.MIDDLE to null,
        BodyZone.LOWER to null
    )

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

    private fun startTimedAction(zone: BodyZone, action: ZoneAction) {
        // Cancel any existing job
        zoneTimers[zone]?.cancel()
        setZoneAction(zone, action)

        val job = viewModelScope.launch {
            val activeAction = action
            delay(5000L) // 5 seconds
            // If still the same action, reset
            val now = _uiState.value.zoneActions[zone]
            if (now == activeAction) {
                setZoneAction(zone, ZoneAction.NONE)
            }
        }
        zoneTimers[zone] = job
    }

    private fun setZoneAction(zone: BodyZone, action: ZoneAction) {
        _uiState.update { state ->
            state.copy(
                zoneActions = state.zoneActions.toMutableMap().also { it[zone] = action }
            )
        }
    }

    private fun pushZoneTemps() {
        _uiState.update { state ->
            state.copy(zoneTemperatures = BodyZone.values().associateWith { repo.getZoneTemperature(it) })
        }
    }

    fun increaseGlobalTemp() {
        repo.warm()
        pushGlobalTemp()
    }

    fun decreaseGlobalTemp() {
        repo.cool()
        pushGlobalTemp()
    }

    private fun pushGlobalTemp() {
        _uiState.update { s -> s.copy(globalTemperature = repo.getGlobalTemperature()) }
    }
}