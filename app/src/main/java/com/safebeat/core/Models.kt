package com.safebeat.core

/** 워치에서 받은 심박 한 개. bpm == null 이면 신호 없음(워치 미착용·연결 끊김). */
data class VitalSample(val timeMs: Long, val bpm: Int?)

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

data class VehicleState(
    val speedKmh: Float = 0f,
    val gear: Gear = Gear.PARK,
) {
    val isMoving: Boolean get() = speedKmh >= 5f
}

enum class RiskLevel { NORMAL, CAUTION, SUSPECT }

data class RiskAssessment(
    val level: RiskLevel,
    val score: Int,
    val reasons: List<String>,
    val latestBpm: Int?,
)

/** 차량 화면에 보여줄 전체 상태 */
sealed interface SafetyState {
    data object Monitoring : SafetyState
    data class Caution(val assessment: RiskAssessment) : SafetyState
    data class Checking(val assessment: RiskAssessment, val deadlineMs: Long) : SafetyState
    data class Emergency(val assessment: RiskAssessment, val startedAtMs: Long) : SafetyState
}

/** 긴급 상황에서 실행할 행동. 안드로이드/Pleos 쪽에서 실제 구현을 넣는다. */
interface EmergencyActions {
    fun speak(text: String)
    fun startListeningForResponse()
    fun stopListening()
    fun reportEmergency(assessment: RiskAssessment, vehicle: VehicleState)
    fun turnOnHazardLights()
    fun unlockDoors()
}
