package com.aare.vmax.core.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged

import com.aare.vmax.core.engine.*
import com.aare.vmax.data.repository.PassengerRepository
import com.aare.vmax.data.datastore.ConfigStore
import com.aare.vmax.domain.orchestrator.AutomationOrchestrator

class IRCTCAccessibilityService : AccessibilityService() {

    // 1. Engines
    private val spatial = SpatialHeuristicEngine()
    private val human = HumanMimeticEngine()
    private val chrono = ChronoEngine()
    private val safety = SafetyClutchEngine()

    // 2. Data Layer
    private lateinit var passengerRepo: PassengerRepository
    private lateinit var configStore: ConfigStore

    // 3. Brain
    private lateinit var orchestrator: AutomationOrchestrator

    // 4. Scopes
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        // तिजोरी और इंजनों को चालू करो
        passengerRepo = PassengerRepository(applicationContext)
        configStore = ConfigStore(applicationContext)
        orchestrator = AutomationOrchestrator(spatial, human, chrono, safety)

        Log.d("VMAX_LOG", "🚀 System Initialized. Waiting for UI Trigger...")

        // कमांड सेंटर की बातें सुनो (The Bridge)
        job = serviceScope.launch {
            AutomationCommandCenter.isSystemActive
                .distinctUntilChanged()
                .collect { active ->
                    if (!active) {
                        orchestrator.stopSystem()
                    } else {
                        val root = rootInActiveWindow
                        if (root != null) {
                            orchestrator.startBookingFlow(root)
                        }
                    }
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // अगर सिस्टम OFF है, तो चुपचाप रहो
        if (!AutomationCommandCenter.isRunning()) return

        val rootNode: AccessibilityNodeInfo = rootInActiveWindow ?: return
        if (event == null) return

        // कंटीन्यूअस रडार (Continuous Radar)
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                serviceScope.launch {
                    try {
                        orchestrator.startBookingFlow(rootNode)
                    } catch (e: Exception) {
                        Log.e("VMAX_LOG", "Orchestrator error: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        AutomationCommandCenter.stopSystem()
        orchestrator.stopSystem()
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        serviceScope.cancel()
        AutomationCommandCenter.stopSystem()
    }
}
