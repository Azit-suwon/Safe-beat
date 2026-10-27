package com.safebeat.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safebeat.core.SafetyState
import com.safebeat.data.HeartScenario

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF1A1F27)
private val Ink = Color(0xFFF2F4F7)
private val Quiet = Color(0xFF98A2B3)
private val Ok = Color(0xFF32D583)
private val Warn = Color(0xFFFDB022)
private val Danger = Color(0xFFF04438)

@Composable
fun SafeBeatApp(vm: SafeBeatViewModel, ui: UiState) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Dashboard(vm, ui)
        when (val s = ui.safety) {
            is SafetyState.Checking -> CheckingOverlay(ui, s) { vm.driverSaysOk() }
            is SafetyState.Emergency ->
                if (ui.showRescueMode) RescueMode { vm.showRescueMode(false) }
                else EmergencyOverlay(vm, ui)
            else -> Unit
        }
    }
}

@Composable
private fun Dashboard(vm: SafeBeatViewModel, ui: UiState) {
    val (statusText, statusColor) = when (val s = ui.safety) {
        SafetyState.Monitoring -> "정상 · 운전자를 지키고 있어요" to Ok
        is SafetyState.Caution -> "주의 · ${s.assessment.reasons.firstOrNull() ?: ""}" to Warn
        is SafetyState.Checking -> "의식 확인 중" to Danger
        is SafetyState.Emergency -> "긴급 상황" to Danger
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 900.dp) {
            // 차량 와이드 화면: 왼쪽 심박, 오른쪽 차량·시연 패널
            Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HeartCard(ui, statusText, statusColor, Modifier.weight(1.3f).fillMaxSize())
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    VehiclePanel(ui)
                    DemoPanel(vm, ui, Modifier.verticalScroll(rememberScrollState()))
                }
            }
        } else {
            // 작은 화면(폰 에뮬레이터 등): 위에서 아래로 쌓고 전체 스크롤
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeartCard(ui, statusText, statusColor, Modifier.fillMaxWidth())
                VehiclePanel(ui)
                DemoPanel(vm, ui)
            }
        }
    }
}

@Composable
private fun HeartCard(ui: UiState, statusText: String, statusColor: Color, modifier: Modifier) {
    Column(modifier.background(Card, RoundedCornerShape(20.dp)).padding(28.dp)) {
        Text("SafeBeat", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(statusText, color = statusColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(ui.bpm?.toString() ?: "--", color = Ink, fontSize = 96.sp, fontWeight = FontWeight.Bold)
            Text("  bpm", color = Quiet, fontSize = 24.sp, modifier = Modifier.padding(bottom = 20.dp))
        }
        Text(if (ui.bpm == null) "워치 신호 없음" else "워치 실시간 심박", color = Quiet, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        HeartLine(ui.bpmHistory, statusColor, Modifier.fillMaxWidth().height(140.dp))
    }
}

@Composable
private fun HeartLine(values: List<Int?>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val lo = 30f
        val hi = 180f
        val stepX = size.width / 59f
        val path = Path()
        var started = false
        values.forEachIndexed { i, v ->
            if (v == null) {
                started = false
                return@forEachIndexed
            }
            val x = i * stepX
            val y = size.height * (1f - ((v - lo) / (hi - lo)).coerceIn(0f, 1f))
            if (!started) path.moveTo(x, y) else path.lineTo(x, y)
            started = true
        }
        // 40 / 150 기준선
        listOf(40f, 150f).forEach { ref ->
            val y = size.height * (1f - (ref - lo) / (hi - lo))
            drawLine(Quiet.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
        drawPath(path, color, style = Stroke(width = 5f))
    }
}

@Composable
private fun VehiclePanel(ui: UiState) {
    val blink by rememberInfiniteTransition(label = "hazard").animateFloat(
        1f, 0.15f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink",
    )
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(20.dp)).padding(20.dp)) {
        Text(
            if (ui.usingRealCar) "차량 상태 (차량 신호 연동)" else "차량 상태 (시연용 가상 차량)",
            color = Quiet, fontSize = 15.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("${ui.vehicle.speedKmh.toInt()} km/h · ${gearLabel(ui.vehicle.gear.name)}", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusPill(
                "비상등 ${if (ui.hazardOn) "ON" else "OFF"}",
                if (ui.hazardOn) Warn else Quiet,
                Modifier.alpha(if (ui.hazardOn) blink else 1f),
            )
            StatusPill("도어 ${if (ui.doorsUnlocked) "잠금 해제" else "잠김"}", if (ui.doorsUnlocked) Ok else Quiet)
        }
    }
}

private fun gearLabel(name: String) = when (name) {
    "PARK" -> "P"; "DRIVE" -> "D"; "REVERSE" -> "R"; "NEUTRAL" -> "N"; else -> "-"
}

@Composable
private fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DemoPanel(vm: SafeBeatViewModel, ui: UiState, modifier: Modifier = Modifier) {
    Column(
        Modifier.fillMaxWidth().background(Card, RoundedCornerShape(20.dp)).then(modifier).padding(20.dp),
    ) {
        Text("시연 패널", color = Quiet, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        HeartScenario.entries.forEach { s ->
            FilterChip(
                selected = ui.scenario == s,
                onClick = { vm.setScenario(s) },
                label = { Text(s.label, fontSize = 16.sp) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.setDriving(true) }, enabled = !ui.usingRealCar) { Text("주행") }
            OutlinedButton(onClick = { vm.setDriving(false) }, enabled = !ui.usingRealCar) { Text("정차") }
            OutlinedButton(onClick = { vm.useRealCar(!ui.usingRealCar) }) {
                Text(if (ui.usingRealCar) "가상 차량" else "차량 신호")
            }
        }
        OutlinedButton(onClick = { vm.resetDemo() }, modifier = Modifier.fillMaxWidth()) { Text("처음으로") }
    }
}

@Composable
private fun CheckingOverlay(ui: UiState, s: SafetyState.Checking, onOk: () -> Unit) {
    val left = ((s.deadlineMs - ui.nowMs) / 1000 + 1).coerceIn(0, 15)
    Column(
        Modifier.fillMaxSize().background(Danger.copy(alpha = 0.96f)).verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("괜찮으세요?", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(s.assessment.reasons.joinToString(" · "), color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text("$left", color = Color.White, fontSize = 140.sp, fontWeight = FontWeight.Black)
        Text("초 후 119와 보호자에게 자동으로 알려요", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onOk,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Danger),
            modifier = Modifier.fillMaxWidth(0.7f).height(110.dp),
        ) { Text("괜찮아요", fontSize = 40.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(16.dp))
        Text("또는 \"괜찮아\"라고 말해 주세요", color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun EmergencyOverlay(vm: SafeBeatViewModel, ui: UiState) {
    Row(Modifier.fillMaxSize().background(Color(0xFF3A0B0B)).verticalScroll(rememberScrollState()).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f)) {
            Text("긴급 상황", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Step("119 · 보호자 신고", ui.report != null)
            Step("비상등 켬", ui.hazardOn)
            Step(if (ui.doorsUnlocked) "도어 잠금 해제" else "도어 해제 대기 (정차 후 자동)", ui.doorsUnlocked)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.showRescueMode(true) }, modifier = Modifier.height(72.dp)) {
                    Text("구조자 모드", fontSize = 24.sp)
                }
                OutlinedButton(onClick = { vm.resetDemo() }, modifier = Modifier.height(72.dp)) {
                    Text("데모 초기화", fontSize = 20.sp, color = Color.White)
                }
            }
        }
        // 관제센터가 받는 신고 내용(데모)
        Column(Modifier.weight(1f).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(20.dp)) {
            Text("관제센터 수신 내용 (데모 · 실제 119 미발송)", color = Quiet, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text(ui.report?.message ?: "전송 중…", color = Color.White, fontSize = 18.sp, lineHeight = 26.sp)
        }
    }
}

@Composable
private fun Step(text: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(Modifier.size(28.dp).background(if (done) Ok else Quiet.copy(alpha = 0.4f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else "…", color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 26.sp)
    }
}

/** 행인·구급대원용 화면: 크게, 밝게, 필요한 정보만 */
@Composable
private fun RescueMode(onBack: () -> Unit) {
    Row(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f)) {
            Text("구조자 안내", color = Danger, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("119에 이미 신고되었습니다", color = Color.Black, fontSize = 22.sp)
            Spacer(Modifier.height(16.dp))
            Info("혈액형", "A형 Rh+")
            Info("지병", "고혈압, 부정맥")
            Info("복용약", "항응고제")
            Info("알레르기", "페니실린")
            Info("보호자", "010-0000-0000")
            Text("※ 시연용 예시 프로필", color = Color.Gray, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("의식·호흡이 없으면", color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            listOf(
                "1. 시동을 끄고 운전자를 평평한 곳에 눕혀요",
                "2. 가슴 가운데를 5cm 깊이로 세게 눌러요",
                "3. 1분에 100~120번, 구급대가 올 때까지",
                "4. 주변에 자동심장충격기(AED)를 부탁해요",
            ).forEach { Text(it, color = Color.Black, fontSize = 22.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBack) { Text("돌아가기", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = Color.Gray, fontSize = 22.sp, modifier = Modifier.width(120.dp))
        Text(value, color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}
