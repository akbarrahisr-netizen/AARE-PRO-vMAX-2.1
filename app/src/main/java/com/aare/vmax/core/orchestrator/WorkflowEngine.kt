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
import com.aare.vmax.core.utils.SafeRecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class WorkflowEngine : AccessibilityService() {
    
    companion object {
        private const val TAG = "VMAX_Workflow"
        private const val ACTION_START = "com.aare.vmax.ACTION_START"
        private const val EXTRA_TASK = "extra_task"
        
        // ⚡ उस्ताद का हाइपर-स्पीड कॉन्फ़िग
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 20
            flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or AccessibilityServiceInfo.DEFAULT
        }
        Log.d(TAG, "✅ स्नाइपर इंजन मुकम्मल! फायरिंग के लिए तैयार।")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            // ✅ फिक्स 1: कंप्यूटर को बता दिया कि लिफ़ाफ़े में SniperTask है
            activeTask = intent.getParcelableExtra(EXTRA_TASK) as? SniperTask
            schedulePreFireCheck()
        }
        return super.onStartCommand(intent, flags, startId)
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
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            
            val exactFireTimeMs = calendar.timeInMillis - EARLY_FIRE_MS

            // अगर बटन दबाते ही टाइम हो चुका है (No Tomorrow Bug)
            if (System.currentTimeMillis() >= exactFireTimeMs) {
                Log.d(TAG, "🎯 फर्स्ट क्लिक! तुरंत हमला शुरू...")
                executeWorkflow()
                return@launch
            }
            
            Log.d(TAG, "⏰ टाइमर लॉक: ठीक 200ms पहले फायर होगा।")
            while (System.currentTimeMillis() < exactFireTimeMs && isActive) { delay(10) }
            
            if (isActive) executeWorkflow()
        }
    }

    // ========================================
    // 🚀 स्टेप 2: एंकर लॉजिक (ट्रेन + क्लास क्लिक)
    // ========================================
    private suspend fun executeWorkflow() = actionMutex.withLock {
        if (isExecuting) return@withLock
        isExecuting = true
        val task = activeTask ?: return@withLock
        
        try {
            // १. फोटो वाला लॉजिक: गाड़ी नंबर और क्लास पर क्लिक
            Log.d(TAG, "🚂 ट्रेन ${task.trainNumber} पर क्लास ${task.travelClass} ढूँढ रहे हैं...")
            findAndClickTrainClass(task.trainNumber, task.travelClass)

            // २. 50ms रडार: पैसेंजर पेज का इंतज़ार
            var pageLoaded = false
            val timeoutMs = System.currentTimeMillis() + 5000L
            while (System.currentTimeMillis() < timeoutMs && isActive) {
                val root = rootInActiveWindow
                if (root != null) {
                    val nodes = root.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_NAME)
                    if (nodes.isNotEmpty()) { pageLoaded = true; SafeRecycle.recycle(root); break }
                    SafeRecycle.recycle(root)
                }
                delay(RADAR_SCAN_MS)
            }

            if (!pageLoaded) return@withLock

            // ३. इंडेक्स-अवेयर फिलिंग (जितना भरा उतना काम)
            val activePassengers = task.passengers.filter { it.isFilled() }
            for ((index, passenger) in activePassengers.withIndex()) {
                if (fillPassengerData(passenger, index)) {
                    if (index < activePassengers.lastIndex) {
                        clickAddPassenger()
                        waitForNewForm(index + 1)
                    }
                }
            }
            
            // ४. पेमेंट गेटवे जंप
            triggerPaymentFlow()
            
        } finally { isExecuting = false }
    }

    // ========================================
    // ✍️ स्टेप 3: डेटा फिलिंग (Index-Aware)
    // ========================================
    // ✅ फिक्स 2: यहाँ suspend लगा दिया ताकि delay काम कर सके
    private suspend fun fillPassengerData(passenger: PassengerData, index: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val names = root.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_NAME)
            val ages = root.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_AGE)
            
            if (names.size > index && ages.size > index) {
                val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, passenger.name) }
                names[index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                delay(FIELD_FILL_DELAY_MS)
                
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, passenger.age)
                ages[index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                true
            } else false
        } finally { SafeRecycle.recycle(root) }
    }

    // ========================================
    // 🚂 ट्रेन कार्ड एंकर लॉजिक (फोटो आधारित)
    // ========================================
    private fun findAndClickTrainClass(trainNo: String, className: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val trainNodes = root.findAccessibilityNodeInfosByText(trainNo)
        if (trainNodes.isEmpty()) return false
        
        var card = trainNodes[0].parent
        repeat(5) { // कार्ड के डब्बे को ढूँढना
            val classes = card?.findAccessibilityNodeInfosByText(className)
            if (!classes.isNullOrEmpty()) {
                classes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            card = card?.parent
        }
        return false
    }

    private suspend fun waitForNewForm(requiredCount: Int) {
        val end = System.currentTimeMillis() + VISIBILITY_TIMEOUT_MS
        while (System.currentTimeMillis() < end) {
            val root = rootInActiveWindow
            val count = root?.findAccessibilityNodeInfosByViewId(IRCTCIds.PASSENGER_NAME)?.size ?: 0
            SafeRecycle.recycle(root)
            if (count >= requiredCount + 1) return
            delay(RADAR_SCAN_MS)
        }
    }

    private fun clickAddPassenger() {
        val root = rootInActiveWindow
        root?.findAccessibilityNodeInfosByViewId(IRCTCIds.ADD_PASSENGER_BTN)?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        SafeRecycle.recycle(root)
    }

    private suspend fun triggerPaymentFlow() {
        delay(200)
        // सीधे पेमेंट बटन और UPI की तरफ जंप
        findAndClickByText(listOf("Review Journey", "Proceed", "Pay Now"))
    }

    private fun findAndClickByText(terms: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.lowercase() ?: ""
            if (terms.any { text.contains(it.lowercase()) } && node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            repeat(node.childCount) { i -> node.getChild(i)?.let { queue.add(it) } }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

