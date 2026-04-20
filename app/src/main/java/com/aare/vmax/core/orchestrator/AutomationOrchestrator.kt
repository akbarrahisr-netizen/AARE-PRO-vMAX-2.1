package com.aare.vmax.core.orchestrator

import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.event.*
import com.aare.vmax.core.models.*
import com.aare.vmax.core.executor.ActionExecutor
import com.aare.vmax.core.finder.NodeFinder
import kotlinx.coroutines.*

class AutomationOrchestrator(
    private val eventBus: AutomationEventBus,
    private val workflowEngine: WorkflowEngine,
    private val nodeFinder: NodeFinder,
    private val actionExecutor: ActionExecutor,
    private val scope: CoroutineScope
) {

    private var lastHash = 0
    private var lastScreen: ScreenType = ScreenType.UNKNOWN

    fun start(config: StrikeConfig, getRoot: () -> AccessibilityNodeInfo?) {

        // 1. Event Listener
        eventBus.subscribe { event ->
            when (event) {
                is AutomationEvent.ScreenChanged -> {
                    // ✅ FIX 1: यहाँ नाम सही कर दिया गया है
                    workflowEngine.onScreenChange(event.screenType)
                }
                else -> {}
            }
        }

        // 2. Observer loop
        scope.launch(Dispatchers.Default) {
            observe(getRoot)
        }
    }

    // =========================================================
    // 👁 SCREEN OBSERVER (SAFE VERSION)
    // =========================================================
    // ✅ FIX 2: यहाँ 'CoroutineScope.' जोड़ दिया गया है ताकि isActive का एरर न आए
    private suspend fun CoroutineScope.observe(getRoot: () -> AccessibilityNodeInfo?) {

        while (isActive) {

            val root = getRoot()
            if (root == null) {
                delay(100)
                continue
            }

            try {
                val hash = root.hashCode()

                if (hash != lastHash) {
                    lastHash = hash

                    val screen = detectScreenType(root)

                    // 🚫 prevent duplicate event loop
                    if (screen != lastScreen) {
                        lastScreen = screen
                        eventBus.publish(
                            AutomationEvent.ScreenChanged(screen)
                        )
                    }
                }

            } catch (e: Exception) {
                // silent fail safe
            } finally {
                root.recycle()
            }

            delay(80L)
        }
    }

    // =========================================================
    // 🧠 SCREEN DETECTION (IMPROVED)
    // =========================================================
    private fun detectScreenType(root: AccessibilityNodeInfo): ScreenType {

        val text = buildString {
            root.text?.let { append(it) }
            root.contentDescription?.let { append(it) }
        }.lowercase()

        return when {
            text.contains("search") || text.contains("sort by") ->
                ScreenType.SEARCH_RESULTS

            text.contains("passenger") ->
                ScreenType.PASSENGER_DETAILS

            text.contains("payment") ->
                ScreenType.PAYMENT

            text.contains("captcha") ->
                ScreenType.CAPTCHA

            text.contains("confirm") ->
                ScreenType.CONFIRMATION

            else -> ScreenType.UNKNOWN
        }
    }
}
