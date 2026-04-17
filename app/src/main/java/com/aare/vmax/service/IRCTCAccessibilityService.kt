package com.aare.vmax.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.aare.vmax.core.engine.SpatialHeuristicEngine
import com.aare.vmax.core.engine.HumanMimeticEngine
import kotlinx.coroutines.*

class IRCTCAccessibilityService : AccessibilityService() {

    private val spatialEngine = SpatialHeuristicEngine()
    private val humanEngine = HumanMimeticEngine()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VMAX_LOG", "🚀 AARE-PRO vMAX Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        val rootNode = rootInActiveWindow ?: return

        when (event?.eventType) {

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleScreenChange(rootNode, event)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // future: live form detection
            }
        }
    }

    // -----------------------------
    // MAIN SCREEN HANDLER
    // -----------------------------
    private fun handleScreenChange(
        rootNode: AccessibilityNodeInfo,
        event: AccessibilityEvent
    ) {

        Log.d("VMAX_LOG", "Screen Changed: ${event.packageName}")

        serviceScope.launch {

            humanEngine.thinkingPause()

            // Example: Login button detection (safe test logic)
            val loginNode = spatialEngine.findNodeByText(rootNode, "Login")

            if (loginNode != null) {

                Log.d("VMAX_LOG", "Login button detected")

                humanEngine.humanDelay(120, 300)

                // अभी क्लिक disabled (safe mode)
                // humanEngine.performHumanClick(loginNode)
            }
        }
    }

    override fun onInterrupt() {
        Log.e("VMAX_LOG", "Service Interrupted")
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
