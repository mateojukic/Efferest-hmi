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
import kotlin.text.toFloat
import kotlin.text.toInt

/**
 * Car-backed HVAC repository focused on HVAC_TEMPERATURE_SET (target temperature in Celsius).
 *
 * According to the AAOS docs for HVAC_TEMPERATURE_SET:
 *  - configArray (if present) encodes:
 *      [0] = minC * 10
 *      [1] = maxC * 10
 *      [2] = incrementC * 10
 *      [3] = minF * 10 (ignored here)
 *      [4] = maxF * 10 (ignored here)
 *      [5] = incrementF * 10 (ignored here)
 *  - If configArray missing, use getMinValue(areaId) / getMaxValue(areaId).
 *  - Property may be FLOAT (typical) or INT32 depending on vehicle implementation.
 *
 * Simplification: All conceptual body zones map to the same physical areaId (first available).
 */
class CarHvacRepository(
    private val context: Context
) : HvacRepository {

    override var minTemp: Int = 16
        private set
    override var maxTemp: Int = 28
        private set

    // Flag that indicates chosen representation of HVAC_TEMPERATURE_SET
    private var isFloatTemp: Boolean = true

    // Increment (Celsius) used for snapping; derived from configArray or fallback.
    private var incrementC: Float = 0.5f

    // Area we control (first areaId from config)
    private var areaId: Int = 0

    private val connected = AtomicBoolean(false)
    private var car: Car? = null
    private var mgr: CarPropertyManager? = null

    // Callback retained for proper unregistering.
    private var tempCallback: CarPropertyEventCallback? = null

    // Internal zone model (integer Celsius values)
    private val zoneTemps = mutableMapOf(
        BodyZone.UPPER to 22,
        BodyZone.MIDDLE to 22,
        BodyZone.LOWER to 22
    )
    private var globalTemp: Int = 22

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    /**
     * Connect to Car service and initialize HVAC configuration.
     * Safe to call multiple times; subsequent calls no-op if already connected.
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        if (connected.get()) return@withContext
        try {
            val carInst = Car.createCar(context)
            car = carInst
            mgr = carInst.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            queryConfig()
            registerTemperatureCallback()
            readInitialTemperature()
            connected.set(true)
            _ready.value = true
            Log.d(
                HVAC_TAG,
                "HVAC connected: areaId=$areaId type=${if (isFloatTemp) "FLOAT" else "INT"} bounds=$minTemp..$maxTemp inc=$incrementC"
            )
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Failed to connect HVAC", e)
        }
    }

    /**
     * Obtain CarPropertyConfig for HVAC_TEMPERATURE_SET and derive:
     *  - areaId (first area)
     *  - isFloatTemp (inferred from min value type)
     *  - min/max/increment from configArray or fallback
     */
    private fun queryConfig() {
        val manager = mgr ?: return
        try {
            val ids = ArraySet<Int>().apply { add(VehiclePropertyIds.HVAC_TEMPERATURE_SET) }
            val configs = manager.getPropertyList(ids)
            val cfg = configs.firstOrNull() as? CarPropertyConfig<*>
            if (cfg == null) {
                Log.w(HVAC_TAG, "No CarPropertyConfig for HVAC_TEMPERATURE_SET; using defaults")
                applyDefaultBounds()
                return
            }

            // Area selection (first available)
            areaId = cfg.areaIds.firstOrNull() ?: 0

            // Determine property type by inspecting min value object
            // (Avoid constants that may not be exported in current SDK).
            val sampleMin = safeGetMinValue(cfg, areaId)
            isFloatTemp = sampleMin is Float

            // Parse configArray if present (Celsius indices 0,1,2)
            val ca = cfg.configArray
            if (ca != null && ca.size >= 3) {
                val minC = ca[0] / 10f
                val maxC = ca[1] / 10f
                val incC = ca[2] / 10f
                if (minC < maxC && incC > 0f) {
                    minTemp = minC.toInt()
                    maxTemp = maxC.toInt()
                    incrementC = incC
                    Log.d(
                        HVAC_TAG,
                        "ConfigArray (Celsius) -> min=$minC max=$maxC inc=$incrementC"
                    )
                } else {
                    Log.w(HVAC_TAG, "ConfigArray invalid values; falling back to min/max")
                    deriveBoundsFromMinMax(cfg, areaId, sampleMin)
                }
            } else {
                deriveBoundsFromMinMax(cfg, areaId, sampleMin)
            }

        } catch (e: Exception) {
            Log.w(HVAC_TAG, "queryConfig failed; applying defaults", e)
            applyDefaultBounds()
        }
    }

    private fun safeGetMinValue(cfg: CarPropertyConfig<*>, areaId: Int): Any? =
        try { cfg.getMinValue(areaId) } catch (e: Exception) { null }

    private fun safeGetMaxValue(cfg: CarPropertyConfig<*>, areaId: Int): Any? =
        try { cfg.getMaxValue(areaId) } catch (e: Exception) { null }

    private fun deriveBoundsFromMinMax(cfg: CarPropertyConfig<*>, areaId: Int, sampleMin: Any?) {
        val minAny = sampleMin ?: safeGetMinValue(cfg, areaId)
        val maxAny = safeGetMaxValue(cfg, areaId)
        val minC = when (minAny) {
            is Float -> minAny
            is Int -> minAny.toFloat()
            else -> 16f
        }
        val maxC = when (maxAny) {
            is Float -> maxAny
            is Int -> maxAny.toFloat()
            else -> 28f
        }
        minTemp = minC.toInt()
        maxTemp = maxC.toInt()
        incrementC = if (isFloatTemp) 0.5f else 1.0f
        Log.d(
            HVAC_TAG,
            "Bounds from min/max -> min=$minC max=$maxC inc=$incrementC (floatType=$isFloatTemp)"
        )
    }

    private fun applyDefaultBounds() {
        minTemp = 16
        maxTemp = 28
        incrementC = 0.5f
        isFloatTemp = true
    }

    private fun registerTemperatureCallback() {
        val manager = mgr ?: return
        // Unregister existing if reconnecting
        tempCallback?.let {
            try { manager.unregisterCallback(it) } catch (_: Exception) {}
        }

        val callback = object : CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>?) {
                val raw = value?.value ?: return
                val tempC = when (raw) {
                    is Float -> raw
                    is Int -> raw.toFloat()
                    else -> return
                }
                updateAllZones(tempC.toInt())
                Log.d(HVAC_TAG, "HVAC event -> ${formatTemp(tempC)}")
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(HVAC_TAG, "HVAC error property=$propertyId area=$areaId")
            }
        }

        try {
            manager.registerCallback(
                callback,
                VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            tempCallback = callback
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "registerCallback failed", e)
        }
    }

    private fun readInitialTemperature() {
        val manager = mgr ?: return
        try {
            val v = if (isFloatTemp) {
                manager.getProperty(
                    Float::class.java,
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    areaId
                )?.value
            } else {
                manager.getProperty(
                    Int::class.java,
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    areaId
                )?.value
            }
            val tempC = when (v) {
                is Float -> v
                is Int -> v.toFloat()
                else -> 22f
            }
            updateAllZones(tempC.toInt())
            Log.d(HVAC_TAG, "Initial HVAC temp -> ${formatTemp(tempC)}")
        } catch (se: SecurityException) {
            Log.e(HVAC_TAG, "Permission denied reading HVAC_TEMPERATURE_SET", se)
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "Unable to read initial temp; using defaults", e)
        }
    }

    private fun updateAllZones(tempInt: Int) {
        globalTemp = tempInt
        zoneTemps.keys.forEach { zoneTemps[it] = tempInt }
    }

    override fun getZoneTemperature(zone: BodyZone): Int =
        zoneTemps[zone] ?: globalTemp

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

    /**
     * Write target temperature to HVAC_TEMPERATURE_SET.
     * Uses Float or Int based on isFloatTemp.
     */
    private fun writeTemperature(tempC: Float) {
        val manager = mgr ?: return
        if (!_ready.value) {
            Log.d(HVAC_TAG, "Skip write; repository not ready")
            return
        }
        val clamped = snapToSupported(tempC)
        try {
            if (isFloatTemp) {
                manager.setProperty(
                    Float::class.java,
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    areaId,
                    clamped
                )
                Log.d(HVAC_TAG, "Set HVAC temp -> ${formatTemp(clamped)} (area=$areaId)")
            } else {
                manager.setProperty(
                    Int::class.java,
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    areaId,
                    clamped.toInt()
                )
                Log.d(HVAC_TAG, "Set HVAC temp -> ${clamped.toInt()}°C (area=$areaId)")
            }
        } catch (se: SecurityException) {
            Log.e(HVAC_TAG, "No permission to set HVAC temp", se)
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Failed to set HVAC temp", e)
        }
    }

    /**
     * Clamp and snap requested temperature to supported range & increments.
     */
    private fun snapToSupported(requestedC: Float): Float {
        val clamped = requestedC.coerceIn(minTemp.toFloat(), maxTemp.toFloat())
        val steps = ((clamped - minTemp) / incrementC).roundToInt()
        val snapped = minTemp + steps * incrementC
        return snapped.coerceIn(minTemp.toFloat(), maxTemp.toFloat())
    }

    private fun formatTemp(c: Float): String =
        if (isFloatTemp) String.format("%.1f°C", c) else "${c.toInt()}°C"

    fun disconnect() {
        try {
            tempCallback?.let { callback ->
                try { mgr?.unregisterCallback(callback) } catch (_: Exception) {}
            }
            tempCallback = null
            car?.disconnect()
            connected.set(false)
            _ready.value = false
            Log.d(HVAC_TAG, "HVAC disconnected")
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "Disconnect encountered issues", e)
        }
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
}