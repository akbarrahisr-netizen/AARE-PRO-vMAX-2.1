package com.aare.vmax.core.orchestrator

import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Safe Node Wrapper (Inlined for Coroutine Support)
 */
class NodeHandle(val node: AccessibilityNodeInfo?) {
    @PublishedApi internal var consumed = false
    
    inline fun <T> use(block: (AccessibilityNodeInfo) -> T): T? {
        if (consumed || node == null) return null
        return try {
            block(node)
        } finally {
            safeRecycle()
        }
    }

    @PublishedApi internal fun safeRecycle() {
        if (!consumed) {
            consumed = true
            try { node?.recycle() } catch (_: Exception) {}
        }
    }
    
    companion object {
        fun wrap(node: AccessibilityNodeInfo?): NodeHandle = NodeHandle(node)
    }
}

data class CausalFingerprint(val nodeCount: Int, val textHash: Long, val structureHash: Long, val positionHash: Long) {
    fun hasSignificantChange(other: CausalFingerprint): Boolean =
        abs(nodeCount - other.nodeCount) > 0 || textHash != other.textHash || structureHash != other.structureHash
}

class ExecutionMemory {
    var lastActionId: String? = null
    var lastFingerprint: CausalFingerprint? = null
    fun reset() { lastActionId = null; lastFingerprint = null }
}

sealed class ExecutionDecision {
    object RETRY : ExecutionDecision(); object SCROLL : ExecutionDecision()
    object SUCCESS : ExecutionDecision(); object FAIL : ExecutionDecision()
}

interface GestureDispatcher {
    fun dispatchGesture(gesture: android.accessibilityservice.GestureDescription, callback: android.accessibilityservice.AccessibilityService.GestureResultCallback?, handler: Handler?): Boolean
}

data class EngineConfig(
    val clickDelayMs: Long = 150L,
    val scrollDelayMs: Long = 400L,
    val verificationDelayMs: Long = 300L,
    val allowedPackages: Set<String> = setOf("in.irctc"),
    val maxSignatureNodes: Int = 50
)

class WorkflowEngine(
    private val getRoot: () -> AccessibilityNodeInfo?,
    private val gestureDispatcher: GestureDispatcher,
    private val config: EngineConfig = EngineConfig(),
    private val engineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val steps = mutableListOf<RecordedStep>()
    private var currentIndex = 0
    private val executionMutex = Mutex()
    private val memory = ExecutionMemory()
    private val eventChannel = Channel<AccessibilityEvent>(capacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val causalContext = AtomicReference<CausalContext?>(null)

    data class CausalContext(val actionId: String, val fingerprint: CausalFingerprint)

    fun startReactiveListening(eventFlow: MutableSharedFlow<AccessibilityEvent>) {
        engineScope.launch {
            eventFlow.collect { event ->
                if (isValidUiEvent(event)) eventChannel.trySend(event)
            }
        }
    }

    private fun isValidUiEvent(event: AccessibilityEvent): Boolean = 
        config.allowedPackages.contains(event.packageName?.toString())

    fun notifyEvent(event: AccessibilityEvent) { if (isValidUiEvent(event)) eventChannel.trySend(event) }

    suspend fun loadRecording(list: List<RecordedStep>) = executionMutex.withLock {
        steps.clear(); steps.addAll(list); currentIndex = 0; memory.reset()
    }

    suspend fun reset() = executionMutex.withLock { currentIndex = 0; memory.reset() }

    fun shutdown() { engineScope.cancel(); eventChannel.close() }

    suspend fun onScreenChanged(): Boolean = executionMutex.withLock {
        if (currentIndex >= steps.size) return@withLock true
        val step = steps[currentIndex]
        executeStep(step).also { if (it) currentIndex++ }
    }

    private suspend fun executeStep(step: RecordedStep): Boolean {
        var attempts = 0
        val rootHandle = NodeHandle.wrap(safeRoot())
        val preFp = rootHandle.use { buildFingerprint(it) } ?: return false
        val actionId = "${step.id}_${System.nanoTime()}"
        causalContext.set(CausalContext(actionId, preFp))
        memory.lastFingerprint = preFp

        while (attempts < step.maxRetries) {
            val currentRoot = NodeHandle.wrap(safeRoot())
            val target = currentRoot.use { resolveTargetNode(it, step) }
            val success = when (step.actionType) {
                ActionType.CLICK -> target?.use { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) } ?: false
                ActionType.SCROLL -> currentRoot.use { smartScroll(it, actionId) } ?: false
                ActionType.WAIT -> { delay(step.postActionDelayMs); true }
                else -> false
            }

            val decision = if (success) {
                if (verifyStep(step, memory.lastFingerprint!!)) ExecutionDecision.SUCCESS else ExecutionDecision.RETRY
            } else if (attempts < step.maxRetries / 2) ExecutionDecision.RETRY else ExecutionDecision.SCROLL

            when (decision) {
                ExecutionDecision.SUCCESS -> return true
                ExecutionDecision.FAIL -> return false
                else -> { attempts++; delay(300) }
            }
        }
        return false
    }

    private fun safeRoot(): AccessibilityNodeInfo? = try { getRoot()?.let { AccessibilityNodeInfo.obtain(it) } } catch (e: Exception) { null }

    private fun buildFingerprint(root: AccessibilityNodeInfo): CausalFingerprint {
        var count = 0; var textH = 0L; var structH = 0L
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty() && count < config.maxSignatureNodes) {
            val node = queue.removeFirst(); count++
            textH = textH * 31 + (node.text?.hashCode() ?: 0)
            structH = structH * 31 + (node.className?.hashCode() ?: 0)
            for (i in 0 until node.childCount) { node.getChild(i)?.let { queue.add(it) } }
        }
        return CausalFingerprint(count, textH, structH, 0L)
    }

    private fun resolveTargetNode(root: AccessibilityNodeInfo, step: RecordedStep): NodeHandle? {
        for ((value, type) in step.getAllSelectors()) {
            val node = when (type) {
                SelectorType.RESOURCE_ID -> {
                    val fullId = if (value.contains(":")) value else "${root.packageName}:id/$value"
                    root.findAccessibilityNodeInfosByViewId(fullId).firstOrNull { it.isVisibleToUser }
                }
                SelectorType.TEXT -> root.findAccessibilityNodeInfosByText(value).firstOrNull { it.isVisibleToUser }
                else -> null
            }
            if (node != null) return NodeHandle.wrap(node)
        }
        return null
    }

    private suspend fun smartScroll(root: AccessibilityNodeInfo, actionId: String): Boolean {
        val path = Path().apply { moveTo(500f, 1500f); lineTo(500f, 500f) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 200)).build()
        
        return suspendCancellableCoroutine { cont ->
            val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: android.accessibilityservice.GestureDescription?) { cont.resume(true) {} }
                override fun onCancelled(g: android.accessibilityservice.GestureDescription?) { cont.resume(false) {} }
            }
            if (!gestureDispatcher.dispatchGesture(gesture, callback, null)) cont.resume(false) {}
        }
    }

    private suspend fun verifyStep(step: RecordedStep, oldFp: CausalFingerprint): Boolean {
        delay(config.verificationDelayMs)
        val currentRoot = NodeHandle.wrap(safeRoot())
        return currentRoot.use { root ->
            val newFp = buildFingerprint(root)
            when (val strat = step.verificationStrategy) {
                is VerificationStrategy.ScreenChanged -> oldFp.hasSignificantChange(newFp)
                is VerificationStrategy.NodeExists -> resolveTargetNode(root, RecordedStep("tmp", strat.selector)) != null
                else -> true
            }
        } ?: false
    }
}
