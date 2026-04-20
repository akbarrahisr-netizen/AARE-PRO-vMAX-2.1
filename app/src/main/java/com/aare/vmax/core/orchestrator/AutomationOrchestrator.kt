package com.aare.vmax.core.orchestrator

// 🛠️ इन इम्पोर्ट्स के बिना "Unresolved Reference" वाले 50 एरर आएंगे
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.aare.vmax.core.event.*
import com.aare.vmax.core.models.*
import com.aare.vmax.core.finder.NodeFinder
import com.aare.vmax.core.executor.ActionExecutor
import com.aare.vmax.core.utils.detectScreenType // यह हेल्पर फंक्शन ज़रूरी है
import kotlinx.coroutines.*

/**
 * ✅ AutomationOrchestrator: यह बॉट का 'कप्तान' है।
 * इसका काम स्क्रीन को देखना और सही समय पर इवेंट्स फायर करना है।
 */
class AutomationOrchestrator(
    private val eventBus: AutomationEventBus,
    private val workflowEngine: WorkflowEngine,
    private val nodeFinder: NodeFinder,
    private val actionExecutor: ActionExecutor,
    private val scope: CoroutineScope
) {

    private var lastHash = 0

    fun start(config: StrikeConfig, getRoot: () -> AccessibilityNodeInfo?) {

        // 🎯 इवेंट हैंडलर्स: जब कुछ होगा, तो यहाँ से कमांड जाएगी
        eventBus.subscribe { event ->
            when (event) {
                is AutomationEvent.ScreenChanged -> {
                    workflowEngine.onScreenChange(event.screenType)
                }

                is AutomationEvent.NodeFound -> {
                    // actionExecutor.execute(event.nodeId) // अगर आपका शूटर तैयार है
                }

                is AutomationEvent.ErrorOccurred -> {
                    // if (event.recoverable) workflowEngine.retry()
                    // else eventBus.publish(AutomationEvent.UserInterventionRequired)
                }

                else -> {}
            }
        }

        // 📡 स्क्रीन पर नज़र रखने वाला 'जासूस' (Observer)
        scope.launch(Dispatchers.Default) {
            observe(getRoot)
        }
    }

    // =========================================================
    // ✅ SAFE OBSERVER (मेमोरी लीक और क्रैश से बचाव)
    // =========================================================
    private suspend fun observe(getRoot: () -> AccessibilityNodeInfo?) {

        while (isActive) {
            val root = getRoot() ?: run {
                delay(100)
                return@run null
            } ?: continue

            try {
                // 🛠️ स्ट्रक्चरल हैश: यह चेक करता है कि स्क्रीन बदली या नहीं
                val hash = root.hashCode() 

                if (hash != lastHash) {
                    lastHash = hash

                    val screen = detectScreenType(root)
                    
                    eventBus.publish(
                        AutomationEvent.ScreenChanged(screen)
                    )
                    
                    Log.d("VMAX_ORCH", "🎯 Screen Detected: $screen")
                }

            } catch (e: Exception) {
                Log.e("VMAX_ORCH", "❌ Observation Error: ${e.message}")
            } finally {
                // ♻️ ज़रूरी: नोड को रीसायकल करना ताकि फोन लैग न करे
                root.recycle()
            }

            delay(80L) // 80ms का गैप (Performance के लिए बेस्ट)
        }
    }
}

