package com.aare.vmax.core.orchestrator

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aare.vmax.core.models.ActionType
import com.aare.vmax.core.models.RecordedStep
import com.aare.vmax.core.models.SelectorType
import com.aare.vmax.core.models.VerificationStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

data class StrikeConfig(
    val trainNumber: String,
    val bookingClass: String
)

/**
 * 🔥 AUTOMATION ORCHESTRATOR V3
 * Unified Event Pipeline + Compatible with V26 Engine
 */
class AutomationOrchestrator(
    private val workflowEngine: WorkflowEngine,
    private val scope: CoroutineScope
) {
    private val eventFlow = MutableSharedFlow<AccessibilityEvent>(
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var executionJob: Job? = null

    fun start(config: StrikeConfig) {
        executionJob?.cancel()

        executionJob = scope.launch {
            try {
                Log.d("ORCHESTRATOR", "🚀 Starting strike for ${config.trainNumber} - ${config.bookingClass}")
                
                val liveSteps = buildSteps(config)
                workflowEngine.loadRecording(liveSteps)                
                workflowEngine.startReactiveListening(eventFlow)
                
                // ✅ V26 Engine Compatible Execution Loop
                while (isActive) {
                    val success = workflowEngine.onScreenChanged()
                    if (success) {
                        // Engine will internally advance to the next step.
                        delay(100) 
                    } else {
                        Log.e("ORCHESTRATOR", "🛑 Workflow stopped or exhausted all retries.")
                        break
                    }
                }
                
            } catch (e: CancellationException) {
                Log.d("ORCHESTRATOR", "⚠️ Strike cancelled")
            } catch (e: Exception) {
                Log.e("ORCHESTRATOR", "💥 Orchestrator error", e)
            }
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        eventFlow.tryEmit(event)
    }

    private fun buildSteps(config: StrikeConfig): List<RecordedStep> {
        return listOf(
            // Step 1: Select Class
            RecordedStep(
                id = "step_select_class",
                actionType = ActionType.CLICK,
                criteria = config.trainNumber, 
                targetClass = config.bookingClass, 
                fallbackCriteria = listOf(config.bookingClass to SelectorType.TEXT), // ✅ Fixed type mismatch
                maxRetries = 15,
                postActionDelayMs = 300L,
                verificationStrategy = VerificationStrategy.ScreenChanged(minHashDiff = 100L),
                isCritical = true
            ),

            // Step 2: Click 'Book Now'
            RecordedStep(
                id = "step_book_now",
                actionType = ActionType.CLICK,
                criteria = "Book Now",
                targetId = "btn_book_now", 
                fallbackCriteria = listOf(
                    "BOOK NOW" to SelectorType.TEXT, 
                    "Book" to SelectorType.TEXT
                ), // ✅ Fixed type mismatch
                maxRetries = 10,
                postActionDelayMs = 500L,
                verificationStrategy = VerificationStrategy.NodeExists(
                    selector = "Passenger Details",
                    selectorType = SelectorType.TEXT
                ),
                isCritical = true
            ),

            // Step 3: Wait for Passenger Screen
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

    fun reset() {
        executionJob?.cancel()
        // ✅ Fixed: reset() is a suspend function in V26
        scope.launch { 
            workflowEngine.reset() 
        }
        Log.d("ORCHESTRATOR", "🔄 Orchestrator reset")
    }

    fun shutdown() {
        executionJob?.cancel()
        workflowEngine.shutdown()
        scope.cancel()
        Log.d("ORCHESTRATOR", "🔌 Orchestrator shutdown")
    }
}
