package com.safebeat.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safebeat.BuildConfig
import com.safebeat.data.HeartScenario

class MainActivity : ComponentActivity() {

    private val vm: SafeBeatViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    /**
     * 시연 중 노트북에서 시나리오를 바꾸는 명령 (디버그 빌드만):
     * adb shell am broadcast -a com.safebeat.DEBUG --es scenario FAINT
     * adb shell am broadcast -a com.safebeat.DEBUG --es drive false
     * adb shell am broadcast -a com.safebeat.DEBUG --es reset true
     */
    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            intent.getStringExtra("scenario")?.let { name ->
                HeartScenario.entries.firstOrNull { it.name == name.uppercase() }?.let(vm::setScenario)
            }
            intent.getStringExtra("drive")?.let { vm.setDriving(it.toBoolean()) }
            if (intent.getStringExtra("reset") == "true") vm.resetDemo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                "android.car.permission.CAR_SPEED",
            ),
        )
        if (BuildConfig.DEBUG) {
            val filter = IntentFilter("com.safebeat.DEBUG")
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(debugReceiver, filter, RECEIVER_EXPORTED)
            else registerReceiver(debugReceiver, filter)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val ui by vm.ui.collectAsStateWithLifecycle()
                SafeBeatApp(vm, ui)
            }
        }
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) unregisterReceiver(debugReceiver)
        super.onDestroy()
    }
}
