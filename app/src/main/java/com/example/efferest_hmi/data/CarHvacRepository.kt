package com.example.efferest_hmi.data

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback
import android.car.hardware.property.VehiclePropertyType
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

/**
 * Car-backed HVAC repository.
 *
 * Simplification: All UI "body zones" adjust the same HVAC_TEMPERATURE_SET area (chosen areaId).
 * On many platforms this property is a Float (°C), often in 0.5°C increments. Some use Int.
 */
class CarHvacRepository(
    private val context: Context
) : HvacRepository {

    // Exposed bounds (will be updated from CarPropertyConfig if available)
    override var minTemp: Int = 16
        private set
    override var maxTemp: Int = 28
        private set

    // If property is FLOAT we’ll round to this step when writing. Default to 0.5°C.
    private var tempStepC: Float = 0.5f

    // Internal
    private val connected = AtomicBoolean(false)
    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null

    // The areaId we will target (picked from property config areaIds)
    private var areaId: Int = 0

    // Whether HVAC_TEMPERATURE_SET is Float or Int on this platform
    private var isFloatTemp: Boolean = true

    // Keep a simple internal model (UI concept zones all map to the same value for now)
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
            val mgr = carInst.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            carPropertyManager = mgr

            // Discover config (type, areas, bounds)
            queryConfig(mgr)

            // Subscribe for changes from vehicle
            subscribeTemperature(mgr)

            // Read initial temperature
            queryInitialTemperature(mgr)

            connected.set(true)
            _ready.value = true
            Log.d(HVAC_TAG, "CarHvacRepository connected (areaId=$areaId, isFloat=$isFloatTemp, bounds=$minTemp..$maxTemp step=$tempStepC)")
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Failed to connect CarHvacRepository", e)
        }
    }

    private fun queryConfig(mgr: CarPropertyManager) {
        try {
            // getPropertyList expects an ArraySet<Integer>, NOT a Kotlin List
            val ids = ArraySet<Int>().apply { add(VehiclePropertyIds.HVAC_TEMPERATURE_SET) }
            val list = mgr.getPropertyList(ids)
            val cfg = list.firstOrNull()
            if (cfg == null) {
                Log.w(HVAC_TAG, "No CarPropertyConfig for HVAC_TEMPERATURE_SET; using defaults")
                return
            }

            // Choose an areaId (pick the first available)
            val areas = cfg.areaIds
            if (areas.isNotEmpty()) {
                areaId = areas[0]
            } else {
                areaId = 0
            }

            // Determine property type
            isFloatTemp = when (cfg.propertyType) {
                VehiclePropertyType.FLOAT -> true
                VehiclePropertyType.INT32 -> false
                else -> {
                    Log.w(HVAC_TAG, "Unexpected HVAC_TEMPERATURE_SET type=${cfg.propertyType}, defaulting to FLOAT")
                    true
                }
            }

            // Read min/max bounds from config when available
            // CarPropertyConfig<T>.minValue / maxValue return boxed values for the type (Float or Int).
            val minAny = cfg.minValue
            val maxAny = cfg.maxValue
            val (minC, maxC) = if (isFloatTemp) {
                ((minAny as? Float) ?: minTemp.toFloat()) to ((maxAny as? Float) ?: maxTemp.toFloat())
            } else {
                ((minAny as? Int)?.toFloat() ?: minTemp.toFloat()) to ((maxAny as? Int)?.toFloat() ?: maxTemp.toFloat())
            }

            // Set integer bounds for our UI model (we use whole °C in the current UI)
            minTemp = minC.toInt()
            maxTemp = maxC.toInt()

            // Step/increment isn’t consistently exposed across builds.
            // If your platform provides a specific increment, set tempStepC to that.
            // Otherwise keep default 0.5°C when using float; for int we assume 1°C.
            tempStepC = if (isFloatTemp) 0.5f else 1.0f

            Log.d(HVAC_TAG, "Config: areaId=$areaId, type=${if (isFloatTemp) "FLOAT" else "INT"}, min=$minC, max=$maxC, step=$tempStepC")
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "queryConfig failed; using defaults", e)
        }
    }

    private fun subscribeTemperature(mgr: CarPropertyManager) {
        mgr.subscribePropertyEvents(
            VehiclePropertyIds.HVAC_TEMPERATURE_SET,
            object : CarPropertyEventCallback {
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
                    Log.d(HVAC_TAG, "Temperature change event -> ${formatTemp(tempC)}")
                }

                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    Log.w(HVAC_TAG, "HVAC_TEMPERATURE_SET error event (property=$propertyId area=$areaId)")
                }
            }
        )
    }

    private fun queryInitialTemperature(mgr: CarPropertyManager) {
        try {
            val v = if (isFloatTemp) {
                mgr.getProperty(Float::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, areaId)?.value
            } else {
                mgr.getProperty(Int::class.java, VehiclePropertyIds.HVAC_TEMPERATURE_SET, areaId)?.value
            }
            val tempC = when (v) {
                is Float -> v
                is Int -> v.toFloat()
                else -> 22f
            }
            val tInt = tempC.toInt()
            globalTemp = tInt
            zoneTemps.keys.forEach { zoneTemps[it] = tInt }
            Log.d(HVAC_TAG, "Initial HVAC_TEMPERATURE_SET = ${formatTemp(tempC)}")
        } catch (e: SecurityException) {
            Log.e(HVAC_TAG, "Permission denied reading HVAC_TEMPERATURE_SET", e)
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "Unable to read initial HVAC_TEMPERATURE_SET; using defaults", e)
        }
    }

    override fun getZoneTemperature(zone: BodyZone): Int = zoneTemps[zone] ?: globalTemp

    override fun setZoneTemperature(zone: BodyZone, temp: Int) {
        val clamped = temp.coerceIn(minTemp, maxTemp)
        zoneTemps[zone] = clamped
        globalTemp = clamped
        writeTemperature(clamped)
    }

    override fun adjustZoneTemperature(zone: BodyZone, delta: Int) {
        val newTemp = (zoneTemps[zone] ?: globalTemp) + delta
        setZoneTemperature(zone, newTemp)
    }

    override fun getGlobalTemperature(): Int = globalTemp

    override fun setGlobalTemperature(temp: Int) {
        val clamped = temp.coerceIn(minTemp, maxTemp)
        globalTemp = clamped
        zoneTemps.keys.forEach { zoneTemps[it] = clamped }
        writeTemperature(clamped)
    }

    private fun writeTemperature(tempInt: Int) {
        try {
            if (isFloatTemp) {
                // Round to step for float properties
                val current = tempInt.toFloat()
                val rounded = roundToStep(current, tempStepC)
                carPropertyManager?.setProperty(
                    Float::class.java,
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    areaId,
                    rounded
                )
                Log.d(HVAC_TAG, "Set HVAC_TEMPERATURE_SET to ${formatTemp(rounded)} (area=$areaId)")
            } else {
                carPropertyManager?.setProperty(
                    Int::class.java,
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    areaId,
                    tempInt
                )
                Log.d(HVAC_TAG, "Set HVAC_TEMPERATURE_SET to ${tempInt}°C (area=$areaId)")
            }
        } catch (e: SecurityException) {
            Log.e(HVAC_TAG, "No permission to set HVAC temperature", e)
        } catch (e: Exception) {
            Log.e(HVAC_TAG, "Failed to set HVAC temperature", e)
        }
    }

    fun disconnect() {
        try {
            carPropertyManager?.unsubscribePropertyEvents(VehiclePropertyIds.HVAC_TEMPERATURE_SET)
            car?.disconnect()
            connected.set(false)
            Log.d(HVAC_TAG, "CarHvacRepository disconnected")
        } catch (e: Exception) {
            Log.w(HVAC_TAG, "Disconnect issues", e)
        }
    }

    private fun roundToStep(value: Float, step: Float): Float {
        if (step <= 0f) return value
        val steps = (value / step).toInt()
        val lower = steps * step
        val upper = (steps + 1) * step
        return if (value - lower < upper - value) lower else upper
    }

    private fun formatTemp(c: Float): String = if (isFloatTemp) String.format("%.1f°C", c) else "${c.toInt()}°C"
}