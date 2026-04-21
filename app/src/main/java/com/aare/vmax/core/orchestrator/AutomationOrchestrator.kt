package com.aare.vmax.core.orchestrator

import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.event.AutomationEventBus
import com.aare.vmax.core.models.*
import com.aare.vmax.core.executor.ActionExecutor
import com.aare.vmax.core.finder.NodeFinder
// import com.aare.vmax.core.engine.WorkflowEngine // इसे हटा दिया है क्योंकि यह Orchestrator के ही फोल्डर में है

import kotlinx.coroutines.*

class AutomationOrchestrator(
    private val eventBus: AutomationEventBus,
    private val workflowEngine: WorkflowEngine,
    private val nodeFinder: NodeFinder,
    private val actionExecutor: ActionExecutor,
    private val scope: CoroutineScope
) {

    private var lastScreen: ScreenType = ScreenType.UNKNOWN

    fun start(config: StrikeConfig, getRoot: () -> AccessibilityNodeInfo?) {

        val liveSteps = listOf(
            RecordedStep(
                id = "step_select_class",
                action = ActionType.CLICK,
                criteria = config.bookingClass,
                maxRetries = 3 // 🔥 Excellent: Reliability बढ़ेगी
            ),
            RecordedStep(
                id = "step_book_now",
                action = ActionType.CLICK,
                criteria = "Passenger",
                maxRetries = 3
            )
        )

        workflowEngine.loadRecording(liveSteps)

        scope.launch(Dispatchers.Default) {
            while (isActive) {

                val root = getRoot()
                if (root == null) {
                    delay(200)
                    continue
                }

                try {
                    val screen = detectScreenType(root)

                    // स्क्रीन state अपडेट
                    if (screen != lastScreen) {
                        lastScreen = screen
                    }

                    // 🔥 HUNTER MODE
                    if (screen == ScreenType.SEARCH_RESULTS) {
                        
                        // सीधा इंजन को फायर करो
                        workflowEngine.onScreenChanged(root)
                        
                        // ✅ Smart Delay: क्लिक के बाद स्क्रीन को सॉर्ट होने का टाइम मिलेगा
                        delay(300) 
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    root.recycle()
                }

                delay(200) // 🎯 scan frequency
            }
        }
    }

    private fun detectScreenType(root: AccessibilityNodeInfo): ScreenType {

        val text = buildString {
            root.text?.let { append(it) }
            root.contentDescription?.let { append(it) }
        }.lowercase()

        return when {
            text.contains("search") || text.contains("sort by") || text.contains("quota") ->
                ScreenType.SEARCH_RESULTS

            text.contains("passenger") ->
                ScreenType.PASSENGER_DETAILS

            text.contains("payment") ->
                ScreenType.PAYMENT

            else -> ScreenType.UNKNOWN
        }
    }
}

