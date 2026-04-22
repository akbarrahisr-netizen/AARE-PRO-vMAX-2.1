package com.aare.vmax.core.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aare.vmax.core.orchestrator.*
import com.aare.vmax.ui.FloatingPanelManager
import kotlinx.coroutines.*

class VMaxAccessibilityService : AccessibilityService(), GestureDispatcher {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("VMaxService"))
    private lateinit var engine: WorkflowEngine
    private lateinit var orchestrator: AutomationOrchestrator
    private lateinit var panelManager: FloatingPanelManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VMAX_SERVICE", "🔌 Service connected | VMax Beast Online")

        engine = WorkflowEngine(
            getRoot = { rootInActiveWindow },
            gestureDispatcher = this,
            config = EngineConfig(expectedPackageName = "in.irctc"),
            engineScope = serviceScope
        )

        orchestrator = AutomationOrchestrator(
            workflowEngine = engine,
            scope = serviceScope
        )

        // ✅ UI Panel Setup
        panelManager = FloatingPanelManager(
            context = this,
            engine = engine,
            orchestrator = orchestrator,
            scope = serviceScope
        )

        engine.startReactiveListening(orchestrator.eventFlow)
        
        // UI को मेन थ्रेड पर दिखाएं
        CoroutineScope(Dispatchers.Main).launch {
            panelManager.show()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            event?.let {
                engine.notifyEvent(it)
                if (::orchestrator.isInitialized) {
                    orchestrator.onAccessibilityEvent(it)
                }
            }
        } catch (e: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::panelManager.isInitialized) panelManager.remove()
        if (::engine.isInitialized) engine.shutdown()
        if (::orchestrator.isInitialized) orchestrator.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ✅ Android के फाइनल फंक्शन को बायपास किया (Crash Proof)
    override fun executeCustomGesture(
        gesture: GestureDescription,
        callback: AccessibilityService.GestureResultCallback?,
        handler: Handler?
    ): Boolean {
        return dispatchGesture(gesture, callback, handler) 
    }
}
