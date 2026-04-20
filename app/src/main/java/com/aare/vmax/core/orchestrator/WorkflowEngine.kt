package com.aare.vmax.core.orchestrator

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.executor.ActionExecutor
import com.aare.vmax.core.executor.ActionPriority
import com.aare.vmax.core.finder.NodeFinder
import com.aare.vmax.core.models.RecordedStep
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class WorkflowEngine(
    private val finder: NodeFinder,
    private val executor: ActionExecutor
) {

    private val recording = mutableListOf<RecordedStep>()
    private var currentStepIndex = 0
    private val mutex = Mutex()

    fun loadRecording(steps: List<RecordedStep>) {
        recording.clear()
        recording.addAll(steps)
        currentStepIndex = 0
        Log.d("VMAX_FLOW", "📦 Recording loaded: ${steps.size} steps")
    }

    // =========================================================
    // 🎯 MAIN EXECUTION ENTRY
    // =========================================================
    suspend fun onScreenChanged(root: AccessibilityNodeInfo?) {
        if (root == null) return

        val step: RecordedStep?

        // 🔒 lock only state access (NOT execution)
        mutex.withLock {
            if (currentStepIndex >= recording.size) return
            step = recording[currentStepIndex]
        }

        if (step == null) return

        Log.d("VMAX_FLOW", "🎯 Executing Step: ${step.id}")

        val success = withTimeoutOrNull(3000L) {
            executeStep(root, step)
        } ?: false

        if (success) {
            mutex.withLock {
                currentStepIndex++
            }
            Log.d("VMAX_FLOW", "✅ Step Completed: ${step.id}")
        } else {
            Log.e("VMAX_FLOW", "❌ Step Failed: ${step.id}")
        }
    }

    // =========================================================
    // ⚙️ STEP EXECUTION LOGIC
    // =========================================================
    private suspend fun executeStep(
        root: AccessibilityNodeInfo,
        step: RecordedStep
    ): Boolean {

        var attempts = 0
        val maxAttempts = step.maxRetries.coerceAtLeast(1)

        while (attempts < maxAttempts) {

            val node = try {
                finder.findBySmartMatch(root, step.criteria)
            } catch (e: Exception) {
                Log.e("VMAX_FLOW", "Finder error: ${e.message}")
                null
            }

            if (node != null) {
                return try {
                    executor.click(node, ActionPriority.CRITICAL)
                } finally {
                    node.recycle()
                }
            }

            attempts++
        }

        return false
    }
}
