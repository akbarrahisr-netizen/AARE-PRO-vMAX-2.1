package com.aare.vmax.core.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aare.vmax.core.orchestrator.*
import com.aare.vmax.ui.FloatingPanelManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

class VMaxAccessibilityService : AccessibilityService(), GestureDispatcher {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("VMaxService"))
    
    private lateinit var engine: WorkflowEngine
    private lateinit var orchestrator: AutomationOrchestrator
    private lateinit var panelManager: FloatingPanelManager
    private val eventFlow = MutableSharedFlow<AccessibilityEvent>(extraBufferCapacity = 20)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VMAX_SERVICE", "🔌 Service connected")

        engine = WorkflowEngine(
            getRoot = { rootInActiveWindow },
            gestureDispatcher = this,
            config = EngineConfig(expectedPackageName = "in.irctc", minScrollChangeThreshold = 50L),
            engineScope = serviceScope
        )

        orchestrator = AutomationOrchestrator(
            eventBus = AutomationEventBus(),
            workflowEngine = engine,
            nodeFinder = NodeFinder(),
            actionExecutor = ActionExecutor(this),
            scope = serviceScope
        )

        panelManager = FloatingPanelManager(
            context = this,
            engine = engine,
            orchestrator = orchestrator,
            uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        )

        if (Settings.canDrawOverlays(this)) {
            panelManager.show()
        }

        engine.startReactiveListening(eventFlow)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            event?.let {
                engine.notifyEvent(it)
                eventFlow.tryEmit(it)
                if (::orchestrator.isInitialized) {
                    orchestrator.onAccessibilityEvent(it) // 🔥 Fix #1
                }
            }
        } catch (e: Exception) {
            Log.e("VMAX_SERVICE", "💥 Event error: ${e.message}", e)
        }
    }

    override fun onInterrupt() {
        Log.d("VMAX_SERVICE", "⚠️ Service interrupted")
    }

    override fun onDestroy() {
        Log.d("VMAX_SERVICE", "🔌 Service destroying")
        
        if (::panelManager.isInitialized) panelManager.remove()
        if (::engine.isInitialized) engine.shutdown()
        if (::orchestrator.isInitialized) orchestrator.shutdown() // 🔥 Fix #2
        
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun dispatchGesture(
        gesture: GestureDescription,
        callback: AccessibilityService.GestureResultCallback?,
        handler: Handler?
    ): Boolean {
        return super.dispatchGesture(gesture, callback, handler) // ✅ Crash fix
    }
}
