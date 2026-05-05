package com.vmax.sniper.core.network

import android.util.Log
import kotlinx.coroutines.delay
import java.util.Calendar

object TimeSniper {

    private const val TAG = "TimeSniper"
    private var lastLoggedSecond = -1

    suspend fun scheduleFire(
        targetHour: Int,
        advanceMs: Long,
        onFire: () -> Unit
    ) {
        Log.d(TAG, "🎯 Scheduler started - Target: ${targetHour}:00:00, Advance: ${advanceMs}ms")
        lastLoggedSecond = -1
        
        while (true) {
            val now = TimeSyncManager.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = now }

            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val second = cal.get(Calendar.SECOND)
            val ms = cal.get(Calendar.MILLISECOND)

            val totalMs = ((hour * 3600L) + (minute * 60L) + second) * 1000L + ms
            val targetMs = targetHour * 3600L * 1000L

            val remaining = targetMs - advanceMs - totalMs

            when {
                remaining > 500 -> {
                    // Log only once per second to avoid spam
                    val remainingSec = (remaining / 1000).toInt()
                    if (remainingSec != lastLoggedSecond) {
                        lastLoggedSecond = remainingSec
                        Log.d(TAG, "⏰ Countdown: ${remainingSec}s remaining")
                    }
                    delay(remaining - 500)
                }
                remaining > 0 -> {
                    delay(1)
                }
                else -> {
                    Log.d(TAG, "🔥🔥🔥 FIRE! Time: ${TimeSyncManager.getPreciseTimeString()} 🔥🔥🔥")
                    onFire()
                    return
                }
            }
        }
    }
}
