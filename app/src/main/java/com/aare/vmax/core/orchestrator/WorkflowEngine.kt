package com.aare.vmax.core.orchestrator

// 🛠️ इन इम्पोर्ट्स के बिना "Unresolved Reference" एरर आता रहेगा
import com.aare.vmax.core.logging.Logger
import com.aare.vmax.core.models.StepResult
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ✅ WorkflowEngine: यह बॉट का सुरक्षा गार्ड है।
 * इसका काम है ये देखना कि बॉट एक ही जगह गोल-गोल न घूमे (Loop Detection)।
 */
class WorkflowEngine(
    private val logger: Logger
) {

    private val stateLog = CopyOnWriteArrayList<StateTransition>()

    data class StateTransition(
        val from: Int?,
        val to: Int,
        val result: StepResult,
        val timestamp: Long
    )

    // =========================================================
    // ✅ SAFE LOOP DETECTION (बॉट को अटकने से बचाना)
    // =========================================================
    fun detectLoop(stepId: Int): Boolean {

        val recent = stateLog.takeLast(10).map { it.to }
        val frequency = recent.count { it == stepId }

        if (frequency >= 3) {
            logger.error(
                tag = "Workflow",
                message = "Loop detected at state $stepId",
                metadata = mapOf("recentStates" to recent)
            )
            return true
        }

        return false
    }

    // =========================================================
    // ✅ SAFE STATE EXECUTION TRACKING (हर कदम का हिसाब)
    // =========================================================
    fun recordTransition(
        from: Int?,
        to: Int,
        result: StepResult
    ) {
        stateLog.add(
            StateTransition(
                from = from,
                to = to,
                result = result,
                timestamp = System.currentTimeMillis()
            )
        )

        cleanup()
    }

    // =========================================================
    // ✅ MEMORY SAFE CLEANUP (रैम बचाने के लिए)
    // =========================================================
    private fun cleanup(maxSize: Int = 100) {
        if (stateLog.size > maxSize) {
            val removeCount = stateLog.size - maxSize
            repeat(removeCount) {
                stateLog.removeAt(0)
            }
        }
    }
}

