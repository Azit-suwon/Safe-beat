package com.safebeat.data

import com.safebeat.core.VitalSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 심박 입력. 지금은 시뮬레이터, 나중에 워치(폰 → 서버 → 차량) 연동으로 교체한다. */
interface HeartRateSource {
    fun start(scope: CoroutineScope, onSample: (VitalSample) -> Unit)
    fun stop()
}

enum class HeartScenario(val label: String) {
    NORMAL("정상 운전"),
    EXERCISE("운동 직후 (오탐 방지)"),
    FAINT("의식 상실"),
    SIGNAL_LOSS("워치 신호 끊김"),
}

/**
 * PT 시연용 심박 시뮬레이터. 1초마다 값을 만든다.
 * 시연 버튼이나 adb 명령으로 시나리오를 바꿀 수 있다.
 */
class SimulatedHeartRateSource : HeartRateSource {
    private val _scenario = MutableStateFlow(HeartScenario.NORMAL)
    val scenario: StateFlow<HeartScenario> = _scenario
    private var job: Job? = null
    private var bpm = 72.0

    fun setScenario(s: HeartScenario) {
        _scenario.value = s
    }

    override fun start(scope: CoroutineScope, onSample: (VitalSample) -> Unit) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val value: Int? = when (_scenario.value) {
                    HeartScenario.NORMAL -> approach(72.0, 4.0)
                    HeartScenario.EXERCISE -> approach(160.0, 6.0)
                    // 실제 실신은 수 초 안에 급락한다 → 한 번에 크게 떨어뜨림
                    HeartScenario.FAINT -> approach(34.0, 40.0)
                    HeartScenario.SIGNAL_LOSS -> null
                }
                onSample(VitalSample(System.currentTimeMillis(), value))
                delay(1_000)
            }
        }
    }

    private fun approach(target: Double, step: Double): Int {
        bpm += (target - bpm).coerceIn(-step, step)
        return (bpm + Random.nextDouble(-1.5, 1.5)).toInt()
    }

    override fun stop() {
        job?.cancel()
    }
}
