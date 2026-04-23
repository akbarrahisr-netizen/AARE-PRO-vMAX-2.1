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

    // Single unified scope (FIXED)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val isInitialized = AtomicBoolean(false)

    // safer null handling instead of lateinit
    private var engine: WorkflowEngine? = null
    private var orchestrator: AutomationOrchestrator? = null
    private var panelManager: FloatingPanelManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VMAX_SERVICE", "🔌 Service Connected")

        serviceScope.launch {

            // Gesture dispatcher bridge
            val gestureDispatcher = object : GestureDispatcher {
                override fun dispatchGesture(
                    gesture: GestureDescription,
                    callback: GestureResultCallback?,
                    handler: Handler?
                ): Boolean {
                    return this@VMaxAccessibilityService.dispatchGesture(
                        gesture,
                        callback,
                        handler
                    )
                }
            }

            // INIT ENGINE
            engine = WorkflowEngine(
                getRoot = { rootInActiveWindow },
                gestureDispatcher = gestureDispatcher,
                engineScope = serviceScope
            )

            // INIT ORCHESTRATOR
            orchestrator = AutomationOrchestrator(
                engine = engine!!,
                scope = serviceScope
            )

            // INIT PANEL (UI safe) - 'context' used instead of 'service' to prevent parameter name mismatch
            panelManager = FloatingPanelManager(
                context = this@VMaxAccessibilityService,
                engine = engine!!,
                orchestrator = orchestrator!!,
                scope = serviceScope
            )

            // Start reactive flow safely
            engine?.startReactiveListening(orchestrator!!.eventFlow)

            isInitialized.set(true)

            withContext(Dispatchers.Main) {
                try {
                    panelManager?.show()
                } catch (e: Exception) {
                    Log.e("VMAX_SERVICE", "UI Error", e)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isInitialized.get()) return

        // IMPORTANT: off main thread processing
        serviceScope.launch {
            engine?.notifyEvent(event)
            orchestrator?.onAccessibilityEvent(event)
        }
    }

    override fun onInterrupt() {
        Log.d("VMAX_SERVICE", "⚠️ Service Interrupted")
    }

    override fun onDestroy() {
        Log.d("VMAX_SERVICE", "🛑 Service Destroyed")

        try {
            panelManager?.remove()
            engine?.shutdown()
            orchestrator?.shutdown()
        } catch (e: Exception) {
            Log.e("VMAX_SERVICE", "Shutdown Error", e)
        }

        serviceScope.cancel()
        super.onDestroy()
    }
}
