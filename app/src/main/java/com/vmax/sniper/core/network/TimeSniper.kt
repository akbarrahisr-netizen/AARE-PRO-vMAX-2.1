package com.vmax.sniper.engine

import kotlinx.coroutines.*
import java.util.*

/**
 * 🎯 VMAX SNIPER TRIGGER
 * यह फाइल 1-1 मिलीसेकंड का हिसाब रखती है।
 */
object TimeSniper {
    private var sniperJob: Job? = null
    private var hasFired = false

    // 1. सबसे पहले परमाणु घड़ी को तैयार करो
    fun prepareSniper() = TimeSyncManager.syncWithNetwork()

    // 2. बुकिंग का समय सेट करो (10 या 11 बजे)
    fun scheduleFire(hour: Int, min: Int = 0, onFire: () -> Unit) {
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val target = Calendar.getInstance(istTimeZone).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        hasFired = false
        
        // एक अलग 'थ्रेड' में टाइम चेक करना शुरू करो
        sniperJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && !hasFired) {
                // परमाणु समय (System Time + Offset) प्राप्त करें
                val preciseNow = TimeSyncManager.getNetworkTime()
                val timeLeft = target - preciseNow
                
                // 🚀 हेडशॉट! (Target Reached)
                if (timeLeft <= 0) {
                    hasFired = true
                    withContext(Dispatchers.Main) {
                        onFire() // यहाँ से WorkflowEngine चालू होता है
                    }
                    break
                }
                
                // 🧠 स्मार्ट डिले (CPU बचाने के लिए उस्ताद की ट्रिक)
                when {
                    timeLeft > 10000 -> delay(100) // 10 सेकंड से ज़्यादा दूर: 100ms आराम
                    timeLeft > 1000  -> delay(10)  // 1 से 10 सेकंड की दूरी: 10ms सतर्क
                    else -> delay(1)           // आखिरी 1 सेकंड: 1ms (Super Fast!)
                }
            }
        }
    }

    fun stopSniper() {
        sniperJob?.cancel()
        hasFired = false
    }
}
