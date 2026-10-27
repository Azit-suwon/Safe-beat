package com.safebeat.core

/**
 * 단계별 에스컬레이션: 감시 → (주의) → 15초 확인 → 긴급 조치
 * 시간은 밖에서 tick(now)로 넣어준다. 그래서 안드로이드 없이도 테스트할 수 있다.
 */
class EmergencyCoordinator(
    private val engine: RiskEngine,
    private val actions: EmergencyActions,
    private val countdownMs: Long = 15_000,
    private val cooldownMs: Long = 30_000,
) {
    var state: SafetyState = SafetyState.Monitoring
        private set

    private var vehicle = VehicleState()
    private var cooldownUntilMs = 0L
    private var cautionSpoken = false
    private var doorsUnlocked = false

    fun onVital(sample: VitalSample) = engine.add(sample)

    fun onVehicle(v: VehicleState) {
        vehicle = v
    }

    fun currentVehicle(): VehicleState = vehicle

    fun tick(nowMs: Long): SafetyState {
        val current = state
        when (current) {
            is SafetyState.Emergency -> {
                // 긴급 상황은 사람이 reset 할 때까지 유지. 차가 멈추면 그때 문을 연다.
                if (!doorsUnlocked && !vehicle.isMoving) {
                    actions.unlockDoors()
                    doorsUnlocked = true
                }
                return current
            }
            is SafetyState.Checking -> {
                if (nowMs >= current.deadlineMs) {
                    actions.stopListening()
                    enterEmergency(current.assessment, nowMs)
                }
                return state
            }
            else -> Unit
        }

        val a = engine.assess(nowMs, vehicle)
        state = when {
            a.level == RiskLevel.SUSPECT && nowMs >= cooldownUntilMs -> {
                actions.speak("괜찮으세요? 괜찮으시면 '괜찮아'라고 말하거나 화면 버튼을 눌러주세요.")
                actions.startListeningForResponse()
                SafetyState.Checking(a, nowMs + countdownMs)
            }
            a.level != RiskLevel.NORMAL -> {
                if (!cautionSpoken) {
                    actions.speak("심박이 평소와 달라요. 가까운 쉼터에서 잠시 쉬어가는 건 어떨까요?")
                    cautionSpoken = true
                }
                SafetyState.Caution(a)
            }
            else -> {
                cautionSpoken = false
                SafetyState.Monitoring
            }
        }
        return state
    }

    /** 운전자가 음성("괜찮아") 또는 버튼으로 응답했을 때 */
    fun driverResponded(nowMs: Long) {
        if (state is SafetyState.Checking) {
            actions.stopListening()
            actions.speak("확인했어요. 안전운전 하세요.")
            cooldownUntilMs = nowMs + cooldownMs
            state = SafetyState.Monitoring
        }
    }

    /** 데모 / 구조 완료 후 처음 상태로 */
    fun reset() {
        engine.clear()
        cooldownUntilMs = 0
        cautionSpoken = false
        doorsUnlocked = false
        state = SafetyState.Monitoring
    }

    private fun enterEmergency(a: RiskAssessment, nowMs: Long) {
        state = SafetyState.Emergency(a, nowMs)
        actions.reportEmergency(a, vehicle)
        actions.turnOnHazardLights()
        if (!vehicle.isMoving) {
            // 구조대가 문을 열 수 있게, 차가 멈춘 상태에서만 잠금 해제
            actions.unlockDoors()
            doorsUnlocked = true
        }
        actions.speak("응답이 없어 119와 보호자에게 위치를 알렸어요. 비상등을 켰어요.")
    }
}
