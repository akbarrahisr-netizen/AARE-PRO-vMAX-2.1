package com.aare.vmax.core.orchestrator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.core.model.SniperTask
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

object SafeRecycle {
    fun recycle(node: AccessibilityNodeInfo?) {
        try { node?.recycle() } catch (_: Exception) {}
    }
    fun recycleAll(nodes: Collection<AccessibilityNodeInfo>?) {
        nodes?.forEach { recycle(it) }
    }
}

class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Workflow"
        const val ACTION_START = "com.aare.vmax.ACTION_START"
        const val EXTRA_TASK = "extra_task"

        private const val RADAR_SCAN_MS = 50L
        private const val EARLY_FIRE_MS = 200L
        private const val FIELD_FILL_DELAY_MS = 10L
        private const val VISIBILITY_TIMEOUT_MS = 2000L

        private const val DEBUG_LOGS = true
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actionMutex = Mutex()

    private var activeTask: SniperTask? = null
    private var isExecuting = false
    private val textActionBundle = Bundle()
    private val lowercaseCache = mutableMapOf<String, String>()

    private object IRCTCIds {
        const val PASSENGER_NAME = "cris.org.in.prs.ima:id/et_passenger_name"
        const val PASSENGER_AGE = "cris.org.in.prs.ima:id/et_passenger_age"
        const val ADD_PASSENGER_BTN = "cris.org.in.prs.ima:id/btn_add_passenger"
        const val REVIEW_JOURNEY_BTN = "cris.org.in.prs.ima:id/btn_review"
        const val PAYMENT_UPI = "cris.org.in.prs.ima:id/payment_upi"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 20
            flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.DEFAULT
        }
        if (DEBUG_LOGS) Log.d(TAG, "✅ WorkflowEngine connected")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            @Suppress("DEPRECATION")
            activeTask = intent.getParcelableExtra(EXTRA_TASK)
            if (activeTask != null) {
                schedulePreFireCheck()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        isExecuting = false
    }

    override fun onDestroy() {
        engineScope.cancel()
        textActionBundle.clear()
        lowercaseCache.clear()
        activeTask = null
        super.onDestroy()
    }

    private fun schedulePreFireCheck() {
        val task = activeTask ?: return

        engineScope.launch {
            val targetHour = when (task.quota) {
                "General" -> 8
                else -> if (task.travelClass in listOf("1A", "2A", "3A", "3E", "CC")) 10 else 11
            }

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val exactFireTimeMs = calendar.timeInMillis - EARLY_FIRE_MS

            if (System.currentTimeMillis() >= exactFireTimeMs) {
                executeWorkflow()
                return@launch
            }

            while (System.currentTimeMillis() < exactFireTimeMs && isActive) {
                delay(10)
            }

            if (isActive) executeWorkflow()
        }
    }

    private suspend fun executeWorkflow() = actionMutex.withLock {
        if (isExecuting) return@withLock
        isExecuting = true

        val task = activeTask ?: run {
            isExecuting = false
            return@withLock
        }

        try {
            findAndClickTrainClass(task.trainNumber, task.travelClass)

            var pageLoaded = false
            val timeoutMs = System.currentTimeMillis() + 5000L

            while (System.currentTimeMillis() < timeoutMs && engineScope.isActive) {
                val root = rootInActiveWindow
                if (root != null) {
                    val nodes = root.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_NAME)
                    if (nodes.isNotEmpty()) {
                        pageLoaded = true
                        SafeRecycle.recycle(root)
                        break
                    }
                    SafeRecycle.recycle(root)
                }
                delay(RADAR_SCAN_MS)
            }

            if (!pageLoaded) return@withLock

            val activePassengers = task.passengers.filter { it.isFilled() }

            for ((index, passenger) in activePassengers.withIndex()) {
                if (fillPassengerData(passenger, index)) {
                    if (index < activePassengers.lastIndex) {
                        clickAddPassenger()
                        waitForNewForm(index + 1)
                    }
                }
            }

            triggerPaymentFlow()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Execution error", e)
        } finally {
            isExecuting = false
            textActionBundle.clear()
            if (lowercaseCache.size > 100) lowercaseCache.clear()
        }
    }

    private suspend fun fillPassengerData(passenger: PassengerData, index: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val names = root.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_NAME)
            val ages = root.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_AGE)

            if (names.size > index && ages.size > index && names[index].isEditable && ages[index].isEditable) {

                textActionBundle.clear()
                textActionBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, passenger.name)
                names[index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textActionBundle)

                delay(FIELD_FILL_DELAY_MS)

                textActionBundle.clear()
                textActionBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, passenger.age)
                ages[index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textActionBundle)

                true
            } else {
                false
            }
        } finally {
            SafeRecycle.recycle(root)
        }
    }

    private fun findAndClickTrainClass(trainNo: String, className: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val trainNodes = root.findAccessibilityNodeInfosByText(trainNo)
            if (trainNodes.isEmpty()) return false

            for (i in 1 until trainNodes.size) SafeRecycle.recycle(trainNodes[i])

            var card = trainNodes[0].parent
            var depth = 0
            while (card != null && depth < 5) {
                try {
                    val classes = card.findAccessibilityNodeInfosByText(className)
                    if (!classes.isNullOrEmpty()) {
                        for (i in 1 until classes.size) SafeRecycle.recycle(classes[i])
                        val clicked = classes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        SafeRecycle.recycle(classes[0])
                        return clicked
                    }
                    classes?.forEach { SafeRecycle.recycle(it) }
                } finally {
                    val nextParent = card.parent
                    SafeRecycle.recycle(card)
                    card = nextParent
                    depth++
                }
            }
            false
        } finally {
            SafeRecycle.recycle(root)
        }
    }

    private suspend fun waitForNewForm(requiredCount: Int) {
        val end = System.currentTimeMillis() + VISIBILITY_TIMEOUT_MS
        while (System.currentTimeMillis() < end && engineScope.isActive) {
            val root = rootInActiveWindow
            val count = root?.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_NAME)?.size ?: 0
            SafeRecycle.recycle(root)
            if (count >= requiredCount + 1) return
            delay(RADAR_SCAN_MS)
        }
    }

    private fun clickAddPassenger() {
        val root = rootInActiveWindow ?: return
        try {
            val nodes = root.findAccessibilityNodeInfosByViewId(IRCTCIds.ADD_PASSENGER_BTN)
            if (nodes.isNotEmpty() && nodes[0].isClickable && nodes[0].isVisibleToUser) {
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            for (i in 1 until nodes.size) SafeRecycle.recycle(nodes[i])
        } finally {
            SafeRecycle.recycle(root)
        }
    }

    private suspend fun triggerPaymentFlow() {
        delay(200)
        findAndClickByText(listOf("Review Journey", "Proceed", "Pay Now", "Book Now"))
    }

    private fun findAndClickByText(terms: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            try {
                val nodeText = node.text?.toString() ?: ""
                val lowerText = lowercaseCache.getOrPut(nodeText) { nodeText.lowercase() }

                val isMatch = terms.any { term ->
                    val lowerTerm = lowercaseCache.getOrPut(term) { term.lowercase() }
                    lowerText.contains(lowerTerm)
                }

                if (isMatch && node.isClickable && node.isVisibleToUser) {
                    // ✅ FIX: यहाँ ब्रैकेट अलग कर दिया गया है!
                    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }

                repeat(node.childCount) { i ->
                    try { node.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
                }
            } finally {
                if (node !== root) SafeRecycle.recycle(node)
            }
        }
        return false
    }
}
