package com.aare.vmax.v2.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.v2.model.EventBuffer
import com.aare.vmax.v2.model.UiFingerprint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class AccessEngineV2(
    private val service: AccessibilityService,
    private val context: Context
) {
    private val buffer = EventBuffer()
    private var engineScope: CoroutineScope? = null
    private val processChannel = Channel<ProcessCommand>(capacity = 16)
    private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    @Volatile private var currentForegroundPackage: String? = null
    private var lastScanDepth = 3
    private var frameDropCount = 0

    companion object {
        private const val MIN_DEPTH = 2
        private const val MAX_DEPTH = 6
        private val FOREGROUND_SIGNAL_TYPES = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED
        )
    }

    fun initialize(scope: CoroutineScope) {
        shutdown()
        engineScope = scope
        workerScope.launch {
            for (command in processChannel) {
                if (command is ProcessCommand.ProcessEvents) {
                    handleProcessEvents(command.targetPackage, command.keywords, command.onCandidate)
                } else if (command is ProcessCommand.Shutdown) break
            }
        }
        scope.launch { monitorFramePressure() }
    }
    
    fun queueEvent(event: AccessibilityEvent) {
        if (event.eventType in FOREGROUND_SIGNAL_TYPES) {
            currentForegroundPackage = event.packageName?.toString()
        }
        buffer.offer(event)
    }
    
    suspend fun processLatestEventsAsync(pkg: String, kws: List<String>, callback: (UiFingerprint) -> Unit) {
        processChannel.send(ProcessCommand.ProcessEvents(pkg, kws, callback))
    }
    
    private suspend fun handleProcessEvents(pkg: String, kws: List<String>, callback: (UiFingerprint) -> Unit) {
        val events = buffer.consumeLatest()
        if (events.isEmpty()) return
        val root = service.rootInActiveWindow ?: return
        try {
            val candidates = incrementalScan(root, pkg, lastScanDepth, kws)
            lastScanDepth = computeAdaptiveDepth(candidates.size, frameDropCount)
            candidates.forEach { callback(it) }
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
    }
    
    private fun incrementalScan(root: AccessibilityNodeInfo, pkg: String, depth: Int, kws: List<String>): List<UiFingerprint> {
        val results = mutableListOf<UiFingerprint>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(Pair(root, 0))
        while (queue.isNotEmpty()) {
            val (node, d) = queue.removeFirst()
            try {
                if (d > depth) continue
                UiFingerprint.from(node)?.let { results.add(it) }
                for (i in 0 until node.childCount) {
                    try {
                        node.getChild(i)?.let { queue.add(Pair(it, d + 1)) }
                    } catch (_: Exception) {}
                }
            } finally {
                if (node !== root) try { node.recycle() } catch (_: Exception) {}
            }
        }
        return results
    }

    private fun computeAdaptiveDepth(found: Int, drops: Int): Int {
        var next = lastScanDepth
        if (drops > 3) next-- else if (found == 0) next++
        return next.coerceIn(MIN_DEPTH, MAX_DEPTH)
    }

    private suspend fun monitorFramePressure() {
        var lastTime = System.nanoTime()
        // ✅ Fix: Simplified loop that works everywhere without extra imports
        while (true) {
            try {
                delay(100)
                val now = System.nanoTime()
                val frameMs = (now - lastTime) / 1_000_000
                frameDropCount = if (frameMs > 33) minOf(frameDropCount + 1, 10) else maxOf(0, frameDropCount - 1)
                lastTime = now
            } catch (e: Exception) {
                break
            }
        }
    }

    fun shutdown() {
        runBlocking { try { processChannel.send(ProcessCommand.Shutdown) } catch(_: Exception) {} }
        workerScope.cancel()
        buffer.clear()
    }

    private sealed class ProcessCommand {
        data class ProcessEvents(val targetPackage: String, val keywords: List<String>, val onCandidate: (UiFingerprint) -> Unit) : ProcessCommand()
        object Shutdown : ProcessCommand()
    }
}
