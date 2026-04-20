package com.aare.vmax.core.orchestrator

import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.event.*
import com.aare.vmax.core.models.*
import com.aare.vmax.core.executor.ActionExecutor
import com.aare.vmax.core.finder.NodeFinder
import kotlinx.coroutines.*

class AutomationOrchestrator(
    private val eventBus: AutomationEventBus,
    private val workflowEngine: WorkflowEngine,
    private val nodeFinder: NodeFinder,
    private val actionExecutor: ActionExecutor,
    private val scope: CoroutineScope
) {

    private var lastHash = 0
    private var lastScreen: ScreenType = ScreenType.UNKNOWN

    fun start(config: StrikeConfig, getRoot: () -> AccessibilityNodeInfo?) {

        // 🚀 असली हलचल: आपके सेव किए हुए 'Profile' को काम पर लगाना
        val liveSteps = listOf(
            RecordedStep(id = "step_1", action = ActionType.CLICK, criteria = "OK"),
            RecordedStep(id = "step_2", action = ActionType.CLICK, criteria = "LOGIN"),
            RecordedStep(id = "step_3", action = ActionType.CLICK, criteria = "Plan My Journey"), // या "Train"
            // यहाँ आपका सेव किया हुआ ट्रेन नंबर (12487) खुद टाइप/सर्च होगा
            RecordedStep(id = "step_4", action = ActionType.CLICK, criteria = config.trainNumber) 
        )
        
        // बॉट के दिमाग में आपकी प्रोफाइल लोड कर दी
        workflowEngine.loadRecording(liveSteps)

        // 1. Event Listener
        eventBus.subscribe { event ->
            when (event) {
                is AutomationEvent.ScreenChanged -> {
                    scope.launch {
                        val root = getRoot()
                        if (root != null) {
                            workflowEngine.onScreenChanged(root)
                        }
                    }
                }
                else -> {}
            }
        }

        // 2. Observer loop
        scope.launch(Dispatchers.Default) {
            observe(getRoot)
        }
    }

    // =========================================================
    // 👁 SAFE OBSERVER LOOP
    // =========================================================
    private suspend fun observe(getRoot: () -> AccessibilityNodeInfo?) {
        while (scope.isActive) {
            val root = getRoot()
            if (root == null) {
                delay(100)
                continue
            }

            try {
                val hash = root.hashCode()
                if (hash != lastHash) {
                    lastHash = hash
                    val screen = detectScreenType(root)
                    if (screen != lastScreen) {
                        lastScreen = screen
                        eventBus.publish(AutomationEvent.ScreenChanged(screen))
                    }
                }
            } catch (e: Exception) {
                // silent catch
            } finally {
                root.recycle()
            }
            delay(80L)
        }
    }

    // =========================================================
    // 🧠 SCREEN DETECTION
    // =========================================================
    private fun detectScreenType(root: AccessibilityNodeInfo): ScreenType {
        val text = buildString {
            root.text?.let { append(it) }
            root.contentDescription?.let { append(it) }
        }.lowercase()

        return when {
            text.contains("search") || text.contains("sort by") -> ScreenType.SEARCH_RESULTS
            text.contains("passenger") -> ScreenType.PASSENGER_DETAILS
            text.contains("payment") -> ScreenType.PAYMENT
            text.contains("captcha") -> ScreenType.CAPTCHA
            text.contains("confirm") -> ScreenType.CONFIRMATION
            text.contains("home") -> ScreenType.HOME
            else -> ScreenType.UNKNOWN
        }
    }
}
