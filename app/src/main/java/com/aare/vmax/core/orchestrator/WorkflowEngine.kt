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
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

// 🔥 INTERFACE: Testable gesture dispatching
interface GestureDispatcher {
    fun dispatchGesture(
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
    val expectedPackageName: String = "in.irctc",  // 🔥 Event validation
    val minScrollChangeThreshold: Long = 50L        // 🔥 Scroll verification threshold
)

class WorkflowEngine(
    private val getRoot: () -> AccessibilityNodeInfo?,
    private val gestureDispatcher: GestureDispatcher,
    private val config: EngineConfig = EngineConfig(),    // 🔥 FIX #5: Lifecycle-aware scope injection
    private val engineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val steps = mutableListOf<RecordedStep>()
    private var currentIndex = 0
    private val executionMutex = Mutex()
    
    private val density = android.content.res.Resources.getSystem().displayMetrics.density
    private val ROW_TOLERANCE_PX = (config.rowToleranceDp * density).toInt()
    
    // 🔥 FIX #4: BUFFERED channel - no dropped events
    private val eventChannel = Channel<AccessibilityEvent>(Channel.BUFFERED)
    private var lastValidEventTime = 0L
    private var lastValidEventPackage: String? = null

    // =========================================================
    // 📥 REACTIVE LISTENER (Event-Validated)
    // =========================================================
    fun startReactiveListening(eventFlow: MutableSharedFlow<AccessibilityEvent>) {
        engineScope.launch {
            eventFlow.collect { event ->
                if (isValidUiEvent(event)) {
                    lastValidEventTime = System.currentTimeMillis()
                    lastValidEventPackage = event.packageName?.toString()
                    eventChannel.trySend(event)
                }
            }
        }
    }

    // 🔥 FIX #1: Event validation logic
    private fun isValidUiEvent(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return false
        }
        // 🔥 Package validation
        if (config.expectedPackageName.isNotBlank() && 
            event.packageName?.toString() != config.expectedPackageName) {
            return false
        }
        // 🔥 Source validation (minimum signal that UI actually changed)
        if (event.source == null && event.text?.isEmpty() != false) {
            return false
        }
        // 🔥 Optional: className filter for specific screens
        // if (!expectedClassNames.contains(event.className?.toString())) return false
        return true
    }
    fun notifyEvent(event: AccessibilityEvent) {
        if (isValidUiEvent(event)) {
            lastValidEventTime = System.currentTimeMillis()
            lastValidEventPackage = event.packageName?.toString()
            eventChannel.trySend(event)
        }
    }

    // =========================================================
    // 📦 SAFE LOADING
    // =========================================================
    suspend fun loadRecording(list: List<RecordedStep>) = executionMutex.withLock {
        steps.clear()
        steps.addAll(list)
        currentIndex = 0
        lastValidEventTime = 0L
        lastValidEventPackage = null
        Log.d("VMAX_FLOW", "📦 Loaded ${list.size} steps | Reactive+Validated mode: ON")
    }

    suspend fun reset() = executionMutex.withLock {
        currentIndex = 0
        lastValidEventTime = 0L
        lastValidEventPackage = null
        Log.d("VMAX_FLOW", "🔄 Engine reset")
    }

    fun shutdown() {
        engineScope.cancel()
        eventChannel.close()
        Log.d("VMAX_FLOW", "🔌 Engine shutdown complete")
    }

    // =========================================================
    // 🎬 EVENT-DRIVEN STEP EXECUTION
    // =========================================================
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
        }    }

    // =========================================================
    // 🎯 MAIN EXECUTION LOOP (Event-Validated + Scroll-Verified)
    // =========================================================
    private suspend fun executeStep(step: RecordedStep): Boolean {
        var attempts = 0
        val maxAttempts = step.maxRetries.coerceAtLeast(config.defaultMaxRetries)

        while (attempts < maxAttempts && coroutineContext.isActive) {
            val root = getRoot() ?: run {
                delay(config.rootRetryDelayMs)
                attempts++
                continue
            }

            try {
                Log.d("VMAX_FLOW", "🎯 [${step.id}] Target: '${step.criteria}' | Try ${attempts + 1}/$maxAttempts")

                val clicked = when {
                    step.id == "step_select_class" && step.criteria.contains(":") -> {
                        val parts = step.criteria.split(":").map { it.trim() }
                        if (parts.size >= 2) targetedClick(root, parts[0], parts[1]) else false
                    }
                    step.criteria.startsWith("@id/") -> findNodeByResourceId(root, step.criteria.removePrefix("@id/"))?.let { smartClick(it) } ?: false
                    step.criteria.startsWith("@desc/") -> findNodeByContentDesc(root, step.criteria.removePrefix("@desc/"))?.let { smartClick(it) } ?: false
                    step.criteria.startsWith("@class/") -> findNodeByClassName(root, step.criteria.removePrefix("@class/"))?.let { smartClick(it) } ?: false
                    else -> findNode(root, step.criteria)?.let { smartClick(it) } ?: false
                }

                if (clicked) {
                    // 🔥 Event-validated wait
                    if (waitForValidEventOrTimeout(config.verificationDelayMs)) {
                        if (verifySuccess(step)) return true
                    }
                    attempts++
                    continue
                }

                // 🔥 Scroll with verification
                if (!smartScrollVerified(root)) {
                    Log.d("VMAX_FLOW", "🛑 Scroll exhausted or failed verification")
                    return false
                }
                
                attempts++
                
            } catch (e: Exception) {
                Log.e("VMAX_FLOW", "💥 ${e.message}", e)
                attempts++            }

            // 🔥 Event-validated retry wait
            waitForValidEventOrTimeout(config.attemptDelayMs)
        }
        Log.e("VMAX_FLOW", "❌ Step[${step.id}] failed after $attempts attempts")
        return false
    }

    // =========================================================
    // 🎯 TARGETED STRIKE (Train + Class)
    // =========================================================
    private fun targetedClick(root: AccessibilityNodeInfo, trainNo: String, targetClass: String): Boolean {
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
                    val res = smartClick(match)
                    match.recycle()
                    return res
                }
            } finally {
                classNodes?.forEach { if (it != trainNode) it.recycle() }
            }
            val old = parent
            parent = parent.parent
            if (old != trainNode) old?.recycle()            depth++
        }
        trainNode.recycle()
        return false
    }

    // =========================================================
    // 🖱 SMART CLICK (Parent Recycling Fixed)
    // =========================================================
    private suspend fun smartClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        try {
            while (current != null && depth < config.maxParentDepth) {
                if (current.isClickable && current.isEnabled && current.isVisibleToUser) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                val old = current
                current = current.parent
                if (old != node) old?.recycle()
                depth++
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                delay(config.clickDelayMs)
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } finally {
            current?.recycle()
        }
        return false
    }

    // =========================================================
    // 📜 VERIFIED SCROLL (Gesture + State Change Confirmation)
    // =========================================================
    private suspend fun smartScrollVerified(root: AccessibilityNodeInfo): Boolean {
        val scrollable = findScrollable(root) ?: return false
        try {
            if (!scrollable.isScrollable && scrollable.childCount == 0) return false
            
            // 🔥 FIX #2: Before signature for scroll verification
            val beforeHash = computeLightSignature(root)
            
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

            // 🔥 Timeout-safe gesture execution
            val gestureSuccess = withTimeoutOrNull(1000L) {
                suspendCoroutine { cont ->
                    val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(g: android.accessibilityservice.GestureDescription?) {
                            if (!cont.isCompleted) cont.resume(true)
                        }
                        override fun onCancelled(g: android.accessibilityservice.GestureDescription?) {
                            if (!cont.isCompleted) cont.resume(false)
                        }
                    }
                    if (!gestureDispatcher.dispatchGesture(gesture, callback, null)) {
                        if (!cont.isCompleted) cont.resume(false)
                    }
                }
            } ?: false

            if (!gestureSuccess) return false

            // 🔥 Wait for UI to potentially update
            waitForValidEventOrTimeout(config.scrollDelayMs)

            // 🔥 FIX #2: After signature - verify scroll actually changed UI
            val newRoot = getRoot() ?: return false
            try {
                val afterHash = computeLightSignature(newRoot)
                val hashDiff = abs(afterHash - beforeHash)
                
                // 🔥 If hash didn't change enough, scroll likely failed
                if (hashDiff < config.minScrollChangeThreshold) {
                    Log.d("VMAX_FLOW", "🔄 Scroll gesture succeeded but UI didn't change (hash diff: $hashDiff)")
                    return false
                }
                Log.d("VMAX_FLOW", "✅ Scroll verified (hash diff: $hashDiff)")
                return true
            } finally {
                // System root - no recycle
            }
            
        } finally {
            scrollable.recycle()
        }    }

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
        return null
    }

    // =========================================================
    // ✅ UI STABILITY (Optimized + Entropy-Boosted)
    // =========================================================
    private suspend fun waitForUiStable(root: AccessibilityNodeInfo) {
        var lastHash = 0L
        repeat(4) {
            if (!waitForValidEventOrTimeout(config.uiStabilityThresholdMs)) return
            
            val currentRoot = getRoot() ?: return
            try {
                // 🔥 FIX #3: Entropy-boosted signature
                val currentHash = computeLightSignature(currentRoot, includeBounds = true)
                if (currentHash == lastHash && lastHash != 0L) return
                lastHash = currentHash
            } finally {
                // System root - no recycle
            }
        }
    }

    // 🔥 FIX #3: Entropy-boosted hash with bounds + contentDescription
    private fun computeLightSignature(
        root: AccessibilityNodeInfo, 
        depth: Int = 0,
        includeBounds: Boolean = false
    ): Long {
        if (depth > config.signatureDepthLimit) return 0L
        
        var hash = 0L
        var count = 0
        
        // Core properties
        hash = hash * 31 + (root.viewIdResourceName?.hashCode() ?: 0)
        hash = hash * 31 + (root.className?.hashCode() ?: 0)        hash = hash * 31 + (root.text?.hashCode() ?: 0)
        hash = hash * 31 + (root.contentDescription?.hashCode() ?: 0)  // 🔥 Added
        hash = hash * 31 + root.childCount
        
        // 🔥 Optional: bounds entropy for higher collision resistance
        if (includeBounds) {
            val rect = Rect()
            root.getBoundsInScreen(rect)
            hash = hash * 31 + rect.centerX()
            hash = hash * 31 + rect.centerY()
            hash = hash * 31 + (rect.width() shr 4)  // Compress to avoid overflow
            hash = hash * 31 + (rect.height() shr 4)
        }
        
        // Sample children
        for (i in 0 until root.childCount step config.signatureSampleRate) {
            val child = root.getChild(i) ?: continue
            try {
                if (child.isVisibleToUser) {
                    hash = hash * 31 + computeLightSignature(child, depth + 1, includeBounds)
                    count++
                    if (count >= 8) break
                }
            } finally {
                child.recycle()
            }
        }
        return hash
    }

    // =========================================================
    // 🔥 FIX #1: EVENT-VALIDATED WAITING
    // =========================================================
    private suspend fun waitForValidEventOrTimeout(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        
        // Check if valid event already arrived recently
        if (System.currentTimeMillis() - lastValidEventTime < 50L) {
            return lastValidEventPackage == config.expectedPackageName
        }
        
        return withTimeoutOrNull(timeoutMs) {
            select<Boolean> {
                eventChannel.onReceive { event ->
                    // 🔥 Double-validate on receive (defensive)
                    isValidUiEvent(event)
                }
                onTimeout { false }
            }
        } ?: false    }

    // =========================================================
    // 🔍 BFS SEARCH (Queue Cleanup Fixed)
    // =========================================================
    private fun findNodeByContentDesc(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
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
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val target = nodes?.firstOrNull { it.isVisibleToUser && (it.isClickable || it.isClickableOrHasClickableChild()) }
        nodes?.filter { it != target }?.forEach { it.recycle() }
        return target
    }
    private fun AccessibilityNodeInfo.isClickableOrHasClickableChild(): Boolean {
        if (isClickable && isEnabled) return true
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            try {
                if (child.isClickableOrHasClickableChild()) return true
            } finally { child.recycle() }
        }
        return false
    }

    // =========================================================
    // ✅ VERIFICATION (Event-Validated)
    // =========================================================
    private suspend fun verifySuccess(step: RecordedStep): Boolean {
        val criteria = step.successCriteria?.takeIf { it.isNotBlank() } ?: getDefaultSuccessCriteria(step.id)
        if (criteria.isEmpty()) return true

        repeat(config.verificationAttempts) { attempt ->
            val newRoot = getRoot() ?: run { 
                waitForValidEventOrTimeout(config.verificationDelayMs)
                return@repeat 
            }
            try {
                if (checkCriteria(newRoot, criteria)) return true
            } finally {
                // System root - NO recycle
            }
            if (attempt < config.verificationAttempts - 1) {
                waitForValidEventOrTimeout(config.verificationDelayMs)
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
                    val found = nodes?.any { it.isVisibleToUser && it.text?.contains(opt, true) == true } == true                    nodes?.forEach { it.recycle() }
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
