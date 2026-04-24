package com.aare.vmax.v2.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.Choreographer
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.v2.model.EventBuffer
import com.aare.vmax.v2.model.UiFingerprint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * 🚀 PRODUCTION-READY V2.1 (ULTRA STABLE)
 * Optimized for Moto Edge 50 Fusion & High-Speed Automation
 */
class AccessEngineV2(
    private val service: AccessibilityService,
    private val context: Context
) {
    private val buffer = EventBuffer()
    private var engineScope: CoroutineScope? = null
    
    private val processChannel = Channel<ProcessCommand>(capacity = 16)
    private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    @Volatile private var currentForegroundPackage: String? = null
    private val FOREGROUND_SIGNAL_TYPES = setOf(
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        AccessibilityEvent.TYPE_VIEW_FOCUSED
    )
    
    private var scanDepthHistory = ArrayDeque<Int>(5)
    private var lastScanDepth = 3
    
    // ✅ FIX: Separated constant declarations
    private const val MIN_DEPTH = 2
    private const val MAX_DEPTH = 6
    private const val DEPTH_STABILITY_WINDOW = 5
    
    private var frameDropCount = 0

    fun initialize(scope: CoroutineScope) {
        shutdown()
        engineScope = scope
        
        workerScope.launch {
            for (command in processChannel) {
                if (command is ProcessCommand.ProcessEvents) {
                    handleProcessEvents(command.targetPackage, command.keywords, command.onCandidate)
                } else if (command is ProcessCommand.Shutdown) {
                    break
                }
            }
        }
        
        scope.launch { monitorFramePressure() }
    }
    
    fun queueEvent(event: AccessibilityEvent) {
        if (event.eventType in FOREGROUND_SIGNAL_TYPES && event.packageName != null) {
            currentForegroundPackage = event.packageName?.toString()
        }
        buffer.offer(event)
    }
    
    suspend fun processLatestEventsAsync(
        targetPackage: String,
        keywords: List<String>,
        onCandidate: (UiFingerprint) -> Unit
    ) {
        processChannel.send(ProcessCommand.ProcessEvents(targetPackage, keywords, onCandidate))
    }    

    private suspend fun handleProcessEvents(
        targetPackage: String,
        keywords: List<String>,
        onCandidate: (UiFingerprint) -> Unit
    ) {
        val events = buffer.consumeLatest()
        if (events.isEmpty()) return
        
        // फोर्ग्राउंड चेक (IRCTC या अन्य टारगेट ऐप)
        val activeRoot = service.rootInActiveWindow ?: return
        if (activeRoot.packageName?.toString() != targetPackage) {
            activeRoot.recycle()
            return
        }
        
        try {
            // ✅ IMPROVEMENT: Use dynamic scan depth
            val candidates = incrementalScan(activeRoot, targetPackage, lastScanDepth, keywords)
            
            // ✅ Update depth logic based on results
            lastScanDepth = computeAdaptiveDepth(candidates.size, frameDropCount)
            
            candidates.forEach { onCandidate(it) }
            
        } finally {
            try { activeRoot.recycle() } catch (_: Exception) {}
        }
    }
    
    private fun incrementalScan(
        root: AccessibilityNodeInfo, 
        targetPackage: String,
        maxDepth: Int,
        keywords: List<String>
    ): List<UiFingerprint> {
        val results = mutableListOf<UiFingerprint>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(Pair(root, 0))
        
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            
            try {
                if (depth > maxDepth) continue
                
                val fp = UiFingerprint.from(node)
                if (fp != null && matchesKeywords(fp, keywords)) {
                    results.add(fp)
                }
                
                for (i in 0 until node.childCount) {
                    try {
                        node.getChild(i)?.let { queue.add(Pair(it, depth + 1)) }
                    } catch (_: Exception) {}
                }
            } finally {
                if (node !== root) {
                    try { node.recycle() } catch (_: Exception) {}
                }
            }
        }
        return results
    }

    private fun computeAdaptiveDepth(found: Int, drops: Int): Int {
        var nextDepth = lastScanDepth
        if (drops > 3) nextDepth-- 
        else if (found == 0) nextDepth++
        
        return nextDepth.coerceIn(MIN_DEPTH, MAX_DEPTH)
    }

    private suspend fun monitorFramePressure() {
        var lastTime = System.nanoTime()
        while (isActive) {
            delay(100)
            val now = System.nanoTime()
            val frameMs = (now - lastTime) / 1_000_000
            frameDropCount = if (frameMs > 33) minOf(frameDropCount + 1, 10) else maxOf(0, frameDropCount - 1)
            lastTime = now
        }
    }

    private fun matchesKeywords(fp: UiFingerprint, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return true
        val combinedText = "${fp.text} ${fp.contentDescription}".lowercase()
        return keywords.any { it.lowercase() in combinedText }
    }

    fun shutdown() {
        runBlocking { 
            try { processChannel.send(ProcessCommand.Shutdown) } catch(_: Exception) {}
        }
        workerScope.cancel()
        engineScope?.cancel()
        buffer.clear()
    }

    private sealed class ProcessCommand {
        data class ProcessEvents(
            val targetPackage: String,
            val keywords: List<String>,
            val onCandidate: (UiFingerprint) -> Unit
        ) : ProcessCommand()
        object Shutdown : ProcessCommand()
    }
}
