package com.aare.vmax.core.orchestrator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow

class WorkflowEngine(
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val gestureDispatcher: GestureDispatcher,
    private val config: EngineConfig = EngineConfig(),
    private val scope: CoroutineScope
) {
    private var currentRecording: List<RecordedStep> = emptyList()
    private var currentStepIndex = 0
    private val stepRetryCount = mutableMapOf<Int, Int>()
    private var listeningJob: Job? = null
    private var currentStepJob: Job? = null
    private var callback: EngineCallback? = null
    
    companion object {
        private const val TAG = "VMAX_Engine"
        private const val DEFAULT_MAX_RETRIES = 3
    }
    
    fun loadRecording(steps: List<RecordedStep>) {
        currentRecording = steps
        currentStepIndex = 0
        stepRetryCount.clear()
        Log.d(TAG, "✅ Loaded ${steps.size} steps")
        callback?.onWorkflowStarted()
    }
    
    fun setCallback(callback: EngineCallback) {
        this.callback = callback
    }
    
    fun onScreenChanged() {
        if (currentStepIndex >= currentRecording.size) {
            if (currentRecording.isNotEmpty()) {
                Log.d(TAG, "🏁 Workflow completed!")
                callback?.onWorkflowCompleted()
            }
            return
        }
        
        currentStepJob?.cancel()
        currentStepJob = scope.launch {
            withTimeoutOrNull(config.stepTimeoutMs) {
                processCurrentStep()
            } ?: handleTimeout()
        }
    }
    
    private suspend fun processCurrentStep() {
        val step = currentRecording[currentStepIndex]
        callback?.onStepStarted(step, currentStepIndex)
        
        val root = rootProvider()
        if (root == null) {
            delay(100)
            return
        }
        
        // 🎯 Anchor (ट्रेन नंबर) और Criteria के साथ बटन ढूँढना
        val node = findNode(root, step.criteria, step.anchorText)
        
        if (node != null) {
            executeAction(node, step)
            stepRetryCount.remove(currentStepIndex)
            callback?.onStepCompleted(step, currentStepIndex - 1)
        } else {
            handleNodeNotFound(step)
        }
    }
    
    private fun findNode(root: AccessibilityNodeInfo, text: String, anchor: String = ""): AccessibilityNodeInfo? {
        // अगर एंकर (ट्रेन नंबर) है, तो पहले उसे ढूँढो फिर उसके करीब वाले बटन को
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) return null
        
        // IRCTC के लिए: सिर्फ वो बटन चुनें जो स्क्रीन पर दिख रहा हो
        var selected = nodes.firstOrNull { it.isVisibleToUser && (it.isClickable || it.isEnabled) }
        if (selected == null) selected = nodes.firstOrNull { it.isVisibleToUser }
        
        nodes.forEach { if (it != selected) it.recycle() }
        return selected
    }
    
    private suspend fun handleNodeNotFound(step: RecordedStep) {
        val retries = stepRetryCount.getOrDefault(currentStepIndex, 0)
        val maxRetries = config.defaultMaxRetries.coerceAtLeast(DEFAULT_MAX_RETRIES)
        
        if (retries < maxRetries) {
            stepRetryCount[currentStepIndex] = retries + 1
            Log.w(TAG, "⏳ Retry ${retries + 1}/$maxRetries for: ${step.criteria}")
            delay(400L * (retries + 1)) 
        } else {
            Log.e(TAG, "❌ Failed: ${step.criteria}")
            callback?.onStepFailed(step, currentStepIndex, "Node not found")
            stepRetryCount.remove(currentStepIndex)
            currentStepIndex++ 
        }
    }
    
    private suspend fun handleTimeout() {
        if (currentStepIndex < currentRecording.size) {
            Log.e(TAG, "⏰ Step $currentStepIndex timeout!")
            callback?.onStepFailed(currentRecording[currentStepIndex], currentStepIndex, "Timeout")
            currentStepIndex++
        }
    }
    
    private fun executeAction(node: AccessibilityNodeInfo, step: RecordedStep) {
        try {
            val success = when (step.actionType) {
                ActionType.CLICK -> {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                ActionType.INPUT_TEXT -> {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.inputText)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
                else -> {
                    Log.d(TAG, "⚠️ Action skipped: ${step.actionType}")
                    true
                }
            }
            
            if (success) {
                currentStepIndex++
                Log.d(TAG, "✅ Step Done: ${step.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Execution failed", e)
        } finally {
            try { node.recycle() } catch (e: Exception) {}
        }
    }
    
    fun startReactiveListening(eventFlow: SharedFlow<AccessibilityEvent>) {
        listeningJob?.cancel()
        listeningJob = scope.launch {
            eventFlow.collect { onScreenChanged() }
        }
    }
    
    fun notifyEvent(event: AccessibilityEvent) {
        onScreenChanged()
    }
    
    fun reset() {
        currentStepIndex = 0
        stepRetryCount.clear()
        currentStepJob?.cancel()
    }
    
    fun shutdown() {
        listeningJob?.cancel()
        currentStepJob?.cancel()
        reset()
    }
    
    fun isReady(): Boolean = rootProvider() != null
}

// 🛡️ Error रोकने के लिए Interface
interface GestureDispatcher {
    fun dispatchGesture(gesture: GestureDescription, callback: AccessibilityService.GestureResultCallback?, handler: Handler?): Boolean
}

data class EngineConfig(
    val stepTimeoutMs: Long = 10000,
    val defaultMaxRetries: Int = 12
)

interface EngineCallback {
    fun onWorkflowStarted()
    fun onStepStarted(step: RecordedStep, index: Int)
    fun onStepCompleted(step: RecordedStep, index: Int)
    fun onStepFailed(step: RecordedStep, index: Int, error: String)
    fun onWorkflowCompleted()
}
