package com.safebeat.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.safebeat.core.RiskAssessment
import com.safebeat.core.VehicleState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 신고 한 건의 내용 (관제센터 화면에 보여줄 것) */
data class EmergencyReport(
    val time: String,
    val latitude: Double,
    val longitude: Double,
    val lastBpm: Int?,
    val speedKmh: Int,
    val reasons: List<String>,
    val message: String,
)

interface EmergencyReporter {
    fun report(assessment: RiskAssessment, vehicle: VehicleState): EmergencyReport
}

/**
 * 데모용 신고기. 실제 119로는 절대 보내지 않는다(허위신고 방지).
 * 상용화 시: 보호자 문자 + 관제센터 확인 후 119 연계.
 * 위치는 LocationManager 사용 → Pleos FusedLocation SDK로 교체할 자리.
 */
class MockEmergencyReporter(private val context: Context) : EmergencyReporter {

    override fun report(assessment: RiskAssessment, vehicle: VehicleState): EmergencyReport {
        val loc = lastLocation()
        val lat = loc?.latitude ?: 37.5665 // 위치를 못 받으면 데모용 서울시청 좌표
        val lng = loc?.longitude ?: 126.9780
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        val msg = buildString {
            append("[SafeBeat 긴급] 운전자 의식 저하 의심, 15초간 무응답\n")
            append("시각 $time / 위치 https://maps.google.com/?q=$lat,$lng\n")
            append("마지막 심박 ${assessment.latestBpm ?: "신호 없음"} / 속도 ${vehicle.speedKmh.toInt()}km/h\n")
            append("판단 근거: ${assessment.reasons.joinToString(", ")}")
        }
        Log.i("SafeBeat", msg)
        return EmergencyReport(time, lat, lng, assessment.latestBpm, vehicle.speedKmh.toInt(), assessment.reasons, msg)
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(): Location? = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    } catch (_: Throwable) {
        null
    }
}
