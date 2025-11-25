package com.example.efferest_hmi.data

import com.example.efferest_hmi.model.BodyZone

interface HvacRepository {
    val minTemp: Int
    val maxTemp: Int
    fun getZoneTemperature(zone: BodyZone): Int
    fun setZoneTemperature(zone: BodyZone, temp: Int)
    fun adjustZoneTemperature(zone: BodyZone, delta: Int)
    fun getGlobalTemperature(): Int
    fun setGlobalTemperature(temp: Int)
}

class InMemoryHvacRepository(
    override val minTemp: Int = 16,
    override val maxTemp: Int = 28
) : HvacRepository {

    private val zoneTemps = mutableMapOf(
        BodyZone.UPPER to 22,
        BodyZone.MIDDLE to 22,
        BodyZone.LOWER to 22
    )
    private var globalTemp: Int = 22

    override fun getZoneTemperature(zone: BodyZone): Int = zoneTemps[zone] ?: globalTemp

    override fun setZoneTemperature(zone: BodyZone, temp: Int) {
        zoneTemps[zone] = temp.coerceIn(minTemp, maxTemp)
    }

    override fun adjustZoneTemperature(zone: BodyZone, delta: Int) {
        val newTemp = (zoneTemps[zone] ?: globalTemp) + delta
        zoneTemps[zone] = newTemp.coerceIn(minTemp, maxTemp)
    }

    override fun getGlobalTemperature(): Int = globalTemp
    override fun setGlobalTemperature(temp: Int) {
        globalTemp = temp.coerceIn(minTemp, maxTemp)
    }
}