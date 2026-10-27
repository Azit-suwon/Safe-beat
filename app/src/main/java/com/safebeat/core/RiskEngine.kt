package com.safebeat.core

/**
 * 심박 + 차량 상태로 위험도를 점수화한다.
 *
 * 점수표 (합계가 suspectScore 이상이면 SUSPECT → 15초 확인 단계로 간다)
 *  - 서맥: lowBpm 미만이 sustainMs 동안 지속       +3
 *  - 급격한 하강: dropWindowMs 안에 suddenDropBpm 이상 떨어짐  +2
 *  - 신호 소실: 신호가 있다가 signalLossMs 동안 끊김  +2
 *  - 빈맥: highBpm 초과가 sustainMs 동안 지속     +1  (운동 직후 등 오탐 많음 → 단독으론 주의만)
 *  - 주행 중이면 위 항목이 하나라도 있을 때      +1
 *
 * 그래서 "운동 후 심박 160 + 주행"은 2점 → 주의(휴식 권유)만,
 * "심박 75→35 급락 + 주행"은 3점 이상 → 의식 확인 단계로 간다.
 * 임계값은 기획서/PT에서 근거와 함께 설명하고, 실제 데이터로 조정할 수 있게 Config로 분리했다.
 */
class RiskEngine(private val cfg: Config = Config()) {

    data class Config(
        val lowBpm: Int = 40,
        val highBpm: Int = 150,
        val sustainMs: Long = 5_000,
        val suddenDropBpm: Int = 40,
        val dropWindowMs: Long = 10_000,
        val signalLossMs: Long = 10_000,
        val suspectScore: Int = 3,
        val historyMs: Long = 60_000,
    )

    private val history = ArrayDeque<VitalSample>()

    fun add(sample: VitalSample) {
        history.addLast(sample)
        while (history.isNotEmpty() && history.first().timeMs < sample.timeMs - cfg.historyMs) {
            history.removeFirst()
        }
    }

    fun clear() = history.clear()

    fun assess(nowMs: Long, vehicle: VehicleState): RiskAssessment {
        val valid = history.filter { it.bpm != null }
        val latest = valid.lastOrNull()
        if (latest == null) {
            return RiskAssessment(RiskLevel.NORMAL, 0, listOf("워치 연결 대기 중"), null)
        }

        var score = 0
        val reasons = mutableListOf<String>()

        val signalLost = nowMs - latest.timeMs >= cfg.signalLossMs
        if (signalLost) {
            score += 2
            reasons += "심박 신호가 ${(nowMs - latest.timeMs) / 1000}초째 끊김"
        } else {
            if (sustained(nowMs) { it < cfg.lowBpm }) {
                score += 3
                reasons += "심박 ${cfg.lowBpm}회 미만 지속 (현재 ${latest.bpm})"
            }
            val recentMax = valid.filter { it.timeMs >= nowMs - cfg.dropWindowMs }.maxOf { it.bpm!! }
            if (recentMax - latest.bpm!! >= cfg.suddenDropBpm) {
                score += 2
                reasons += "${cfg.dropWindowMs / 1000}초 안에 심박 급락 ($recentMax → ${latest.bpm})"
            }
            if (sustained(nowMs) { it > cfg.highBpm }) {
                score += 1
                reasons += "심박 ${cfg.highBpm}회 초과 지속 (현재 ${latest.bpm})"
            }
        }

        if (score > 0 && vehicle.isMoving) {
            score += 1
            reasons += "주행 중 (${vehicle.speedKmh.toInt()} km/h)"
        }

        val level = when {
            score >= cfg.suspectScore -> RiskLevel.SUSPECT
            score > 0 -> RiskLevel.CAUTION
            else -> RiskLevel.NORMAL
        }
        return RiskAssessment(level, score, reasons, if (signalLost) null else latest.bpm)
    }

    /** 최근 sustainMs 구간이 충분히 채워져 있고, 그 구간의 모든 값이 조건을 만족하는가 */
    private fun sustained(nowMs: Long, predicate: (Int) -> Boolean): Boolean {
        val from = nowMs - cfg.sustainMs
        val window = history.filter { it.timeMs >= from && it.bpm != null }
        if (window.size < 3) return false
        if (window.first().timeMs > from + 1_500) return false
        return window.all { predicate(it.bpm!!) }
    }
}
