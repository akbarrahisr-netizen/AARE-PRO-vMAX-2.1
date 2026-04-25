package com.aare.vmax.core.orchestrator

import android.accessibilityservice.*
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.accessibility.*
import androidx.core.content.ContextCompat
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.core.model.BookingOptions
import com.aare.vmax.core.model.SniperTask
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

// ✅ मेमोरी लीक बचाने के लिए नोड रीसायकल हेल्पर
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

    // 🎯 IRCTC IDs (100% Merged)
    private object IRCTCIds {
        const val PASSENGER_NAME = "cris.org.in.prs.ima:id/et_passenger_name"
        const val PASSENGER_AGE = "cris.org.in.prs.ima:id/et_passenger_age"
        const val ADD_PASSENGER_BTN = "cris.org.in.prs.ima:id/btn_add_passenger"
        
        // पेमेंट और एडवांस ऑप्शंस के IDs
        const val PAYMENT_UPI = "cris.org.in.prs.ima:id/payment_upi"
        const val UPI_BHIM = "cris.org.in.prs.ima:id/bhim_upi"
        const val UPI_PHONEPE = "cris.org.in.prs.ima:id/phonepe"
        const val UPI_PAYTM = "cris.org.in.prs.ima:id/paytm_upi"
        const val BOOK_NOW_BTN = "cris.org.in.prs.ima:id/btn_book_now"
    }

    // ✅ BROADCAST RECEIVER: UI से सिग्नल पकड़ने के लिए
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_START) {
                @Suppress("DEPRECATION")
                val task = intent.getParcelableExtra<SniperTask>(EXTRA_TASK)
                if (task != null) {
                    if (DEBUG_LOGS) Log.d(TAG, "🚀 Signal Received: Task for ${task.trainNumber}")
                    activeTask = task
                    schedulePreFireCheck()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 20
            flags = AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        
        // रिसीवर को रजिस्टर करना
        val filter = IntentFilter(ACTION_START)
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        if (DEBUG_LOGS) Log.d(TAG, "✅ WorkflowEngine Ready & Registered")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // यहाँ हम लाइव स्क्रीन को मॉनिटर कर सकते हैं
    }

    override fun onInterrupt() {
        isExecuting = false
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        engineScope.cancel()
        textActionBundle.clear()
        lowercaseCache.clear()
        activeTask = null
        super.onDestroy()
    }

    // ========================================
    // ⏰ TIMING LOGIC (Tatkal Time Control)
    // ========================================
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

    // ========================================
    // ⚡ THE SNIPER WORKFLOW (Main Engine)
    // ========================================
    private suspend fun executeWorkflow() = actionMutex.withLock {
        if (isExecuting) return@withLock
        isExecuting = true

        val task = activeTask ?: run { isExecuting = false; return@withLock }

        try {
            // 1. ट्रेन और क्लास ढूँढना
            findAndClickTrainClass(task.trainNumber, task.travelClass)

            // 2. पैसेंजर पेज लोड होने का इंतज़ार
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

            // 3. पैसेंजर डिटेल्स भरना
            val activePassengers = task.passengers.filter { it.isFilled() }
            for ((index, passenger) in activePassengers.withIndex()) {
                fillPassengerComplete(passenger, index)
                if (index < activePassengers.lastIndex) {
                    clickAddPassenger()
                    waitForNewForm(index + 1)
                }
            }
            
            // 4. पेमेंट और फाइनल सबमिशन
            triggerPaymentFlow(task)

            if (DEBUG_LOGS) Log.d(TAG, "✅ Workflow Successful!")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Execution error: ${e.message}")
        } finally {
            isExecuting = false
            textActionBundle.clear()
            if (lowercaseCache.size > 100) lowercaseCache.clear()
        }
    }

    // ========================================
    // ✍️ FILLING & CLICKING HELPERS
    // ========================================
    private suspend fun fillPassengerComplete(passenger: PassengerData, index: Int) {
        val root = rootInActiveWindow ?: return
        try {
            fillFieldById(root, IRCTCIds.PASSENGER_NAME, passenger.name, index)
            delay(FIELD_FILL_DELAY_MS)
            fillFieldById(root, IRCTCIds.PASSENGER_AGE, passenger.age, index)
            
            // जेंडर और बर्थ प्रेफरेंस
            selectDropdown("Gender", passenger.gender)
            if (passenger.berthPreference != "No Preference") {
                selectDropdown("Berth Preference", passenger.berthPreference)
            }
        } finally {
            SafeRecycle.recycle(root)
        }
    }

    private suspend fun triggerPaymentFlow(task: SniperTask) {
        delay(200)
        val root = rootInActiveWindow ?: return
        try {
            // पेमेंट मेथड सिलेक्शन लॉजिक
            when (task.paymentMethod) {
                "UPI" -> clickById(root, IRCTCIds.PAYMENT_UPI)
            }
            
            delay(300)
            // फाइनल बुक बटन
            clickById(root, IRCTCIds.BOOK_NOW_BTN)
        } finally {
            SafeRecycle.recycle(root)
        }
    }

    private fun fillFieldById(root: AccessibilityNodeInfo, id: String, text: String, index: Int): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        if (nodes.size > index && nodes[index].isEditable) {
            textActionBundle.clear()
            textActionBundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val success = nodes[index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textActionBundle)
            SafeRecycle.recycleAll(nodes)
            return success
        }
        SafeRecycle.recycleAll(nodes)
        return false
    }

    private fun clickById(root: AccessibilityNodeInfo, id: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        if (nodes.isNotEmpty() && nodes[0].isClickable) {
            val success = nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            SafeRecycle.recycleAll(nodes)
            return success
        }
        SafeRecycle.recycleAll(nodes)
        return false
    }

    private suspend fun selectDropdown(label: String, value: String) {
        if (findAndClickByText(listOf(label))) {
            delay(300) 
            findAndClickByText(listOf(value))
        }
    }

    private fun findAndClickTrainClass(trainNo: String, className: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val trainNodes = root.findAccessibilityNodeInfosByText(trainNo)
            if (trainNodes.isEmpty()) return false
            var card = trainNodes[0].parent
            var depth = 0
            while (card != null && depth < 5) {
                val classes = card.findAccessibilityNodeInfosByText(className)
                if (!classes.isNullOrEmpty()) {
                    val clicked = classes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return clicked
                }
                card = card.parent
                depth++
            }
            false
        } finally {
            SafeRecycle.recycle(root)
        }
    }

    private fun clickAddPassenger() {
        val root = rootInActiveWindow ?: return
        try {
            val nodes = root.findAccessibilityNodeInfosByViewId(IRCTCIds.ADD_PASSENGER_BTN)
            if (nodes.isNotEmpty()) nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            SafeRecycle.recycleAll(nodes)
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

    private fun findAndClickByText(terms: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            try {
                val nodeText = node.text?.toString() ?: ""
                val lowerText = lowercaseCache.getOrPut(nodeText) { nodeText.lowercase() }
                val isMatch = terms.any { term -> lowerText.contains(term.lowercase()) }
                if (isMatch && node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                repeat(node.childCount) { i -> node.getChild(i)?.let { queue.add(it) } }
            } finally {
                if (node !== root) SafeRecycle.recycle(node)
            }
        }
        return false
    }
}
