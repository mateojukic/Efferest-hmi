package com.example.efferest_hmi.data

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.CarPropertyConfig
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

/**
 * Car-backed HVAC repository for HVAC_TEMPERATURE_SET.
 * Simplification: all conceptual body zones write the same seat area temperature (first available areaId).
 */
class CarHvacRepository(
    private val context: Context
) : HvacRepository {

    override var minTemp: Int = 16
        private set
    override var maxTemp: Int = 28
        private set

    private val connected = AtomicBoolean(false)
    private var car: Car? = null
    private var mgr: CarPropertyManager? = null

    // Property is commonly FLOAT; handle both FLOAT and INT builds
    private var isFloatTemp: Boolean = true

    // Area we control (first available)
    private var areaId: Int = 0

    // Discrete increment (Celsius). From configArray when available; fallback to 0.5f for float, 1.0f for int.
    private var incrementC: Float = 0.5f

    // Keep UI model in sync
    private val zoneTemps = mutableMapOf(
        BodyZone.UPPER to 22,
        BodyZone.MIDDLE to 22,
        BodyZone.LOWER to 22
    )
    private var globalTemp: Int = 22

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

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
            Log.d(HVAC_TAG, "HVAC connected: areaId=$areaId, type=${if (isFloatTemp) "FLOAT" else "INT"}, bounds=$minTemp..$maxTemp, inc=$incrementC")
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Failed to connect HVAC", e)
        }
    }

    private fun queryConfig() {
        try {
            val ids = ArraySet<Int>().apply { add(VehiclePropertyIds.HVAC_TEMPERATURE_SET) }
            val configs = mgr?.getPropertyList(ids).orEmpty()
            val cfg = configs.firstOrNull() as? CarPropertyConfig<*> ?: run {
                Log.w(HVAC_TAG, "No config for HVAC_TEMPERATURE_SET; using defaults")
                return
            }

            // Area
            val areas = cfg.areaIds
            areaId = if (areas.isNotEmpty()) areas[0] else 0

            // Type
            isFloatTemp = when (cfg.propertyType) {
                CarPropertyConfig.VEHICLE_PROPERTY_TYPE_FLOAT -> true
                CarPropertyConfig.VEHICLE_PROPERTY_TYPE_INT32 -> false
                else -> {
                    Log.w(HVAC_TAG, "Unexpected property type ${cfg.propertyType}; default FLOAT")
                    true
                }
            }

            // Prefer configArray (discrete values per docs)
            val ca = cfg.configArray
            if (ca != null && ca.size >= 6) {
                val minC = ca[0] / 10f
                val maxC = ca[1] / 10f
                val incC = ca[2] / 10f
                incrementC = if (incC > 0f) incC else if (isFloatTemp) 0.5f else 1.0f
                minTemp = minC.toInt()
                maxTemp = maxC.toInt()
                Log.d(HVAC_TAG, "ConfigArray found: minC=$minC, maxC=$maxC, incC=$incrementC")
            } else {
                // Fallback to min/max
                val minAny = cfg.getMinValue(areaId)
                val maxAny = cfg.getMaxValue(areaId)
                val minC = if (isFloatTemp) (minAny as? Float ?: 16f) else (minAny as? Int)?.toFloat() ?: 16f
                val maxC = if (isFloatTemp) (maxAny as? Float ?: 28f) else (maxAny as? Int)?.toFloat() ?: 28f
                minTemp = minC.toInt()
                maxTemp = maxC.toInt()
                incrementC = if (isFloatTemp) 0.5f else 1.0f
                Log.d(HVAC_TAG, "Bounds from min/max: minC=$minC, maxC=$maxC, incC=$incrementC")
            }
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "queryConfig failed; using defaults", e)
        }
    }

    private fun registerTemperatureCallback() {
        try {
            // Use registerCallback with ONCHANGE rate; handles events per docs
            mgr?.registerCallback(object : CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>?) {
                    val raw = value?.value ?: return
                    val tempC = when (raw) {
                        is Float -> raw
                        is Int -> raw.toFloat()
                        else -> return
                    }
                    val tInt = tempC.toInt()
                    globalTemp = tInt
                    zoneTemps.keys.forEach { zoneTemps[it] = tInt }
                    Log.d(HVAC_TAG, "HVAC event -> ${formatTemp(tempC)}")
                }
                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    Log.w(HVAC_TAG, "HVAC error property=$propertyId area=$areaId")
                }
            }, VehiclePropertyIds.HVAC_TEMPERATURE_SET, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "registerCallback failed", e)
        }
    }

    private fun readInitialTemperature() {
        try {
            val v = if (isFloatTemp) {
                mgr?.getProperty(Float::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, areaId)?.value
            } else {
                mgr?.getProperty(Int::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, areaId)?.value
            }
            val tempC = when (v) {
                is Float -> v
                is Int -> v.toFloat()
                else -> 22f
            }
            val tInt = tempC.toInt()
            globalTemp = tInt
            zoneTemps.keys.forEach { zoneTemps[it] = tInt }
            Log.d(HVAC_TAG, "Initial temp -> ${formatTemp(tempC)}")
        } catch (e: SecurityException) {
            Log.e(HVAC_TAG, "Permission denied reading HVAC_TEMPERATURE_SET", e)
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "Unable to read initial temp; using defaults", e)
        }
    }

    override fun getZoneTemperature(zone: BodyZone): Int = zoneTemps[zone] ?: globalTemp

    override fun setZoneTemperature(zone: BodyZone, temp: Int) {
        val snappedC = snapToSupported(temp.toFloat())
        val snappedInt = snappedC.toInt()
        zoneTemps[zone] = snappedInt
        globalTemp = snappedInt
        writeTemperature(snappedC)
    }

    override fun adjustZoneTemperature(zone: BodyZone, delta: Int) {
        val current = (zoneTemps[zone] ?: globalTemp).toFloat()
        val snappedC = snapToSupported(current + delta)
        val snappedInt = snappedC.toInt()
        zoneTemps[zone] = snappedInt
        globalTemp = snappedInt
        writeTemperature(snappedC)
    }

    override fun getGlobalTemperature(): Int = globalTemp

    override fun setGlobalTemperature(temp: Int) {
        val snappedC = snapToSupported(temp.toFloat())
        val snappedInt = snappedC.toInt()
        globalTemp = snappedInt
        zoneTemps.keys.forEach { zoneTemps[it] = snappedInt }
        writeTemperature(snappedC)
    }

    private fun writeTemperature(tempC: Float) {
        try {
            if (isFloatTemp) {
                mgr?.setProperty(Float::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, areaId, tempC)
                Log.d(HVAC_TAG, "Set HVAC temp -> ${formatTemp(tempC)} (area=$areaId)")
            } else {
                mgr?.setProperty(Int::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, areaId, tempC.toInt())
                Log.d(HVAC_TAG, "Set HVAC temp -> ${tempC.toInt()}°C (area=$areaId)")
            }
        } catch (e: SecurityException) {
            Log.e(HVAC_TAG, "No permission to set HVAC temp", e)
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Failed to set HVAC temp", e)
        }
    }

    private fun snapToSupported(requestedC: Float): Float {
        // Clamp
        val clamped = requestedC.coerceIn(minTemp.toFloat(), maxTemp.toFloat())
        // Snap to increment
        val steps = ((clamped - minTemp) / incrementC).roundToInt()
        val snapped = minTemp + steps * incrementC
        return snapped.coerceIn(minTemp.toFloat(), maxTemp.toFloat())
    }

    private fun formatTemp(c: Float): String =
        if (isFloatTemp) String.format("%.1f°C", c) else "${c.toInt()}°C"

    fun disconnect() {
        try {
            mgr?.unregisterCallback(null) // Unregister all callbacks for this client
            car?.disconnect()
            connected.set(false)
            Log.d(HVAC_TAG, "HVAC disconnected")
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "Disconnect issues", e)
        }
    }
}