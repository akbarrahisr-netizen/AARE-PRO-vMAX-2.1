package com.aare.vmax.core.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aare.vmax.core.orchestrator.*
import com.aare.vmax.ui.FloatingPanelManager
import kotlinx.coroutines.*

class VMaxAccessibilityService : AccessibilityService(), GestureDispatcher {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + job)
    private val mainScope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var engine: WorkflowEngine
    private lateinit var orchestrator: AutomationOrchestrator
    private lateinit var panelManager: FloatingPanelManager

    private var listeningStarted = false
    private var lastEventTime = 0L
    private val EVENT_THROTTLE = 50L

    override fun onServiceConnected() {
        super.onServiceConnected()

        engine = WorkflowEngine(
            getRoot = { rootInActiveWindow },
            gestureDispatcher = this,
            engineScope = serviceScope
        )

        orchestrator = AutomationOrchestrator(engine, serviceScope)

        panelManager = FloatingPanelManager(
            context = this,
            engine = engine,
            orchestrator = orchestrator,
            scope = serviceScope
        )

        if (!listeningStarted) {
            engine.startReactiveListening(orchestrator.eventFlow)
            listeningStarted = true
        }

        mainScope.launch {
            try {
                panelManager.show()
            } catch (e: Exception) {
                Log.e("VMAX_SERVICE", "Panel show failed", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val now = System.currentTimeMillis()
        if (now - lastEventTime < EVENT_THROTTLE) return
        lastEventTime = now

        try {
            if (::engine.isInitialized) engine.notifyEvent(event)
            if (::orchestrator.isInitialized) orchestrator.onAccessibilityEvent(event)
        } catch (e: Exception) {
            Log.e("VMAX_SERVICE", "Event error", e)
        }
    }

    override fun onDestroy() {
        if (::panelManager.isInitialized) panelManager.remove()
        if (::engine.isInitialized) engine.shutdown()
        if (::orchestrator.isInitialized) orchestrator.shutdown()

        job.cancel()
        super.onDestroy()
    }

    override fun dispatchGesture(
        gesture: GestureDescription,
        callback: GestureResultCallback?,
        handler: Handler?
    ): Boolean {
        return try {
            super.dispatchGesture(gesture, callback, handler)
        } catch (e: Exception) {
            Log.e("VMAX_SERVICE", "Gesture failed", e)
            false
        }
    }

    override fun onInterrupt() {}
}
