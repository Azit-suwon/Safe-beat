package com.safebeat.data

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.safebeat.core.Gear
import com.safebeat.core.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 차량 상태 입력 */
interface VehicleSource {
    val state: StateFlow<VehicleState>
    val isRealCar: Boolean
    fun start()
    fun stop()
}

/** 시연 버튼으로 주행/정차를 바꾸는 가짜 차량 */
class SimulatedVehicleSource : VehicleSource {
    private val _state = MutableStateFlow(VehicleState(60f, Gear.DRIVE))
    override val state: StateFlow<VehicleState> = _state
    override val isRealCar = false
    fun setDriving(driving: Boolean) {
        _state.value = if (driving) VehicleState(60f, Gear.DRIVE) else VehicleState(0f, Gear.PARK)
    }
    override fun start() = Unit
    override fun stop() = Unit
}

/**
 * Pleos Connect 에뮬레이터/실차의 차량 신호(속도, 기어)를 읽는다.
 * Android Automotive 표준 CarPropertyManager 사용 → Pleos Vehicle SDK로 바꾸기 쉬운 자리.
 * 에뮬레이터에서는 Virtual Console(Motion > Gear Selection)로 값을 바꿀 수 있다.
 * 권한: CAR_SPEED(런타임 요청 필요), CAR_POWERTRAIN
 */
class CarVehicleSource(private val context: Context) : VehicleSource {
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state
    override val isRealCar = true
    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    val ms = (value.value as? Float) ?: return
                    _state.value = _state.value.copy(speedKmh = ms * 3.6f)
                }
                VehiclePropertyIds.GEAR_SELECTION -> {
                    val g = (value.value as? Int) ?: return
                    _state.value = _state.value.copy(gear = gearOf(g))
                }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "property error $propId")
        }
    }

    override fun start() {
        try {
            val c = Car.createCar(context)
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = m
            m.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            m.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (t: Throwable) {
            // 권한이 없거나 차량 서비스가 없는 기기 → 시뮬레이터로 대체(ViewModel에서 처리)
            Log.w(TAG, "Car API unavailable: ${t.message}")
            throw t
        }
    }

    override fun stop() {
        try {
            manager?.unregisterCallback(callback)
            car?.disconnect()
        } catch (_: Throwable) {
        }
    }

    // VehicleGear 상수: NEUTRAL=1, REVERSE=2, PARK=4, DRIVE=8
    private fun gearOf(v: Int) = when (v) {
        1 -> Gear.NEUTRAL
        2 -> Gear.REVERSE
        4 -> Gear.PARK
        8 -> Gear.DRIVE
        else -> if (v > 8) Gear.DRIVE else Gear.UNKNOWN
    }

    companion object {
        private const val TAG = "SafeBeat"
    }
}
