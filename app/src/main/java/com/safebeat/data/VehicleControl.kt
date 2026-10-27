package com.safebeat.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 제어(비상등, 도어 잠금 해제).
 * Android Automotive에서 이 제어는 보통 시스템 권한(CONTROL_CAR_DOORS 등)이 필요해서
 * 일반 앱은 쓸 수 없다. 그래서 인터페이스로 분리하고, 지금은 화면의 '가상 차량 패널'에 표시한다.
 * Pleos에서 제어 API가 열리면 이 인터페이스의 구현만 바꾸면 된다.
 */
interface VehicleControl {
    val hazardOn: StateFlow<Boolean>
    val doorsUnlocked: StateFlow<Boolean>
    fun turnOnHazardLights()
    fun unlockDoors()
    fun reset()
}

class SimulatedVehicleControl : VehicleControl {
    private val _hazard = MutableStateFlow(false)
    private val _doors = MutableStateFlow(false)
    override val hazardOn: StateFlow<Boolean> = _hazard
    override val doorsUnlocked: StateFlow<Boolean> = _doors
    override fun turnOnHazardLights() {
        _hazard.value = true
    }
    override fun unlockDoors() {
        _doors.value = true
    }
    override fun reset() {
        _hazard.value = false
        _doors.value = false
    }
}
