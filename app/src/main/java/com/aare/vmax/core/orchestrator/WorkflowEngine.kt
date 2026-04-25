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
import com.aare.vmax.PassengerData
import kotlin.math.min

class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Sniper"
        const val ACTION_START_SNIPER = "com.aare.vmax.START_SNIPER"
        
        // Notification Constants
        private const val NOTIF_CHANNEL_ID = "vmax_sniper"
        private const val NOTIF_ID = 1
    }

    private var passengerList = ArrayList<PassengerData>()
    private var targetQuota = "General"
    private var isArmed = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        Log.d(TAG, "✅ Sniper Engine ONLINE! IRCTC रडार चालू है।")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SNIPER) {
            val quota = intent.getStringExtra("QUOTA") ?: "General"
            
            @Suppress("UNCHECKED_CAST")
            val passengers = intent.getSerializableExtra("PASSENGER_LIST") as? ArrayList<PassengerData> ?: arrayListOf()
            
            targetQuota = quota
            passengerList = passengers
            isArmed = true
            
            Log.d(TAG, "🔥 TARGET LOCKED! Quota: $targetQuota | ${passengerList.size} Passengers Loaded.")
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isArmed) return

        val root = rootInActiveWindow ?: return

        try {
            // कन्फर्म करें कि हम IRCTC ऐप के अंदर ही हैं
            if (root.packageName == "cris.org.in.prs.ima") {
                
                val nameNodes = root.findAccessibilityNodeInfosByViewId("cris.org.in.prs.ima:id/et_passenger_name")
                val ageNodes = root.findAccessibilityNodeInfosByViewId("cris.org.in.prs.ima:id/et_passenger_age")

                if (nameNodes.isNotEmpty() && ageNodes.isNotEmpty()) {
                    
                    // 1. पैसेंजर का डेटा भरना
                    for (i in 0 until min(nameNodes.size, passengerList.size)) {
                        val p = passengerList[i]
                        
                        if (nameNodes[i].text?.toString().isNullOrBlank() && p.name.isNotBlank()) {
                            fillTextField(nameNodes[i], p.name)
                            Log.d(TAG, "✍️ Filled Name: ${p.name}")
                            Thread.sleep(50) // ✅ बॉट डिटेक्शन से बचाव (Human-like delay)
                        }
                        
                        if (ageNodes[i].text?.toString().isNullOrBlank() && p.age.isNotBlank()) {
                            fillTextField(ageNodes[i], p.age)
                            Log.d(TAG, "✍️ Filled Age: ${p.age}")
                            Thread.sleep(50) // ✅ बॉट डिटेक्शन से बचाव
                        }
                    }

                    // 2. नए पैसेंजर का डिब्बा खोलना (अगर लिस्ट में और पैसेंजर हैं)
                    if (nameNodes.size < passengerList.size) {
                        // बटन को ID या फिर Text से ढूँढने का मास्टरस्ट्रोक
                        val addBtn = root.findAccessibilityNodeInfosByViewId("cris.org.in.prs.ima:id/tv_add_passanger")
                            .ifEmpty { root.findAccessibilityNodeInfosByText("Add Passenger") }
                            .ifEmpty { root.findAccessibilityNodeInfosByText("Add New") }
                        
                        if (addBtn.isNotEmpty()) {
                            addBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "➕ Clicked Add Passenger Button")
                            Thread.sleep(200) // ✅ नया डिब्बा खुलने का इंतज़ार
                        }
                    }
                }
            }
        } finally {
            // ✅ 3. मेमोरी लीक से 100% बचाव
            root.recycle()
        }
    }

    // ✍️ डब्बे में जादुई तरीके से टाइप करने का कोड
    private fun fillTextField(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    // Notification Channel Create करना (Android 8+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "VMAX Sniper",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // Notification का डिज़ाइन
    private fun buildNotification() = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
        .setContentTitle("🎯 VMAX Pro")
        .setContentText("Sniper armed | Monitoring IRCTC...")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    override fun onInterrupt() {
        Log.e(TAG, "⚠️ सर्विस रुक गई!")
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
        Log.d(TAG, "🛑 Sniper Engine OFF")
    }
}
