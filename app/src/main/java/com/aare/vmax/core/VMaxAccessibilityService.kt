package com.aare.vmax.core.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.aare.vmax.core.orchestrator.*
import com.aare.vmax.ui.FloatingPanelManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class VMaxAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Service"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vmax_service_channel"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val isInitialized = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)

    private var engine: WorkflowEngine? = null
    private var orchestrator: AutomationOrchestrator? = null
    private var panelManager: FloatingPanelManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.d(TAG, "🔌 Service Connected")

        startForegroundIfNeeded()

        serviceScope.launch {
            try {
                val gestureDispatcher = object : GestureDispatcher {
                    override fun dispatchGesture(
                        gesture: GestureDescription,
                        callback: GestureResultCallback?,
                        handler: Handler?
                    ): Boolean {
                        return this@VMaxAccessibilityService.dispatchGesture(
                            gesture, callback, handler
                        )
                    }
                }

                // बिना नाम के पास किया ताकि नाम का कोई कन्फ्यूज़न न हो
                engine = WorkflowEngine(
                    { rootInActiveWindow },
                    gestureDispatcher,
                    EngineConfig(),
                    serviceScope
                )

                orchestrator = AutomationOrchestrator(
                    engine!!,
                    serviceScope
                )

                // यहाँ engine वापस डाल दिया है
                panelManager = FloatingPanelManager(
                    this@VMaxAccessibilityService,
                    engine!!,
                    orchestrator!!,
                    serviceScope
                )

                engine?.startReactiveListening(orchestrator!!.eventFlow)

                isInitialized.set(true)
                isRunning.set(true)

                Log.d(TAG, "✅ Initialized")

                withContext(Dispatchers.Main) {
                    if (Settings.canDrawOverlays(this@VMaxAccessibilityService)) {
                        panelManager?.show()
                    } else {
                        Log.w(TAG, "Overlay permission missing")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Init failed", e)
                cleanup()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isInitialized.get() || !isRunning.get()) return

        serviceScope.launch {
            try {
                engine?.notifyEvent(event)
                orchestrator?.onAccessibilityEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Event error", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Interrupted")
        isRunning.set(false)
    }

    override fun onDestroy() {
        Log.d(TAG, "🛑 Destroying")

        isRunning.set(false)
        isInitialized.set(false)

        cleanup()
        serviceScope.cancel()

        super.onDestroy()
    }

    // ✅ Foreground Service (आपका कमाल का आईडिया)
    private fun startForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                CHANNEL_ID,
                "VMAX Service",
                NotificationManager.IMPORTANCE_LOW
            )

            nm.createNotificationChannel(channel)

            // R.drawable.ic_notification की जगह एंड्रॉइड का डिफ़ॉल्ट आइकॉन लगाया है ताकि एरर न आए
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("VMAX Running")
                .setContentText("Automation active")
                .setSmallIcon(android.R.drawable.btn_star) 
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)

        } catch (e: Exception) {
            Log.e(TAG, "Foreground failed", e)
        }
    }

    private fun cleanup() {
        try {
            panelManager?.remove()
            engine?.shutdown()
            orchestrator?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }
}
