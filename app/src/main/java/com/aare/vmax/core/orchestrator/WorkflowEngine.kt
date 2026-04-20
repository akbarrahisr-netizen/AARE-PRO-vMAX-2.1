package com.aare.vmax.core.orchestrator // ✅ FIX 1: छोटा 'package'

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.executor.ActionExecutor
import com.aare.vmax.core.models.ActionPriority // ✅ FIX 2: सही Import
import com.aare.vmax.core.finder.NodeFinder
import com.aare.vmax.core.models.RecordedStep
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay

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
    // 🎯 MAIN EXECUTION
    // =========================================================
    suspend fun onScreenChanged(root: AccessibilityNodeInfo?) {
        if (root == null) return

        val step: RecordedStep?

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
    // ⚙️ STEP EXECUTION (SAFE NATIVE SEARCH)
    // =========================================================
    private suspend fun executeStep(
        root: AccessibilityNodeInfo,
        step: RecordedStep
    ): Boolean {

        var attempts = 0
        val maxAttempts = step.maxRetries.coerceAtLeast(1)

        while (attempts < maxAttempts) {

            // ✅ FIX 3: Native Search (इससे Type Mismatch नहीं होगा)
            val node = try {
                val texts = root.findAccessibilityNodeInfosByText(step.criteria)
                if (!texts.isNullOrEmpty()) {
                    texts[0]
                } else {
                    val ids = root.findAccessibilityNodeInfosByViewId(step.criteria)
                    if (!ids.isNullOrEmpty()) ids[0] else null
                }
            } catch (e: Exception) {
                Log.e("VMAX_FLOW", "Search error: ${e.message}")
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
            delay(100)
        }

        return false
    }
}
