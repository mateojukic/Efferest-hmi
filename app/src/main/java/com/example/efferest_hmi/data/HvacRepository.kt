package com.example.efferest_hmi.data

import com.example.efferest_hmi.model.BodyZone

interface HvacRepository {
    val minTemp: Int
    val maxTemp: Int

    // Temperature
    fun getZoneTemperature(zone: BodyZone): Int
    fun setZoneTemperature(zone: BodyZone, temp: Int)
    fun adjustZoneTemperature(zone: BodyZone, delta: Int)
    fun getGlobalTemperature(): Int
    fun setGlobalTemperature(temp: Int)
    fun warm()
    fun cool()

    // Fan
    fun getFanSpeed(): Int
    fun setFanSpeed(speed: Int)
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
    private var fanSpeed: Int = 0

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
        BodyZone.values().forEach { zoneTemps[it] = globalTemp }
    }

    override fun warm() {
        setGlobalTemperature(globalTemp + 1)
    }

    override fun cool() {
        setGlobalTemperature(globalTemp - 1)
    }

    override fun getFanSpeed(): Int = fanSpeed

    override fun setFanSpeed(speed: Int) {
        fanSpeed = speed.coerceIn(0, 5) // Mock max level 5
    }
}