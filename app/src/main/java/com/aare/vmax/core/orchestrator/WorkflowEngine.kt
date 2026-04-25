package com.aare.vmax.core.engine

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.aare.vmax.core.model.SniperTask
import com.aare.vmax.core.network.TimeSyncManager
import kotlinx.coroutines.*
import java.util.Calendar
import kotlin.math.min

class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Sniper"
        const val ACTION_START_SNIPER = "com.aare.vmax.START_SNIPER"
        private const val NOTIF_CHANNEL_ID = "vmax_sniper"
        private const val NOTIF_ID = 1
    }

    // 🚀 Coroutine Scope बैकग्राउंड टाइमर के लिए
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    private var currentTask: SniperTask? = null
    private var isArmed = false // यह तब तक false रहेगा जब तक टाइमर 0 न हो जाए

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        Log.d(TAG, "✅ Sniper Engine ONLINE! Waiting for Task...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SNIPER) {
            
            // 🎯 अब हम पूरा का पूरा SniperTask रिसीव कर रहे हैं
            val task = intent.getParcelableExtra<SniperTask>("SNIPER_TASK")
            
            if (task != null) {
                currentTask = task
                Log.d(TAG, "🔥 TARGET LOCKED! Train: ${task.trainNumber} | MS Advance: ${task.msAdvance}")
                
                // 🚀 टाइमर स्टार्ट करें! (Coroutine के अंदर)
                serviceScope.launch {
                    executeWithPrecision(task)
                }
            }
        }
        return START_STICKY
    }

    // ==========================================
    // ⏰ THE TIME SNIPER LOGIC (आपका दिया हुआ कोड)
    // ==========================================
    private suspend fun executeWithPrecision(task: SniperTask) {
        // NTP से सटीक समय प्राप्त करें
        if (!TimeSyncManager.isSynced()) {
            TimeSyncManager.syncWithFallback()
            delay(1000)
        }
        
        // मान लेते हैं task में triggerTime "10:00:00" या "11:00:00" है
        val targetHour = if (task.triggerTime.startsWith("10")) 10 else 11
        
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // 🎯 Advance time sniper: msAdvance milliseconds पहले
        val triggerTime = targetTime - task.msAdvance
        
        Log.d(TAG, "⏳ Countdown Started... Target MS: $triggerTime")

        // 🚀 CPU बचाने के लिए स्मार्ट लूप
        while (true) {
            val now = TimeSyncManager.currentTimeMillis()
            val diff = triggerTime - now
            
            if (diff <= 0) break // समय पूरा हुआ! FIRE!
            
            // अगर टाइम ज़्यादा है तो लंबी सांस लें, अगर 50ms बचे हैं तो पागलों की तरह चेक करें
            if (diff > 1000) delay(500)
            else if (diff > 50) delay(10)
            else delay(1) // हर 1ms चेक करो – सुपर सटीकता!
        }
        
        // ⚡ EXACT TIME REACHED! Execute booking
        val exactTime = TimeSyncManager.getPreciseTimeString()
        Log.d(TAG, "🎯 FIRING at: $exactTime")
        executeBooking(task)
    }

    // ==========================================
    // 🔫 THE TRIGGER (समय पूरा होने पर क्या होगा)
    // ==========================================
    private fun executeBooking(task: SniperTask) {
        isArmed = true // अब आपका AccessibilityEvent काम करना शुरू करेगा
        
        // 🚀 यहाँ आप वो कोड डाल सकते हैं जो 10:00:00 बजते ही सबसे पहले ट्रेन या क्लास पर क्लिक करेगा
        Log.d(TAG, "🔓 SNIPER IS NOW ARMED AND ACTIVE ON SCREEN!")
    }

    // ==========================================
    // ✍️ THE AUTO FILL LOGIC (आपका पुराना कोड)
    // ==========================================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // जब तक executeBooking() फायर नहीं होता, तब तक यह कुछ नहीं करेगा
        if (!isArmed) return
        val task = currentTask ?: return

        val root = rootInActiveWindow ?: return

        try {
            if (root.packageName == "cris.org.in.prs.ima") {
                val nameNodes = root.findAccessibilityNodeInfosByViewId("cris.org.in.prs.ima:id/et_passenger_name")
                val ageNodes = root.findAccessibilityNodeInfosByViewId("cris.org.in.prs.ima:id/et_passenger_age")

                if (nameNodes.isNotEmpty() && ageNodes.isNotEmpty()) {
                    val passengers = task.passengers

                    for (i in 0 until min(nameNodes.size, passengers.size)) {
                        val p = passengers[i]
                        
                        if (nameNodes[i].text?.toString().isNullOrBlank() && p.name.isNotBlank()) {
                            fillTextField(nameNodes[i], p.name)
                            Thread.sleep(50) 
                        }
                        
                        if (ageNodes[i].text?.toString().isNullOrBlank() && p.age.isNotBlank()) {
                            fillTextField(ageNodes[i], p.age)
                            Thread.sleep(50) 
                        }
                    }

                    if (nameNodes.size < passengers.size) {
                        val addBtn = root.findAccessibilityNodeInfosByViewId("cris.org.in.prs.ima:id/tv_add_passanger")
                            .ifEmpty { root.findAccessibilityNodeInfosByText("Add Passenger") }
                        
                        if (addBtn.isNotEmpty()) {
                            addBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Thread.sleep(200) 
                        }
                    }
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun fillTextField(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIF_CHANNEL_ID, "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
        .setContentTitle("🎯 VMAX Pro")
        .setContentText("Sniper countdown active...")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    override fun onInterrupt() {}

    override fun onDestroy() {
        serviceScope.cancel() // 🧹 सर्विस बंद होने पर टाइमर को भी मार दें (Memory Leak बचाव)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
        Log.d(TAG, "🛑 Sniper Engine OFF")
    }
}
