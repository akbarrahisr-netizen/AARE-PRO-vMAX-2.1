package com.aare.vmax.core.orchestrator

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
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
    private var recording: List<RecordedStep> = emptyList()
    private var currentStepIndex = 0
    private val mutex = Mutex()

    fun loadRecording(steps: List<RecordedStep>) {
        recording = steps
        currentStepIndex = 0
        Log.d("VMAX_FLOW", "📦 Recording loaded: ${steps.size} steps")
    }

    suspend fun onScreenChanged(root: AccessibilityNodeInfo?) {
        if (root == null) return

        mutex.withLock {
            if (currentStepIndex >= recording.size) return

            val step = recording[currentStepIndex]
            Log.d("VMAX_FLOW", "🎯 Attempting Step: ${step.id} (Index: $currentStepIndex)")

            val success = withTimeoutOrNull(3000L) {
                executeStep(root, step)
            } ?: false

            if (success) {
                Log.d("VMAX_FLOW", "✅ Step Success: ${step.id}")
                currentStepIndex++
            }
        }
    }

    private suspend fun executeStep(root: AccessibilityNodeInfo, step: RecordedStep): Boolean {
        var attempts = 0
        val maxAttempts = step.maxRetries.coerceAtLeast(1)

        while (attempts < maxAttempts) {
            val node = try {
                finder.findBySmartMatch(root, step.criteria)
            } catch (e: Exception) { null }

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

