package id.psw.vshlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class VSHSystemInfo {
    var batteryLevel : Float = 0.0f
    var isCharging : Boolean = false
}

private val vshSystemInfo = VSHSystemInfo()
private var batteryReceiverRegistered = false

private fun updateBatteryStateFromIntent(intent: Intent?) {
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
    val chargeState = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    vshSystemInfo.isCharging =
        chargeState == BatteryManager.BATTERY_STATUS_CHARGING ||
            chargeState == BatteryManager.BATTERY_STATUS_FULL
    vshSystemInfo.batteryLevel =
        if (scale > 0) level.toFloat() / scale.toFloat() else 0.0f
}

fun Vsh.updateBatteryInfo(){
    if (batteryReceiverRegistered) return

    val stickyIntent = registerReceiver(
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateBatteryStateFromIntent(intent)
            }
        },
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )
    updateBatteryStateFromIntent(stickyIntent)
    batteryReceiverRegistered = true
}

fun Vsh.getBatteryLevel() : Float = vshSystemInfo.batteryLevel
fun Vsh.isBatteryCharging() : Boolean = vshSystemInfo.isCharging
