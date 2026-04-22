package com.aare.vmax.core.orchestrator

import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.models.RecordedStep
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
// 🔥 CORE CONCEPTS (Causal State Machine)
// =========================================================

/**
 * 1️⃣ Causal Context (✅ FIX #1 & #2)
 * Replaces global action tracking with atomic, per-action context.
 */
data class CausalContext(
    val actionId: String,
    val snapshotHash: Long,
    val timestamp: Long
)

/**
 * 2️⃣ Node Snapshot for Hashing (Lightweight)
 */
data class NodeSnapshot(
    val viewId: String?,
    val text: String?
) {
    fun toHash(): Int {
        return (viewId?.hashCode() ?: 0) * 31 + (text?.hashCode() ?: 0)
    }
}

// =========================================================
// 🔥 INTERFACES & CONFIG
// =========================================================

interface GestureDispatcher {    fun dispatchGesture(
        gesture: android.accessibilityservice.GestureDescription,
        callback: android.accessibilityservice.AccessibilityService.GestureResultCallback?,
        handler: Handler?
    ): Boolean
}

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
    val minScrollChangeThreshold: Double = 0.1, // Used as multiplier for hash diff
    val enableSmoothing: Boolean = true,
    val enableFrameAlignment: Boolean = true,
    val maxSignatureNodes: Int = 50
)

// =========================================================
// 🔥 MAIN ENGINE CLASS (V24)
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
    
    // ✅ Event Channel    private val eventChannel = Channel<AccessibilityEvent>(capacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var lastValidEventTime = 0L
    
    // ✅ FIX #1 & #2: Atomic Causal Context (No global mutable state)
    private val causalContext = AtomicReference<CausalContext?>(null)

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
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return false
        }
        
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
        currentIndex = 0
        lastValidEventTime = 0L
        backoffScheduler.reset()
        causalContext.set(null)
        Log.d("VMAX_FLOW", "📦 Loaded ${list.size} steps | Causal State Machine Mode: ON")    }

    suspend fun reset() = executionMutex.withLock {
        currentIndex = 0
        lastValidEventTime = 0L
        backoffScheduler.reset()
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
    // 🎯 EXECUTION LOOP (Causal State Machine)
    // =========================================================
    private suspend fun executeStep(step: RecordedStep): Boolean {
        var attempts = 0
        val maxAttempts = step.maxRetries.coerceAtLeast(config.defaultMaxRetries)

        while (attempts < maxAttempts && coroutineContext.isActive) {
            try {
                // ✅ FIX #3: Safe Root Handling (Obtain + Recycle)
                val root = safeRoot() ?: run {
                    delay(config.rootRetryDelayMs)
                    attempts++
                    continue
                }
                
                try {
                    // ✅ FIX #2: Build Causal Context for this specific attempt                    val snapshotHash = buildSnapshotHash(root)
                    val actionId = "${step.id}_${System.nanoTime()}"
                    
                    val context = CausalContext(
                        actionId = actionId,
                        snapshotHash = snapshotHash,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    // Set atomic context
                    causalContext.set(context)
                    
                    Log.d("VMAX_FLOW", "🎯 [${step.id}] Target: '${step.criteria}' | Try ${attempts + 1}/$maxAttempts | ActionID: $actionId")

                    val clicked = when {
                        step.id == "step_select_class" && step.criteria.contains(":") -> {
                            val parts = step.criteria.split(":").map { it.trim() }
                            if (parts.size >= 2) targetedClick(root, parts[0], parts[1], actionId) else false
                        }
                        step.criteria.startsWith("@id/") -> findNodeByResourceId(root, step.criteria.removePrefix("@id/"))?.let { smartClick(it, actionId) } ?: false
                        step.criteria.startsWith("@desc/") -> findNodeByContentDesc(root, step.criteria.removePrefix("@desc/"))?.let { smartClick(it, actionId) } ?: false
                        step.criteria.startsWith("@class/") -> findNodeByClassName(root, step.criteria.removePrefix("@class/"))?.let { smartClick(it, actionId) } ?: false
                        else -> findNode(root, step.criteria)?.let { smartClick(it, actionId) } ?: false
                    }

                    if (clicked) {
                        // ✅ FIX #4: Wait for causally correlated event using Atomic Context
                        if (waitForCausalEvent(actionId, config.verificationDelayMs)) {
                            if (verifySuccess(step)) {
                                backoffScheduler.reset()
                                return true
                            }
                        }
                    } else {
                        // Scroll fallback
                        if (!smartScrollVerified(root, actionId)) {
                            Log.d("VMAX_FLOW", "🛑 Scroll failed or exhausted")
                            return false
                        }
                    }
                    
                    attempts++
                    
                } finally {
                    // ✅ FIX #3: Always recycle the obtained root
                    root.recycle()
                }
                
            } catch (e: Exception) {
                Log.e("VMAX_FLOW", "💥 ${e.message}", e)                attempts++
            }

            // ✅ Adaptive Backoff Delay
            val delayMs = backoffScheduler.nextDelay()
            delay(delayMs)
        }
        Log.e("VMAX_FLOW", "❌ Step[${step.id}] failed after $attempts attempts")
        return false
    }

    // =========================================================
    // 🛡 SAFE ROOT HANDLING (✅ FIX #3)
    // =========================================================
    private fun safeRoot(): AccessibilityNodeInfo? {
        val root = getRoot() ?: return null
        // Obtain a copy to ensure we own the lifecycle
        return try {
            AccessibilityNodeInfo.obtain(root)
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================
    // 📸 SNAPSHOT HASHING (✅ FIX #1: Lightweight & Fast)
    // =========================================================
    private fun buildSnapshotHash(root: AccessibilityNodeInfo): Long {
        val cursor = TreeCursor(root)
        var hash = 1L
        var count = 0

        try {
            while (true) {
                val node = cursor.nextVisibleNode() ?: break
                count++

                // Simple, fast hash components
                hash = 31 * hash + (node.viewIdResourceName?.hashCode() ?: 0)
                hash = 31 * hash + (node.text?.hashCode() ?: 0)
                hash = 31 * hash + (node.className?.hashCode() ?: 0)

                if (count > config.maxSignatureNodes) break
            }
        } finally {
            cursor.close()
        }
        return hash
    }
    // =========================================================
    // 🎯 TARGETED CLICK (Causal)
    // =========================================================
    private fun targetedClick(root: AccessibilityNodeInfo, trainNo: String, targetClass: String, actionId: String): Boolean {
        val trainNodes = root.findAccessibilityNodeInfosByText(trainNo)
        if (trainNodes.isNullOrEmpty()) return false

        val trainNode = trainNodes.firstOrNull { it.isVisibleToUser && it.isClickableOrHasClickableChild() }
        trainNodes.filter { it != trainNode }.forEach { it.recycle() }
        if (trainNode == null) return false

        val trainRect = Rect()
        trainNode.getBoundsInScreen(trainRect)
        val trainCenterY = (trainRect.top + trainRect.bottom) / 2

        var parent: AccessibilityNodeInfo? = trainNode
        var depth = 0

        while (parent != null && depth < config.maxParentDepth) {
            var classNodes: List<AccessibilityNodeInfo>? = null
            try {
                classNodes = parent.findAccessibilityNodeInfosByText(targetClass)
                val match = classNodes?.firstOrNull { node ->
                    if (!node.isVisibleToUser) return@firstOrNull false
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val nodeCenterY = (rect.top + rect.bottom) / 2
                    abs(nodeCenterY - trainCenterY) < ROW_TOLERANCE_PX
                }

                if (match != null) {
                    val res = smartClick(match, actionId)
                    match.recycle()
                    return res
                }
            } finally {
                classNodes?.forEach { if (it != trainNode) it.recycle() }
            }
            val old = parent
            parent = parent.parent
            if (old != trainNode) old?.recycle()
            depth++
        }
        trainNode.recycle()
        return false
    }

    // =========================================================
    // 🖱 SMART CLICK (Causal & Validated)
    // =========================================================    private suspend fun smartClick(node: AccessibilityNodeInfo, actionId: String): Boolean {
        if (node.isClickable && node.isEnabled && node.isVisibleToUser) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (result) {
                return waitForCausalEvent(actionId, config.clickDelayMs)
            }
            return false
        }
        
        if (node.isVisibleToUser) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                delay(config.clickDelayMs)
                val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (result) {
                    return waitForCausalEvent(actionId, config.clickDelayMs)
                }
            }
        }
        return false
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

            val path = buildSmoothPath(cx, cy, swipeLength)

            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 150))
                .build()

            // ✅ FIX #6: Gesture Pipeline with Hash Comparison
            val gestureSuccess = executeGestureWithAck(
                gesture = gesture,
                actionId = actionId,
                verifyChange = {
                    val newRoot = safeRoot() ?: return@executeGestureWithAck false
                    try {
                        val newHash = buildSnapshotHash(newRoot)
                        val oldHash = causalContext.get()?.snapshotHash ?: 0L
                        // Significant change detected
                        abs(newHash - oldHash) > (config.minScrollChangeThreshold * 1000).toLong()                    } finally {
                        newRoot.recycle()
                    }
                },
                timeoutMs = 1500L
            )

            if (!gestureSuccess) return false

            waitForCausalEvent(actionId, config.scrollDelayMs)

            val newRoot = safeRoot() ?: return false
            try {
                val newHash = buildSnapshotHash(newRoot)
                val oldHash = causalContext.get()?.snapshotHash ?: 0L
                
                if (abs(newHash - oldHash) <= (config.minScrollChangeThreshold * 1000).toLong()) {
                    Log.d("VMAX_FLOW", "🔄 Scroll gesture succeeded but UI didn't change significantly")
                    return false
                }
                Log.d("VMAX_FLOW", "✅ Scroll verified")
                return true
            } finally {
                newRoot.recycle()
            }
            
        } finally {
            scrollable.recycle()
        }
    }

    private fun buildSmoothPath(cx: Float, cy: Float, len: Float): Path {
        return Path().apply {
            moveTo(cx, cy + len / 2)
            cubicTo(
                cx + 10, cy,
                cx - 10, cy,
                cx, cy - len / 2
            )
        }
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isVisibleToUser && root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            try {
                val res = findScrollable(child)
                if (res != null) return res
            } finally {                child.recycle()
            }
        }
        return null
    }

    // =========================================================
    // 🔥 GESTURE ACK PIPELINE (✅ FIX #6: Scope Binding)
    // =========================================================
    private suspend fun executeGestureWithAck(
        gesture: android.accessibilityservice.GestureDescription,
        actionId: String,
        verifyChange: suspend () -> Boolean,
        timeoutMs: Long = 1500L
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            suspendCoroutine { cont ->
                val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: android.accessibilityservice.GestureDescription?) {
                        if (!cont.isCompleted) {
                            engineScope.launch {
                                val changed = verifyChange()
                                if (cont.isActive) cont.resume(changed)
                            }
                        }
                    }
                    override fun onCancelled(g: android.accessibilityservice.GestureDescription?) {
                        if (!cont.isCompleted && cont.isActive) cont.resume(false)
                    }
                }
                
                val dispatched = gestureDispatcher.dispatchGesture(gesture, callback, null)
                if (!dispatched && !cont.isCompleted) {
                    cont.resume(false)
                }
            }
        } ?: false
    }

    // =========================================================
    // ✅ UI STABILITY & HASHING (Iterative Cursor)
    // =========================================================
    
    // ✅ FIX #2: Safe TreeCursor with clear ownership (Pure Iterator)
    class TreeCursor(private val root: AccessibilityNodeInfo) {
        private val queue = ArrayDeque<AccessibilityNodeInfo>()
        private var nodesVisited = 0
        private val maxNodes = 100

        init {            queue.addLast(root)
        }

        fun nextVisibleNode(): AccessibilityNodeInfo? {
            while (queue.isNotEmpty() && nodesVisited < maxNodes) {
                val current = queue.removeFirst()
                nodesVisited++
                
                if (current.isVisibleToUser) {
                    for (i in 0 until current.childCount) {
                        current.getChild(i)?.let { queue.addLast(it) }
                    }
                    return current // Caller owns this node
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

    // =========================================================
    // 🔥 CAUSAL EVENT WAITING (✅ FIX #4: Strict Validation)
    // =========================================================
    private suspend fun waitForCausalEvent(actionId: String, timeoutMs: Long): Boolean {
        val context = causalContext.get() ?: return false
        
        // Ensure we are waiting for the correct action
        if (context.actionId != actionId) return false

        return withTimeoutOrNull(timeoutMs) {
            // ✅ FIX #4: Use receiveAsFlow().first for strict causal matching
            eventChannel.receiveAsFlow().first { event ->
                val root = safeRoot() ?: return@first false
                try {
                    val newHash = buildSnapshotHash(root)
                    val diff = abs(newHash - context.snapshotHash)
                    
                    // Strict validation: Event type must be content change AND hash must differ
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && diff > 0
                } finally {
                    root.recycle()
                }
            }
            true // If first() returns, condition was met        } ?: false
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
                    return AccessibilityNodeInfo.obtain(current) // Caller owns this
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
        return target // Caller owns this
    }

    private fun findNodeByClassName(root: AccessibilityNodeInfo, cls: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByClassName(cls)
        val target = nodes?.firstOrNull { it.isVisibleToUser }
        nodes?.filter { it != target }?.forEach { it.recycle() }
        return target // Caller owns this
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val target = nodes?.firstOrNull { it.isVisibleToUser && (it.isClickable || it.isClickableOrHasClickableChild()) }
        nodes?.filter { it != target }?.forEach { it.recycle() }
        return target // Caller owns this
    }
    // ✅ Iterative check for clickable child (No Recursion)
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
    // ✅ VERIFICATION
    // =========================================================
    private suspend fun verifySuccess(step: RecordedStep): Boolean {
        val criteria = step.successCriteria?.takeIf { it.isNotBlank() } ?: getDefaultSuccessCriteria(step.id)
        if (criteria.isEmpty()) return true

        repeat(config.verificationAttempts) { attempt ->
            val newRoot = safeRoot() ?: run { 
                waitForCausalEvent(step.id, config.verificationDelayMs)
                return@repeat 
            }
            try {
                if (checkCriteria(newRoot, criteria)) return true
            } finally {
                newRoot.recycle()
            }
            if (attempt < config.verificationAttempts - 1) {
                waitForCausalEvent(step.id, config.verificationDelayMs)
            }
        }
        return false
    }
    private fun checkCriteria(root: AccessibilityNodeInfo, criteria: String): Boolean {
        val options = criteria.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        for (opt in options) {
            when {
                opt.startsWith("@id/") -> {
                    val node = findNodeByResourceId(root, opt.removePrefix("@id/"))
                    if (node != null) { node.recycle(); return true }
                }
                opt.startsWith("@desc/") -> {
                    val node = findNodeByContentDesc(root, opt.removePrefix("@desc/"))
                    if (node != null) { node.recycle(); return true }
                }
                else -> {
                    val nodes = root.findAccessibilityNodeInfosByText(opt)
                    val found = nodes?.any { it.isVisibleToUser && it.text?.contains(opt, true) == true } == true
                    nodes?.forEach { it.recycle() }
                    if (found) return true
                }
            }
        }
        return false
    }

    private fun getDefaultSuccessCriteria(stepId: String): String = when (stepId) {
        "step_select_class" -> "@id/passenger_details|passenger"
        "step_book_now" -> "@id/payment_container|captcha"
        "step_login" -> "@id/home_screen|dashboard"
        else -> ""
    }
}

// =========================================================
// 🔥 ADAPTIVE BACKOFF SCHEDULER (Safe Math)
// =========================================================
class AdaptiveBackoffScheduler {
    private var baseDelay = 100L
    private var attemptCount = 0
    
    fun nextDelay(): Long {
        attemptCount++
        // ✅ Safe bounded exponential: 100, 200, 400... capped at 2000
        val shiftAmount = (attemptCount - 1).coerceAtMost(10) // Prevent overflow
        val exponential = baseDelay * (1L shl shiftAmount)
        val jitter = (Math.random() * 50).toLong()
        return (exponential + jitter).coerceAtMost(2000L)
    }
    
    fun reset() {
        baseDelay = 100L        attemptCount = 0
    }
}
