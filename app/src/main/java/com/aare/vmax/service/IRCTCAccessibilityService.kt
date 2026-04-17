package com.aare.vmax.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.aare.vmax.core.engine.*
import com.aare.vmax.data.repository.PassengerRepository
import com.aare.vmax.data.datastore.ConfigStore
import com.aare.vmax.domain.orchestrator.AutomationOrchestrator
import kotlinx.coroutines.*

class IRCTCAccessibilityService : AccessibilityService() {

    private val spatial = SpatialHeuristicEngine()
    private val human = HumanMimeticEngine()
    private val chrono = ChronoEngine()
    private val safety = SafetyClutchEngine()

    // Data layer
    private lateinit var passengerRepo: PassengerRepository
    private lateinit var configStore: ConfigStore

    // Brain
    private lateinit var orchestrator: AutomationOrchestrator

    // Scope
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()

        // init data layer
        passengerRepo = PassengerRepository(applicationContext)
        configStore = ConfigStore(applicationContext)

        // IMPORTANT: match your existing orchestrator signature
        orchestrator = AutomationOrchestrator(
            spatial,
            human,
            chrono,
            safety
        )

        Log.d("VMAX_LOG", "🚀 Full System Integrated: Ready to fire!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        val rootNode: AccessibilityNodeInfo = rootInActiveWindow ?: return

        if (event == null) return

        when (event.eventType) {

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {

                serviceScope.launch {
                    handleScreenChange(rootNode, event)
                }
            }
        }
    }

    // -----------------------------
    // SCREEN HANDLER (NEW CLEAN FLOW)
    // -----------------------------
    private suspend fun handleScreenChange(
        rootNode: AccessibilityNodeInfo,
        event: AccessibilityEvent
    ) {
        try {
            orchestrator.startBookingFlow(rootNode)
        } catch (e: Exception) {
            Log.e("VMAX_LOG", "Orchestrator error: ${e.message}")
        }
    }

    override fun onInterrupt() {
        serviceScope.cancel()
        orchestrator.stopSystem()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
