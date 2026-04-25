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

// ✅ FIX 1: SafeRecycle को यहीं जोड़ दिया ताकि कोई "Unresolved Reference" एरर न आए
object SafeRecycle {
    fun recycle(node: AccessibilityNodeInfo?) {
        try { node?.recycle() } catch (_: Exception) {}
    }
}

class WorkflowEngine : AccessibilityService() {
    
    companion object {
        private const val TAG = "VMAX_Workflow"
        // ✅ FIX 2: इन्हें Public कर दिया (private हटा दिया) ताकि MainScreen से एरर न आए
        const val ACTION_START = "com.aare.vmax.ACTION_START"
        const val EXTRA_TASK = "extra_task"
        
        // ⚡ उस्ताद का हाइपर-स्पीड कॉन्फ़िग
        private const val RADAR_SCAN_MS = 50L              // 50ms एंटी-ब्लॉक रडार
        private const val EARLY_FIRE_MS = 200L             // 200ms पहले फायर
        private const val FIELD_FILL_DELAY_MS = 10L        // 12GB RAM के लिए अल्ट्रा-फास्ट
        private const val VISIBILITY_TIMEOUT_MS = 2000L    
    }
    
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actionMutex = Mutex()
    private var activeTask: SniperTask? = null
    private var isExecuting = false
    
    // 🎯 IRCTC Resource IDs (Bulletproof IDs)
    private object IRCTCIds {
        const val PASSENGER_NAME = "cris.org.in.prs.ima:id/et_passenger_name"
        const val PASSENGER_AGE = "cris.org.in.prs.ima:id/et_passenger_age"
        const val ADD_PASSENGER_BTN = "cris.org.in.prs.ima:id/btn_add_passenger"
        const val REVIEW_JOURNEY_BTN = "cris.org.in.prs.ima:id/btn_review"
        const val PAYMENT_UPI = "cris.org.in.prs.ima:id/payment_upi"
    }

    // ========================================
    // 🛡️ ANDROID LIFECYCLE
    // ========================================
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 20
            flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or 
                    AccessibilityServiceInfo.DEFAULT
        }
        Log.d(TAG, "✅ स्नाइपर इंजन मुकम्मल! फायरिंग के लिए तैयार।")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            @Suppress("DEPRECATION")
            activeTask = intent.getParcelableExtra(EXTRA_TASK) as? SniperTask
            if (activeTask != null) {
                Log.d(TAG, "🎯 Task loaded: ${activeTask?.taskId}")
                schedulePreFireCheck()
            } else {
                Log.e(TAG, "❌ Failed to load SniperTask from intent")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Polling-based, so no event processing needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Service interrupted")
        isExecuting = false
    }

    override fun onDestroy() {
        engineScope.cancel()
        Log.d(TAG, "🛑 WorkflowEngine destroyed")
        super.onDestroy()
    }

    // ========================================
    // ⚡ स्टेप 1: टाइमिंग लॉजिक (8, 10, 11 AM - 200ms)
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
            }
            
            val exactFireTimeMs = calendar.timeInMillis - EARLY_FIRE_MS

            // अगर बटन दबाते ही टाइम हो चुका है
            if (System.currentTimeMillis() >= exactFireTimeMs) {
                Log.d(TAG, "🎯 फर्स्ट क्लिक! तुरंत हमला शुरू...")
                executeWorkflow()
                return@launch
            }
            
            Log.d(TAG, "⏰ टाइमर लॉक: ठीक 200ms पहले फायर होगा।")
            while (System.currentTimeMillis() < exactFireTimeMs && isActive) { 
                delay(10) 
            }
            
            if (isActive) executeWorkflow()
        }
    }

    // ========================================
    // 🚀 स्टेप 2: एंकर लॉजिक + इंडेक्स-अवेयर फिलिंग
    // ========================================
    private suspend fun executeWorkflow() = actionMutex.withLock {
        if (isExecuting) return@withLock
        isExecuting = true
        val task = activeTask ?: run {
            Log.w(TAG, "⚠️ No active task")
            isExecuting = false
            return@withLock
        }        
        try {
            Log.d(TAG, "🚂 ट्रेन ${task.trainNumber} पर क्लास ${task.travelClass} ढूँढ रहे हैं...")
            val trainClicked = findAndClickTrainClass(task.trainNumber, task.travelClass)
            if (!trainClicked) {
                Log.w(TAG, "⚠️ Train/Class not found — proceeding anyway")
            }

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

            if (!pageLoaded) {
                Log.e(TAG, "❌ Passenger page not loaded within timeout")
                return@withLock
            }
            Log.d(TAG, "✅ Passenger page loaded — starting fill")

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
            Log.d(TAG, "✅ Workflow completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Workflow execution error", e)
        } finally {
            isExecuting = false
            Log.d(TAG, "🛑 Workflow execution finished")
        }
    }

    // ========================================
    // ✍️ स्टेप 3: डेटा फिलिंग
    // ========================================
    private suspend fun fillPassengerData(passenger: PassengerData, index: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val names = root.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_NAME)
            val ages = root.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_AGE)
            
            if (names.size > index && ages.size > index && names[index].isEditable && ages[index].isEditable) {
                val nameArgs = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, passenger.name) }
                names[index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, nameArgs)
                delay(FIELD_FILL_DELAY_MS)
                
                val ageArgs = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, passenger.age) }
                ages[index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, ageArgs)
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
                val text = node.text?.toString()?.lowercase() ?: ""
                if (terms.any { term -> text.contains(term.lowercase()) } && node.isClickable && node.isVisibleToUser) {
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
