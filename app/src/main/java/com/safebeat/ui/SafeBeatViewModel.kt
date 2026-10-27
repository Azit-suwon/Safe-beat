package com.safebeat.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safebeat.core.EmergencyActions
import com.safebeat.core.EmergencyCoordinator
import com.safebeat.core.RiskAssessment
import com.safebeat.core.RiskEngine
import com.safebeat.core.SafetyState
import com.safebeat.core.VehicleState
import com.safebeat.data.CarVehicleSource
import com.safebeat.data.EmergencyReport
import com.safebeat.data.HeartScenario
import com.safebeat.data.MockEmergencyReporter
import com.safebeat.data.SimulatedHeartRateSource
import com.safebeat.data.SimulatedVehicleControl
import com.safebeat.data.SimulatedVehicleSource
import com.safebeat.data.VehicleSource
import com.safebeat.data.VoiceAssistant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val safety: SafetyState = SafetyState.Monitoring,
    val bpm: Int? = null,
    val bpmHistory: List<Int?> = emptyList(),
    val scenario: HeartScenario = HeartScenario.NORMAL,
    val vehicle: VehicleState = VehicleState(),
    val usingRealCar: Boolean = false,
    val hazardOn: Boolean = false,
    val doorsUnlocked: Boolean = false,
    val report: EmergencyReport? = null,
    val showRescueMode: Boolean = false,
    val nowMs: Long = System.currentTimeMillis(),
)

class SafeBeatViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val heart = SimulatedHeartRateSource()
    private val simVehicle = SimulatedVehicleSource()
    private var vehicle: VehicleSource = simVehicle
    private var vehicleJob: Job? = null
    private val control = SimulatedVehicleControl()
    private val reporter = MockEmergencyReporter(app)
    private val voice = VoiceAssistant(app) { driverSaysOk() }

    private val actions = object : EmergencyActions {
        override fun speak(text: String) = voice.speak(text)
        override fun startListeningForResponse() = voice.startListening()
        override fun stopListening() = voice.stopListening()
        override fun reportEmergency(assessment: RiskAssessment, vehicle: VehicleState) {
            val r = reporter.report(assessment, vehicle)
            _ui.update { it.copy(report = r) }
        }
        override fun turnOnHazardLights() = control.turnOnHazardLights()
        override fun unlockDoors() = control.unlockDoors()
    }

    private val coordinator = EmergencyCoordinator(RiskEngine(), actions)

    init {
        voice.init()
        heart.start(viewModelScope) { sample ->
            coordinator.onVital(sample)
            _ui.update { s -> s.copy(bpm = sample.bpm, bpmHistory = (s.bpmHistory + sample.bpm).takeLast(60)) }
        }
        observeVehicle()
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val state = coordinator.tick(now)
                _ui.update {
                    it.copy(
                        safety = state,
                        nowMs = now,
                        hazardOn = control.hazardOn.value,
                        doorsUnlocked = control.doorsUnlocked.value,
                        scenario = heart.scenario.value,
                    )
                }
                delay(250)
            }
        }
    }

    private fun observeVehicle() {
        vehicleJob?.cancel()
        vehicleJob = viewModelScope.launch {
            vehicle.state.collect { v ->
                coordinator.onVehicle(v)
                _ui.update { it.copy(vehicle = v, usingRealCar = vehicle.isRealCar) }
            }
        }
    }

    // ---- 시연 패널 / adb 명령 ----

    fun setScenario(s: HeartScenario) = heart.setScenario(s)

    fun setDriving(driving: Boolean) {
        if (vehicle.isRealCar) return
        simVehicle.setDriving(driving)
    }

    /** 에뮬레이터 차량 신호 사용 ↔ 시연용 가짜 차량 */
    fun useRealCar(enable: Boolean) {
        vehicle.stop()
        vehicle = if (enable) {
            try {
                CarVehicleSource(getApplication()).also { it.start() }
            } catch (t: Throwable) {
                Log.w("SafeBeat", "차량 신호 사용 불가, 시뮬레이터 유지: ${t.message}")
                simVehicle
            }
        } else {
            simVehicle
        }
        observeVehicle()
    }

    fun driverSaysOk() {
        coordinator.driverResponded(System.currentTimeMillis())
    }

    fun showRescueMode(show: Boolean) = _ui.update { it.copy(showRescueMode = show) }

    fun resetDemo() {
        heart.setScenario(HeartScenario.NORMAL)
        if (!vehicle.isRealCar) simVehicle.setDriving(true)
        coordinator.reset()
        control.reset()
        voice.stopListening()
        _ui.update { it.copy(report = null, showRescueMode = false, bpmHistory = emptyList()) }
    }

    override fun onCleared() {
        heart.stop()
        vehicle.stop()
        voice.release()
    }
}
