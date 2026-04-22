package com.aare.vmax.core.orchestrator

import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.models.ActionType
import com.aare.vmax.core.models.RecordedStep
import com.aare.vmax.core.models.SelectorType
import com.aare.vmax.core.models.VerificationStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

// =========================================================
// 🔥 CORE CONCEPTS (Autonomous & Safe)
// =========================================================

/**
 * Structural Fingerprint for strong causal detection
 */
data class CausalFingerprint(
    val nodeCount: Int,
    val textHash: Long,
    val structureHash: Long,
    val positionHash: Long
) {
    fun hasSignificantChange(other: CausalFingerprint): Boolean {
        val nodeDelta = abs(nodeCount - other.nodeCount)
        val textChanged = textHash != other.textHash
        val structureChanged = structureHash != other.structureHash
        // Position change is less critical for content verification but good for scroll
        return nodeDelta > 0 || textChanged || structureChanged
    }
}

/**
 * Safe Node Wrapper to prevent double-recycle crashes
 */
class NodeHandle(private val node: AccessibilityNodeInfo?) {
    private var consumed = false
    fun <T> use(block: (AccessibilityNodeInfo) -> T): T? {
        if (consumed || node == null) return null
        return try {
            block(node)
        } finally {
            safeRecycle()
        }
    }

    fun safeRecycle() {
        if (!consumed) {
            consumed = true
            try { node?.recycle() } catch (_: Exception) {}
        }
    }
    
    companion object {
        fun wrap(node: AccessibilityNodeInfo?): NodeHandle = NodeHandle(node)
    }
}

/**
 * Execution Memory for state awareness
 */
class ExecutionMemory {
    var lastActionId: String? = null
    var lastFingerprint: CausalFingerprint? = null
    var lastStepId: String? = null
    
    fun reset() {
        lastActionId = null
        lastFingerprint = null
        lastStepId = null
    }
}

/**
 * Autonomous Decision Engine
 */
sealed class ExecutionDecision {
    object RETRY : ExecutionDecision()
    object SCROLL : ExecutionDecision()
    object SUCCESS : ExecutionDecision()
    object FAIL : ExecutionDecision()
}

/**
 * Interface for Gesture Dispatching
 */
interface GestureDispatcher {    fun dispatchGesture(
        gesture: android.accessibilityservice.GestureDescription,
        callback: android.accessibilityservice.AccessibilityService.GestureResultCallback?,
        handler: Handler?
    ): Boolean
}

/**
 * Engine Configuration
 */
data class EngineConfig(
    val rowToleranceDp: Int = 48,
    val clickDelayMs: Long = 150L,
    val scrollDelayMs: Long = 400L,
    val verificationDelayMs: Long = 300L,
    val attemptDelayMs: Long = 300L,
    val rootRetryDelayMs: Long = 100L,
    val stepTimeoutMs: Long = 10000L,
    val defaultMaxRetries: Int = 12,
    val maxParentDepth: Int = 6,
    val verificationAttempts: Int = 3,
    val uiStabilityThresholdMs: Long = 200L,
    val eventWaitTimeoutMs: Long = 3000L,
    val signatureDepthLimit: Int = 3,
    val signatureSampleRate: Int = 4,
    val allowedPackages: Set<String> = setOf("in.irctc", "com.android.chrome", "android.webkit", "irctc.co.in"),
    val minScrollChangeThreshold: Double = 0.1,
    val maxSignatureNodes: Int = 50
)

// =========================================================
// 🔥 MAIN ENGINE CLASS (V26)
// =========================================================

class WorkflowEngine(
    private val getRoot: () -> AccessibilityNodeInfo?,
    private val gestureDispatcher: GestureDispatcher,
    private val config: EngineConfig = EngineConfig(),
    private val engineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val steps = mutableListOf<RecordedStep>()
    private var currentIndex = 0
    private val executionMutex = Mutex()
    
    private val density = android.content.res.Resources.getSystem().displayMetrics.density
    private val ROW_TOLERANCE_PX = (config.rowToleranceDp * density).toInt()    

    // ✅ System Layers
    private val backoffScheduler = AdaptiveBackoffScheduler()
    private val memory = ExecutionMemory()     
    // ✅ Event Channel
    private val eventChannel = Channel<AccessibilityEvent>(capacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var lastValidEventTime = 0L
    
    // ✅ Atomic Causal Context
    private val causalContext = AtomicReference<CausalContext?>(null)

    data class CausalContext(
        val actionId: String,
        val fingerprint: CausalFingerprint,
        val timestamp: Long
    )

    // =========================================================
    // 📥 REACTIVE LISTENER
    // =========================================================
    fun startReactiveListening(eventFlow: MutableSharedFlow<AccessibilityEvent>) {
        engineScope.launch {
            eventFlow.collect { event ->
                if (isValidUiEvent(event)) {
                    lastValidEventTime = System.currentTimeMillis()
                    eventChannel.trySend(event)
                }
            }
        }
    }

    private fun isValidUiEvent(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return false
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrEmpty()) return false
        return config.allowedPackages.contains(pkg)
    }

    fun notifyEvent(event: AccessibilityEvent) {
        if (isValidUiEvent(event)) {
            lastValidEventTime = System.currentTimeMillis()
            eventChannel.trySend(event)
        }
    }

    // =========================================================
    // 📦 CORE OPERATIONS
    // =========================================================
    suspend fun loadRecording(list: List<RecordedStep>) = executionMutex.withLock {
        steps.clear()
        steps.addAll(list)
        currentIndex = 0        lastValidEventTime = 0L
        backoffScheduler.reset()
        memory.reset()
        causalContext.set(null)
        Log.d("VMAX_FLOW", "📦 Loaded ${list.size} steps | Autonomous Agent Mode: ON")
    }

    suspend fun reset() = executionMutex.withLock {
        currentIndex = 0
        lastValidEventTime = 0L
        backoffScheduler.reset()
        memory.reset()
        causalContext.set(null)
        Log.d("VMAX_FLOW", "🔄 Engine reset")
    }

    fun shutdown() {
        engineScope.cancel()
        eventChannel.close()
        Log.d("VMAX_FLOW", "🔌 Engine shutdown")
    }

    suspend fun onScreenChanged(): Boolean {
        return executionMutex.withLock {
            if (currentIndex >= steps.size) {
                Log.d("VMAX_FLOW", "✅ All steps completed")
                return@withLock true
            }
            val step = steps[currentIndex]
            executeStep(step).also { success ->
                if (success) {
                    currentIndex++
                    Log.d("VMAX_FLOW", "✅ Step[${step.id}] done | Next: $currentIndex/${steps.size}")
                }
            }
        }
    }

    // =========================================================
    // 🎯 AUTONOMOUS EXECUTION LOOP
    // =========================================================
    private suspend fun executeStep(step: RecordedStep): Boolean {        
        var attempts = 0
        val maxAttempts = step.maxRetries.coerceAtLeast(config.defaultMaxRetries)

        // Capture pre-action state
        val rootHandle = NodeHandle.wrap(safeRoot())
        val preFingerprint = rootHandle.use { buildFingerprint(it) } ?: return false
        
        val actionId = "${step.id}_${System.nanoTime()}"        causalContext.set(CausalContext(actionId, preFingerprint, System.currentTimeMillis()))
        
        memory.lastActionId = actionId
        memory.lastFingerprint = preFingerprint
        memory.lastStepId = step.id

        while (attempts < maxAttempts && coroutineContext.isActive) {
            try {
                val currentRootHandle = NodeHandle.wrap(safeRoot())
                
                // Resolve target node safely
                val target = currentRootHandle.use { root -> resolveTargetNode(root, step) }
                
                // Execute Action
                val actionSuccess = when (step.actionType) {
                    ActionType.CLICK -> target?.use { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) } ?: false
                    ActionType.SCROLL -> currentRootHandle.use { smartScrollVerified(it, actionId) } ?: false
                    ActionType.INPUT_TEXT -> target?.use { it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, null) } ?: false
                    ActionType.WAIT -> { delay(step.postActionDelayMs); true }
                    else -> false
                }
                
                // Make Autonomous Decision
                val decision = makeDecision(attempts, maxAttempts, actionSuccess, step)                
                
                when (decision) {
                    ExecutionDecision.SUCCESS -> { 
                        backoffScheduler.reset()
                        return true 
                    }
                    ExecutionDecision.RETRY -> { 
                        attempts++
                        delay(backoffScheduler.nextDelay()) 
                    }
                    ExecutionDecision.SCROLL -> {
                        // Attempt scroll as fallback
                        currentRootHandle.use { root ->
                            if (smartScrollVerified(root, actionId)) {
                                attempts++ 
                            } else {
                                attempts = maxAttempts // Force fail if scroll fails
                            }
                        }
                    }
                    ExecutionDecision.FAIL -> return false
                }
                
            } catch (e: Exception) {
                Log.e("VMAX_FLOW", "💥 ${e.message}", e)
                attempts++                delay(backoffScheduler.nextDelay())
            }
        }
        Log.e("VMAX_FLOW", "❌ Step[${step.id}] failed after $attempts attempts")
        return false
    }

    // ✅ Autonomous Decision Logic
    private suspend fun makeDecision(attempt: Int, max: Int, actionSuccess: Boolean, step: RecordedStep): ExecutionDecision {
        if (!actionSuccess) {
            // If action failed (e.g., node not found), decide next move
            return when {
                attempt < max / 2 -> ExecutionDecision.RETRY // Try finding again
                attempt < max -> ExecutionDecision.SCROLL   // Maybe it's below fold
                else -> ExecutionDecision.FAIL
            }
        }        
        // Action succeeded, now verify
        val verified = verifyStepSuccess(step, memory.lastFingerprint!!)
        return if (verified) ExecutionDecision.SUCCESS else ExecutionDecision.RETRY
    }

    // =========================================================
    // 🛡 SAFE ROOT HANDLING
    // =========================================================
    private fun safeRoot(): AccessibilityNodeInfo? {
        val root = getRoot() ?: return null
        return try { 
            AccessibilityNodeInfo.obtain(root) 
        } catch (e: Exception) { 
            null 
        }
    }

    // =========================================================
    // 📸 STRONG CAUSAL FINGERPRINT
    // =========================================================
    private fun buildFingerprint(root: AccessibilityNodeInfo): CausalFingerprint {
        val cursor = TreeCursor(root)
        var count = 0
        var textHash = 0L
        var posHash = 0L
        var structureHash = 0L

        try {
            while (true) {
                val node = cursor.nextVisibleNode() ?: break
                count++

                textHash = textHash * 31 + (node.text?.hashCode() ?: 0)
                val r = Rect()
                node.getBoundsInScreen(r)
                posHash = posHash * 31 + (r.centerX() + r.centerY())

                structureHash = structureHash * 31 + (node.className?.hashCode() ?: 0)
                
                if (count > config.maxSignatureNodes) break
            }
        } finally { 
            cursor.close() 
        }

        return CausalFingerprint(count, textHash, structureHash, posHash)
    }

    // =========================================================
    // 🎯 TARGET RESOLUTION ENGINE
    // =========================================================
    private fun resolveTargetNode(root: AccessibilityNodeInfo, step: RecordedStep): NodeHandle? {
        val selectors = step.getAllSelectors()
        
        for ((value, type) in selectors) {
            val rawNode = when (type) {
                SelectorType.RESOURCE_ID -> findNodeByResourceId(root, value)
                SelectorType.CONTENT_DESC -> findNodeByContentDesc(root, value)
                SelectorType.CLASS_NAME -> findNodeByClassName(root, value)
                SelectorType.TEXT -> findNode(root, value)
            }
            
            if (rawNode != null) {
                return NodeHandle.wrap(rawNode)
            }
        }
        return null
    }

    // =========================================================
    // 📜 VERIFIED SCROLL (Causal ACK Pipeline)
    // =========================================================
    private suspend fun smartScrollVerified(root: AccessibilityNodeInfo, actionId: String): Boolean {
        val scrollable = findScrollable(root) ?: return false
        try {
            val rect = Rect()
            scrollable.getBoundsInScreen(rect)
            val cx = rect.centerX().toFloat()
            val cy = rect.centerY().toFloat()
            val swipeLength = rect.height() * 0.6f

            val path = Path().apply {                moveTo(cx, cy + swipeLength / 2)
                lineTo(cx, cy - swipeLength / 2)
            }

            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 150))
                .build()

            val gestureSuccess = executeGestureWithAck(
                gesture = gesture,
                actionId = actionId,
                verifyChange = {
                    val newRootHandle = NodeHandle.wrap(safeRoot())
                    newRootHandle.use { newRoot ->
                        val newFp = buildFingerprint(newRoot)
                        val oldFp = causalContext.get()?.fingerprint
                        oldFp?.hasSignificantChange(newFp) == true
                    } ?: false
                },
                timeoutMs = 1500L
            )

            if (!gestureSuccess) return false

            waitForCausalEvent(actionId, config.scrollDelayMs)

            val newRootHandle = NodeHandle.wrap(safeRoot())
            return newRootHandle.use { newRoot ->
                val newFp = buildFingerprint(newRoot)
                val oldFp = causalContext.get()?.fingerprint
                oldFp?.hasSignificantChange(newFp) == true
            } ?: false
            
        } finally { 
            scrollable.recycle() 
        }
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isVisibleToUser && root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            try {
                val res = findScrollable(child)
                if (res != null) return res
            } finally {
                child.recycle()
            }
        }
        return null    }

    // =========================================================
    // 🔥 GESTURE ACK PIPELINE
    // =========================================================
    private suspend fun executeGestureWithAck(
        gesture: android.accessibilityservice.GestureDescription,
        actionId: String,
        verifyChange: suspend () -> Boolean,
        timeoutMs: Long = 1500L
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: android.accessibilityservice.GestureDescription?) {
                        if (cont.isActive) {
                            engineScope.launch {
                                val changed = verifyChange()
                                if (cont.isActive) cont.resume(changed) {}
                            }
                        }
                    }
                    override fun onCancelled(g: android.accessibilityservice.GestureDescription?) {
                        if (cont.isActive) cont.resume(false) {}
                    }
                }
                
                val dispatched = gestureDispatcher.dispatchGesture(gesture, callback, null)
                if (!dispatched && cont.isActive) {
                    cont.resume(false) {}
                }
            }
        } ?: false
    }

    // =========================================================
    // ✅ DECOUPLED VERIFICATION ENGINE
    // =========================================================
    private suspend fun verifyStepSuccess(step: RecordedStep, oldFingerprint: CausalFingerprint): Boolean {
        return when (val strategy = step.verificationStrategy) {
            is VerificationStrategy.None -> true
            
            is VerificationStrategy.ScreenChanged -> {
                val rootHandle = NodeHandle.wrap(safeRoot())
                rootHandle.use { root ->
                    val newFp = buildFingerprint(root)
                    oldFingerprint.hasSignificantChange(newFp)
                } ?: false
            }
                        is VerificationStrategy.NodeExists -> {
                val rootHandle = NodeHandle.wrap(safeRoot())
                rootHandle.use { root ->
                    val tempStep = RecordedStep(
                        id = "temp_verify",
                        criteria = strategy.selector,
                        verificationStrategy = VerificationStrategy.None
                    )
                    resolveTargetNode(root, tempStep) != null
                } ?: false
            }
            
            is VerificationStrategy.TextAppears -> {
                val rootHandle = NodeHandle.wrap(safeRoot())
                rootHandle.use { root ->
                    findNode(root, strategy.text) != null
                } ?: false
            }
            
            is VerificationStrategy.ElementDisappears -> {
                val rootHandle = NodeHandle.wrap(safeRoot())
                rootHandle.use { root ->
                    findNode(root, strategy.selector) == null
                } ?: false
            }
        }
    }

    // =========================================================
    // 🔥 CAUSAL EVENT WAITING
    // =========================================================
    private suspend fun waitForCausalEvent(actionId: String, timeoutMs: Long): Boolean {
        val context = causalContext.get() ?: return false
        if (context.actionId != actionId) return false

        return withTimeoutOrNull(timeoutMs) {
            eventChannel.receiveAsFlow().first { event ->
                val rootHandle = NodeHandle.wrap(safeRoot())
                val isCausal = rootHandle.use { root ->
                    val newFp = buildFingerprint(root)
                    isCausalEvent(event, context.fingerprint, newFp)
                } ?: false
                isCausal
            }
            true
        } ?: false
    }
    
    private fun isCausalEvent(event: AccessibilityEvent, before: CausalFingerprint, after: CausalFingerprint): Boolean {
        val nodeDelta = abs(after.nodeCount - before.nodeCount)        val textDelta = after.textHash != before.textHash
        val structureDelta = after.structureHash != before.structureHash

        return event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
               (nodeDelta > 0 || textDelta || structureDelta)
    }

    // =========================================================
    // 🔍 NODE FINDERS (Clear Ownership)
    // =========================================================
    private fun findNodeByContentDesc(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>(16)
        queue.addLast(root)
        try {
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (current.contentDescription == target && current.isVisibleToUser) {
                    queue.forEach { if (it != current) it.recycle() }
                    queue.clear()
                    return AccessibilityNodeInfo.obtain(current)
                }
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { queue.addLast(it) }
                }
                if (current != root) current.recycle()
            }
        } finally {
            queue.forEach { it.recycle() }
            queue.clear()
        }
        return null
    }

    private fun findNodeByResourceId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val fullId = if (id.contains(":")) id else "${root.packageName}:id/$id"
        val nodes = root.findAccessibilityNodeInfosByViewId(fullId)
        val target = nodes?.firstOrNull { it.isVisibleToUser }
        nodes?.filter { it != target }?.forEach { it.recycle() }
        return target
    }

    private fun findNodeByClassName(root: AccessibilityNodeInfo, cls: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByClassName(cls)
        val target = nodes?.firstOrNull { it.isVisibleToUser }
        nodes?.filter { it != target }?.forEach { it.recycle() }
        return target
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)        val target = nodes?.firstOrNull { it.isVisibleToUser && (it.isClickable || it.isClickableOrHasClickableChild()) }
        nodes?.filter { it != target }?.forEach { it.recycle() }
        return target
    }

    private fun AccessibilityNodeInfo.isClickableOrHasClickableChild(): Boolean {
        if (isClickable && isEnabled) return true
        
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(this)
        var depth = 0
        val maxDepth = 5
        
        while (queue.isNotEmpty() && depth < maxDepth) {
            val current = queue.removeFirst()
            try {
                if (current.isClickable && current.isEnabled) return true
                if (depth < maxDepth - 1) {
                    for (i in 0 until current.childCount) {
                        current.getChild(i)?.let { queue.addLast(it) }
                    }
                }
            } finally {
                if (current != this) current.recycle()
            }
            depth++
        }
        return false
    }

    // =========================================================
    // ✅ TREE CURSOR (Iterative)
    // =========================================================
    class TreeCursor(private val root: AccessibilityNodeInfo) {
        private val queue = ArrayDeque<AccessibilityNodeInfo>()
        private var nodesVisited = 0
        private val maxNodes = 100

        init {
            queue.addLast(root)
        }

        fun nextVisibleNode(): AccessibilityNodeInfo? {
            while (queue.isNotEmpty() && nodesVisited < maxNodes) {
                val current = queue.removeFirst()
                nodesVisited++
                
                if (current.isVisibleToUser) {
                    for (i in 0 until current.childCount) {
                        current.getChild(i)?.let { queue.addLast(it) }                    }
                    return current
                } else {
                    current.recycle()
                }
            }
            return null
        }

        fun close() {
            queue.forEach { it.recycle() }
            queue.clear()
        }
    }
}

// =========================================================
// 🔥 ADAPTIVE BACKOFF SCHEDULER
// =========================================================
class AdaptiveBackoffScheduler {
    private var baseDelay = 100L
    private var attemptCount = 0
    
    fun nextDelay(): Long {
        attemptCount++
        val shiftAmount = (attemptCount - 1).coerceAtMost(10)
        val exponential = baseDelay * (1L shl shiftAmount)
        val jitter = (Math.random() * 50).toLong()
        return (exponential + jitter).coerceAtMost(2000L)
    }
    
    fun reset() {
        baseDelay = 100L
        attemptCount = 0
    }
}
