package com.safebeat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyCoordinatorTest {

    private class FakeActions : EmergencyActions {
        val log = mutableListOf<String>()
        override fun speak(text: String) { log += "speak" }
        override fun startListeningForResponse() { log += "listen" }
        override fun stopListening() { log += "stopListen" }
        override fun reportEmergency(assessment: RiskAssessment, vehicle: VehicleState) { log += "report" }
        override fun turnOnHazardLights() { log += "hazard" }
        override fun unlockDoors() { log += "unlock" }
    }

    private val driving = VehicleState(speedKmh = 60f, gear = Gear.DRIVE)
    private val parked = VehicleState(speedKmh = 0f, gear = Gear.PARK)

    /** 1초마다 bpm을 넣고 tick 한다. 마지막 시각을 돌려준다. */
    private fun feed(c: EmergencyCoordinator, startMs: Long, bpms: List<Int?>): Long {
        var t = startMs
        for (b in bpms) {
            c.onVital(VitalSample(t, b))
            c.tick(t)
            t += 1_000
        }
        return t - 1_000
    }

    @Test
    fun normalHeartRate_staysMonitoring() {
        val c = EmergencyCoordinator(RiskEngine(), FakeActions())
        c.onVehicle(driving)
        feed(c, 0, List(30) { 72 })
        assertEquals(SafetyState.Monitoring, c.state)
    }

    @Test
    fun exerciseHighHeartRate_onlyCaution_noEmergency() {
        val c = EmergencyCoordinator(RiskEngine(), FakeActions())
        c.onVehicle(driving)
        feed(c, 0, List(30) { 160 })
        assertTrue(c.state is SafetyState.Caution)
    }

    @Test
    fun suddenDropWhileDriving_checksThenEmergencyAfter15s() {
        val actions = FakeActions()
        val c = EmergencyCoordinator(RiskEngine(), actions)
        c.onVehicle(driving)
        // 75 → 33 으로 떨어진 그 순간(t)부터 15초 카운트다운이 시작된다
        val t = feed(c, 0, List(10) { 75 } + List(1) { 33 })
        assertTrue(c.state is SafetyState.Checking)

        c.tick(t + 14_000)
        assertTrue(c.state is SafetyState.Checking)

        c.tick(t + 15_500)
        assertTrue(c.state is SafetyState.Emergency)
        assertTrue("report" in actions.log)
        assertTrue("hazard" in actions.log)
        // 주행 중에는 문을 열지 않는다
        assertTrue("unlock" !in actions.log)

        // 차가 멈추면 문을 연다
        c.onVehicle(parked)
        c.tick(t + 20_000)
        assertTrue("unlock" in actions.log)
    }

    @Test
    fun driverSaysOk_cancelsAndCoolsDown() {
        val actions = FakeActions()
        val c = EmergencyCoordinator(RiskEngine(), actions)
        c.onVehicle(driving)
        val t = feed(c, 0, List(10) { 75 } + List(3) { 33 })
        assertTrue(c.state is SafetyState.Checking)

        c.driverResponded(t + 3_000)
        assertEquals(SafetyState.Monitoring, c.state)

        // 쿨다운 중에는 같은 이상이 있어도 다시 확인 단계로 가지 않는다
        c.onVital(VitalSample(t + 4_000, 33))
        assertTrue(c.tick(t + 4_000) !is SafetyState.Checking)
        assertTrue("report" !in actions.log)
    }

    @Test
    fun signalLossWhileDriving_triggersCheck() {
        val c = EmergencyCoordinator(RiskEngine(), FakeActions())
        c.onVehicle(driving)
        val t = feed(c, 0, List(10) { 75 })
        c.tick(t + 11_000)
        assertTrue(c.state is SafetyState.Checking)
    }

    @Test
    fun signalLossWhileParked_onlyCaution() {
        val c = EmergencyCoordinator(RiskEngine(), FakeActions())
        c.onVehicle(parked)
        val t = feed(c, 0, List(10) { 75 })
        c.tick(t + 11_000)
        assertTrue(c.state is SafetyState.Caution)
    }
}
