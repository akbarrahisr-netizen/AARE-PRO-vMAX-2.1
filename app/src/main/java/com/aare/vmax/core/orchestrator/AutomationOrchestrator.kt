package com.aare.vmax.core.orchestrator

// ✅ ज़रूरी Imports (इनके बिना एरर आता है)
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.models.*
import com.aare.vmax.core.executor.ActionExecutor
import com.aare.vmax.core.finder.NodeFinder
import com.aare.vmax.core.event.*
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

        val liveSteps = listOf(
            RecordedStep(
                id = "step_select_class",
                action = ActionType.CLICK,
                criteria = config.bookingClass,
                maxRetries = 2
            ),
            RecordedStep(
                id = "step_book_now",
                action = ActionType.CLICK,
                criteria = "Passenger Details",
                maxRetries = 2
            )
        )

        workflowEngine.loadRecording(liveSteps)

        // ✅ Observer loop
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                val root = getRoot()
                if (root != null) {
                    try {
                        val screen = detectScreenType(root)
                        if (screen == ScreenType.SEARCH_RESULTS) {
                            workflowEngine.onScreenChanged(root)
                        }
                    } catch (e: Exception) {
                        // silent
                    } finally {
                        root.recycle()
                    }
                }
                delay(200L)
            }
        }
    }

    // ✅ यह फंक्शन भी फाइल में होना ज़रूरी है
    private fun detectScreenType(root: AccessibilityNodeInfo): ScreenType {
        val text = root.toString().lowercase()
        return when {
            text.contains("sort by") || text.contains("quota") -> ScreenType.SEARCH_RESULTS
            text.contains("passenger") -> ScreenType.PASSENGER_DETAILS
            else -> ScreenType.UNKNOWN
        }
    }
}
