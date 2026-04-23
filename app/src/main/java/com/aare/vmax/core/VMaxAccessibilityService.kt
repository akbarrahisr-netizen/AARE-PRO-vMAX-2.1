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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isInitialized = AtomicBoolean(false)

    private var engine: WorkflowEngine? = null
    private var orchestrator: AutomationOrchestrator? = null
    private var panelManager: FloatingPanelManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VMAX_SERVICE", "Service Connected")

        serviceScope.launch {
            val gestureDispatcher = object : GestureDispatcher {
                override fun dispatchGesture(
                    gesture: GestureDescription,
                    callback: GestureResultCallback?,
                    handler: Handler?
                ): Boolean {
                    return this@VMaxAccessibilityService.dispatchGesture(gesture, callback, handler)
                }
            }

            // बिना नाम (named parameters) के सीधे वैल्यू भेज रहे हैं
            engine = WorkflowEngine(
                { rootInActiveWindow },
                gestureDispatcher,
                EngineConfig(),
                serviceScope
            )

            orchestrator = AutomationOrchestrator(engine!!, serviceScope)

            panelManager = FloatingPanelManager(this@VMaxAccessibilityService, engine!!, orchestrator!!, serviceScope)

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
        serviceScope.launch {
            engine?.notifyEvent(event)
            orchestrator?.onAccessibilityEvent(event)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
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
