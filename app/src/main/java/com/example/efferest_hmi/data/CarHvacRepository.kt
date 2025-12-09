package com.example.efferest_hmi.data

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback
import android.content.Context
import android.util.ArraySet
import android.util.Log
import com.example.efferest_hmi.model.BodyZone
import com.example.efferest_hmi.util.HVAC_TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class CarHvacRepository(
    private val context: Context
) : HvacRepository {

    override var minTemp: Int = 16
        private set
    override var maxTemp: Int = 28
        private set

    private var isFloatTemp: Boolean = true
    private var incrementC: Float = 0.5f

    // Area IDs
    private var tempAreaId: Int = 0
    private var fanAreaId: Int = 0

    private val connected = AtomicBoolean(false)
    private var car: Car? = null
    private var mgr: CarPropertyManager? = null
    private var hvacCallback: CarPropertyEventCallback? = null

    // Internal State
    private val zoneTemps = mutableMapOf(
        BodyZone.UPPER to 22,
        BodyZone.MIDDLE to 22,
        BodyZone.LOWER to 22
    )
    private var globalTemp: Int = 22
    private var currentFanSpeed: Int = 0

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (connected.get()) return@withContext
        try {
            val carInst = Car.createCar(context)
            car = carInst
            mgr = carInst.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

            initConfigs()
            registerCallbacks()
            readInitialValues()

            connected.set(true)
            _ready.value = true
            Log.d(HVAC_TAG, "HVAC connected. TempArea=$tempAreaId FanArea=$fanAreaId")
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Failed to connect HVAC", e)
        }
    }

    private fun initConfigs() {
        val manager = mgr ?: return
        try {
            val ids = ArraySet<Int>().apply {
                add(VehiclePropertyIds.HVAC_TEMPERATURE_SET)
                add(VehiclePropertyIds.HVAC_FAN_SPEED)
            }
            val configs = manager.getPropertyList(ids)

            // 1. Setup Temperature
            val tempCfg = configs.find { it.propertyId == VehiclePropertyIds.HVAC_TEMPERATURE_SET }
            if (tempCfg != null) {
                tempAreaId = tempCfg.areaIds.firstOrNull() ?: 0
                val sampleMin = try { tempCfg.getMinValue(tempAreaId) } catch (e: Exception) { null }
                isFloatTemp = sampleMin is Float

                val ca = tempCfg.configArray
                if (ca != null && ca.size >= 3) {
                    minTemp = (ca[0] / 10f).toInt()
                    maxTemp = (ca[1] / 10f).toInt()
                    incrementC = ca[2] / 10f
                } else {
                    deriveBoundsFromMinMax(tempCfg, tempAreaId, sampleMin)
                }
            } else {
                applyDefaultBounds()
            }

            // 2. Setup Fan Speed
            val fanCfg = configs.find { it.propertyId == VehiclePropertyIds.HVAC_FAN_SPEED }
            if (fanCfg != null) {
                fanAreaId = fanCfg.areaIds.firstOrNull() ?: 0
                // We could read configArray[0] for max fan speed here if we wanted to be dynamic
            }

        } catch (e: Exception) {
            Log.w(HVAC_TAG, "Config init failed", e)
            applyDefaultBounds()
        }
    }

    private fun deriveBoundsFromMinMax(cfg: CarPropertyConfig<*>, areaId: Int, sampleMin: Any?) {
        val minAny = sampleMin ?: try { cfg.getMinValue(areaId) } catch (e: Exception) { 16f }
        val maxAny = try { cfg.getMaxValue(areaId) } catch (e: Exception) { 28f }

        minTemp = if (minAny is Float) minAny.toInt() else (minAny as? Int) ?: 16
        maxTemp = if (maxAny is Float) maxAny.toInt() else (maxAny as? Int) ?: 28
        incrementC = if (isFloatTemp) 0.5f else 1.0f
    }

    private fun applyDefaultBounds() {
        minTemp = 16
        maxTemp = 28
        incrementC = 0.5f
        isFloatTemp = true
    }

    private fun registerCallbacks() {
        val manager = mgr ?: return
        hvacCallback?.let { try { manager.unregisterCallback(it) } catch (_: Exception) {} }

        val callback = object : CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>?) {
                val v = value ?: return
                when (v.propertyId) {
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET -> {
                        val raw = v.value
                        val tempC = if (raw is Float) raw else (raw as? Int)?.toFloat() ?: return
                        updateAllZones(tempC.toInt())
                        Log.d(HVAC_TAG, "Temp changed -> ${tempC.toInt()}")
                    }
                    VehiclePropertyIds.HVAC_FAN_SPEED -> {
                        val speed = v.value as? Int ?: return
                        currentFanSpeed = speed
                        Log.d(HVAC_TAG, "Fan speed changed -> $speed")
                    }
                }
            }
            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(HVAC_TAG, "HVAC Error: prop=$propertyId area=$areaId")
            }
        }

        try {
            // Register for Temp
            manager.registerCallback(callback, VehiclePropertyIds.HVAC_TEMPERATURE_SET, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            // Register for Fan
            manager.registerCallback(callback, VehiclePropertyIds.HVAC_FAN_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            hvacCallback = callback
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Callback registration failed", e)
        }
    }

    private fun readInitialValues() {
        val manager = mgr ?: return
        try {
            // Read Temp
            val tVal = if (isFloatTemp) {
                manager.getProperty(Float::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, tempAreaId)?.value
            } else {
                manager.getProperty(Int::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, tempAreaId)?.value?.toFloat()
            }
            if (tVal != null) updateAllZones(tVal.toInt())

            // Read Fan
            val fVal = manager.getProperty(Int::class.java, VehiclePropertyIds.HVAC_FAN_SPEED, fanAreaId)?.value
            if (fVal != null) currentFanSpeed = fVal

        } catch (e: Exception) {
            Log.w(HVAC_TAG, "Initial read failed", e)
        }
    }

    // --- Interface Implementation ---

    private fun updateAllZones(tempInt: Int) {
        globalTemp = tempInt
        zoneTemps.keys.forEach { zoneTemps[it] = tempInt }
    }

    override fun getZoneTemperature(zone: BodyZone): Int = zoneTemps[zone] ?: globalTemp

    override fun setZoneTemperature(zone: BodyZone, temp: Int) {
        val snapped = snapToSupported(temp.toFloat()).toInt()
        zoneTemps[zone] = snapped
        globalTemp = snapped
        writeTemperature(snapped.toFloat())
    }

    override fun adjustZoneTemperature(zone: BodyZone, delta: Int) {
        val current = (zoneTemps[zone] ?: globalTemp).toFloat()
        val snapped = snapToSupported(current + delta).toInt()
        zoneTemps[zone] = snapped
        globalTemp = snapped
        writeTemperature(snapped.toFloat())
    }

    override fun getGlobalTemperature(): Int = globalTemp

    override fun setGlobalTemperature(temp: Int) {
        val snapped = snapToSupported(temp.toFloat()).toInt()
        updateAllZones(snapped)
        writeTemperature(snapped.toFloat())
    }

    override fun warm() {
        val step = 1.0f
        val next = snapToSupported(getGlobalTemperature().toFloat() + step).toInt()
        setGlobalTemperature(next)
    }

    override fun cool() {
        val step = 1.0f
        val next = snapToSupported(getGlobalTemperature().toFloat() - step).toInt()
        setGlobalTemperature(next)
    }

    // Fan Implementation
    override fun getFanSpeed(): Int = currentFanSpeed

    override fun setFanSpeed(speed: Int) {
        val manager = mgr ?: return
        if (!_ready.value) return

        try {
            manager.setProperty(Int::class.java, VehiclePropertyIds.HVAC_FAN_SPEED, fanAreaId, speed)
            currentFanSpeed = speed
            Log.d(HVAC_TAG, "Set Fan Speed -> $speed")
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Failed to set fan speed", e)
        }
    }

    private fun writeTemperature(tempC: Float) {
        val manager = mgr ?: return
        if (!_ready.value) return
        val clamped = snapToSupported(tempC)
        try {
            if (isFloatTemp) {
                manager.setProperty(Float::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, tempAreaId, clamped)
            } else {
                manager.setProperty(Int::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, tempAreaId, clamped.toInt())
            }
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Failed to set temp", e)
        }
    }

    private fun snapToSupported(requestedC: Float): Float {
        val clamped = requestedC.coerceIn(minTemp.toFloat(), maxTemp.toFloat())
        val steps = ((clamped - minTemp) / incrementC).roundToInt()
        val snapped = minTemp + steps * incrementC
        return snapped.coerceIn(minTemp.toFloat(), maxTemp.toFloat())
    }

    fun disconnect() {
        try {
            hvacCallback?.let { mgr?.unregisterCallback(it) }
            car?.disconnect()
            connected.set(false)
        } catch (e: Exception) { }
    }
}