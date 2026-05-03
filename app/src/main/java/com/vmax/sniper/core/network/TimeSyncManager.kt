package com.vmax.sniper.core.network

import kotlinx.coroutines.*
import java.util.Calendar

object TimeSniper {
    
    fun scheduleFire(targetHour: Int, latencyMs: Long = 400L, onFire: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!TimeSyncManager.isSynced()) {
                TimeSyncManager.syncWithNetwork()
                delay(1500)
            }
            
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val triggerTime = targetTime - latencyMs
            
            while (TimeSyncManager.currentTimeMillis() < triggerTime) {
                delay(1)
            }
            
            android.util.Log.d("TimeSniper", "🎯 FIRING at ${TimeSyncManager.getPreciseTimeString()}")
            onFire()
        }
    }
}
