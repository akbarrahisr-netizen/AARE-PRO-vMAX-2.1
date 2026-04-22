package com.aare.vmax.core.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aare.vmax.core.orchestrator.*
import com.aare.vmax.ui.FloatingPanelManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class VMaxAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val mainScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var engine: WorkflowEngine
    private lateinit var orchestrator: AutomationOrchestrator
    private lateinit var panelManager: FloatingPanelManager
    private val isListening = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VMAX_SERVICE", "🔌 Connected")

        engine = WorkflowEngine(
            getRoot = { rootInActiveWindow },
            gestureDispatcher = object : GestureDispatcher {
                override fun dispatchGesture(g: GestureDescription, c: GestureResultCallback?, h: Handler?): Boolean {
                    return this@VMaxAccessibilityService.dispatchGesture(g, c, h)
                }
            },
            engineScope = serviceScope
        )

        orchestrator = AutomationOrchestrator(engine, serviceScope)
        panelManager = FloatingPanelManager(this, engine, orchestrator, serviceScope)

        if (isListening.compareAndSet(false, true)) {
            engine.startReactiveListening(orchestrator.eventFlow)
        }

        mainScope.launch { try { panelManager.show() } catch(e: Exception) {} }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (::engine.isInitialized) engine.notifyEvent(it)
            if (::orchestrator.isInitialized) orchestrator.onAccessibilityEvent(it)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
