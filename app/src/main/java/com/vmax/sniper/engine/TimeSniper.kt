package com.vmax.sniper.engine

import android.util.Log
import com.aare.vmax.core.network.TimeSyncManager 
import kotlinx.coroutines.*
import java.util.*

/**
 * VMAX Sniper Precision Timer (V5.1 - The Final 10/10 Edition 🌍🎯)
 * IST Timezone + 0% CPU बर्बादी + Anti-Double-Fire Lock
 */
object TimeSniper {

    private var sniperJob: Job? = null
    private var hasFired = false 

    fun prepareSniper() {
        Log.d("VMAX_TIMER", "🔄 Syncing with Atomic Clock...")
        TimeSyncManager.syncWithNetwork()
    }

    fun scheduleFire(targetHour: Int, targetMinute: Int = 0, onFire: () -> Unit) {
        sniperJob?.cancel() 
        hasFired = false 

        // 🌍 10/10 FIX: हमेशा Indian Standard Time (IST) का ही इस्तेमाल करें
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val targetTimeMillis = Calendar.getInstance(istTimeZone).apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        Log.d("VMAX_TIMER", "⏳ Sniper Scheduled for Target: $targetTimeMillis ms (IST)")

        sniperJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && !hasFired) {
                val preciseTimeMillis = TimeSyncManager.currentTimeMillis()
                val timeLeft = targetTimeMillis - preciseTimeMillis

                // 🎯 Exact Millisecond Fire with Safety Lock!
                if (timeLeft <= 0 && !hasFired) {
                    hasFired = true // 🔒 लॉक!
                    Log.d("VMAX_TIMER", "🚀 TARGET REACHED: IMMORTAL ZERO! Firing...")
                    
                    withContext(Dispatchers.Main) {
                        onFire() 
                    }
                    break 
                }

                // 🧠 स्मार्ट डिले लॉजिक
                when {
                    timeLeft > 10000 -> delay(100) 
                    timeLeft > 1000  -> delay(10)  
                    else -> delay(1)               
                }
            }
        }
    }

    fun stopSniper() {
        sniperJob?.cancel()
        hasFired = false
        Log.d("VMAX_TIMER", "🛑 Sniper Deactivated by User")
    }
}
