package com.aare.vmax.core.orchestrator

import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.executor.ActionExecutor
import com.aare.vmax.core.executor.ActionPriority
import com.aare.vmax.core.finder.NodeFinder
import com.aare.vmax.core.models.RecordedStep
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 🚀 Workflow Engine v2 (Stable + Safe)
 */
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
    }

    /**
     * ⚡ Screen update trigger
     */
    suspend fun onScreenChanged(root: AccessibilityNodeInfo?) {
        if (root == null) return

        mutex.withLock {
            if (currentStepIndex >= recording.size) return

            val step = recording[currentStepIndex]

            val success = withTimeoutOrNull(3000L) {
                executeStep(root, step)
            } ?: false

            if (success) {
                currentStepIndex++
            }
        }
    }

    /**
     * 🎯 Step execution logic
     */
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
