package com.aare.vmax.core.orchestrator

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aare.vmax.core.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 🔥 AUTOMATION ORCHESTRATOR V3 (CLEAN COPY-PASTE VERSION)
 */
data class StrikeConfig(
    val trainNumber: String,
    val bookingClass: String
)

class AutomationOrchestrator(
    private val workflowEngine: WorkflowEngine,
    private val scope: CoroutineScope
) {

    val eventFlow = MutableSharedFlow<AccessibilityEvent>(
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var job: Job? = null

    /**
     * 🚀 START AUTOMATION
     */
    fun start(config: StrikeConfig) {

        job?.cancel()

        job = scope.launch {

            try {
                Log.d("ORCHESTRATOR", "🚀 START ${config.trainNumber} - ${config.bookingClass}")

                val steps = buildSteps(config)

                workflowEngine.loadRecording(steps)
                workflowEngine.startReactiveListening(eventFlow)

                // 🔁 Execution loop (engine-driven)
                while (isActive) {

                    val success = workflowEngine.onScreenChanged()

                    if (success) {
                        delay(100)
                    } else {
                        Log.e("ORCHESTRATOR", "🛑 STOPPED / FAILED")
                        break
                    }
                }

            } catch (e: CancellationException) {
                Log.d("ORCHESTRATOR", "⚠️ CANCELLED")

            } catch (e: Exception) {
                Log.e("ORCHESTRATOR", "💥 ERROR", e)
            }
        }
    }

    /**
     * 📡 RECEIVE ACCESSIBILITY EVENTS
     */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        eventFlow.tryEmit(event)
    }

    /**
     * 🧠 BUILD EXECUTION STEPS
     */
    private fun buildSteps(config: StrikeConfig): List<RecordedStep> {
        return listOf(

            RecordedStep(
                id = "step_select_class",
                actionType = ActionType.CLICK,
                criteria = config.trainNumber,
                targetClass = config.bookingClass,
                fallbackCriteria = listOf(config.bookingClass to SelectorType.TEXT), // ✅ FIXED
                maxRetries = 15,
                postActionDelayMs = 300L,
                verificationStrategy = VerificationStrategy.ScreenChanged(100L),
                isCritical = true
            ),

            RecordedStep(
                id = "step_book_now",
                actionType = ActionType.CLICK,
                criteria = "Book Now",
                targetId = "btn_book_now",
                fallbackCriteria = listOf("BOOK NOW" to SelectorType.TEXT, "Book" to SelectorType.TEXT), // ✅ FIXED
                maxRetries = 10,
                postActionDelayMs = 500L,
                verificationStrategy = VerificationStrategy.NodeExists(
                    selector = "Passenger Details",
                    selectorType = SelectorType.TEXT
                ),
                isCritical = true
            ),

            RecordedStep(
                id = "step_wait_passenger",
                actionType = ActionType.WAIT,
                criteria = "",
                postActionDelayMs = 1000L,
                verificationStrategy = VerificationStrategy.NodeExists(
                    selector = "Passenger Name",
                    selectorType = SelectorType.TEXT
                )
            )
        )
    }

    /**
     * 🔄 RESET ENGINE
     */
    fun reset() {
        job?.cancel()
        job = null

        scope.launch {
            workflowEngine.reset()
        }

        Log.d("ORCHESTRATOR", "🔄 RESET DONE")
    }

    /**
     * 🔌 SHUTDOWN
     */
    fun shutdown() {
        job?.cancel()
        workflowEngine.shutdown()
        scope.cancel()

        Log.d("ORCHESTRATOR", "🔌 SHUTDOWN")
    }
}
