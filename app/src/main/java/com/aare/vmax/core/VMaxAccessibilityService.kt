package com.aare.vmax.core.orchestrator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Calendar

class VMAXAccessibilityService : AccessibilityService() {

    // ═══════════════════════════════════════════════════════
    // ⚙️ CONSTANTS & CONFIG
    // ═══════════════════════════════════════════════════════
    companion object {
        private const val TAG = "VMAX_SERVICE"
        private const val PREFS_NAME = "VMaxProfile"
        private const val ACTION_START = "com.aare.vmax.ACTION_START_AUTOMATION"
        
        // Timing & Safety
        private const val CLICK_DEBOUNCE_MS = 300L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val DEFAULT_LATENCY_MS = 400
        private const val MIN_LATENCY_MS = 50
        private const val MAX_LATENCY_MS = 2000
    }

    // ═══════════════════════════════════════════════════════
    // 🧠 STATE MACHINE
    // ═══════════════════════════════════════════════════════
    enum class AutomationStep {
        IDLE, WAITING_FOR_DASHBOARD, TRAIN_SEARCH,
        PASSENGER_FORM, ADVANCED_OPTIONS,
        REVIEW_CLICKED, PAYMENT_PAGE, COMPLETED, ERROR
    }

    // ═══════════════════════════════════════════════════════
    // 🔧 CORE VARIABLES
    // ═══════════════════════════════════════════════════════
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Runtime State
    private var isBotActive = false
    private var automationStep = AutomationStep.IDLE
    private var passengersProcessed = 0
    private var totalPassengers = 1
    private var retryCount = 0
    private var lastActionTime = 0L
    private var captchaPaused = false
    private var hasClickedReview = false
    private var isCurrentlyFilling = false
    private var hasClickedRefresh = false // ✅ FIX: यह लाइन जुड़ गई है!

    // ═══════════════════════════════════════════════════════
    // 📡 BROADCAST RECEIVER (START Command)
    // ═══════════════════════════════════════════════════════
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_START) {
                startAutomation(intent)
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🔄 LIFECYCLE METHODS
    // ═══════════════════════════════════════════════════════
    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        configureService()
        registerReceiver()
        Log.d(TAG, "✅ VMAX Service Connected & Ready")
    }

    private fun configureService() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                   AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100
            packageNames = arrayOf(
                "cris.org.in.prs.ima",
                "com.irctc.railconnect",
                "in.irctc.railconnect"
            )
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter(ACTION_START)
        ContextCompat.registerReceiver(
            this,
            commandReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isBotActive || event == null || captchaPaused) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        // ✅ Debounce: Prevent rapid duplicate actions
        val now = System.currentTimeMillis()
        if (now - lastActionTime < CLICK_DEBOUNCE_MS && !isCurrentlyFilling) return

        val root = rootInActiveWindow ?: return
        
        try {
            when (automationStep) {
                AutomationStep.WAITING_FOR_DASHBOARD -> handleDashboard(root)
                AutomationStep.TRAIN_SEARCH -> handleTrainSearch(root)
                AutomationStep.PASSENGER_FORM -> handlePassengerForm(root)
                AutomationStep.ADVANCED_OPTIONS -> handleAdvancedOptions(root)
                AutomationStep.REVIEW_CLICKED -> handleReviewStage(root)
                AutomationStep.PAYMENT_PAGE -> handlePaymentPage(root)
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Event handling error: ${e.message}", e)
            handleError("Processing failed: ${e.message?.take(30)}")
        }
    }

    override fun onInterrupt() {
        resetAutomation()
        Log.d(TAG, "⚠️ Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
        scope.cancel()
        resetAutomation()
        Log.d(TAG, "🔌 Service destroyed")
    }

    // ═══════════════════════════════════════════════════════
    // 🚀 START AUTOMATION (Called via Broadcast)
    // ═══════════════════════════════════════════════════════
    private fun startAutomation(intent: Intent) {
        resetAutomation()
        
        totalPassengers = intent.getIntExtra("passengers", 
            prefs.getInt("passenger_count", 1)).coerceIn(1, 6)
        
        isBotActive = true
        automationStep = AutomationStep.WAITING_FOR_DASHBOARD
        
        showToast("🤖 VMAX Active! Target: $totalPassengers passenger(s)")
        Log.d(TAG, "🚀 Automation started: $totalPassengers passenger(s)")
    }

    // ═══════════════════════════════════════════════════════
    // 🧭 STATE HANDLERS (The Brain Logic)
    // ═══════════════════════════════════════════════════════

    private fun handleDashboard(root: AccessibilityNodeInfo) {
        // 🚨 Popup Killer: Remove concession/OK popups
        if (findNodeByText(root, "OK")?.let { node ->
                node.parent?.text?.toString()?.contains("concession", ignoreCase = true) == true
            } == true) {
            clickByText(root, "OK", "🎯 Popup killed")
            return
        }

        // 🎯 Detect IRCTC Dashboard
        if (findNodeByText(root, "Book Ticket") != null || 
            findNodeByText(root, "Train Booking") != null ||
            findNodeByContentDesc(root, "book") != null) {
            
            automationStep = AutomationStep.TRAIN_SEARCH
            retryCount = 0
            showToast("🚂 IRCTC detected. Searching train...")
            Log.d(TAG, "Dashboard detected → Train Search")
        }
    }

    private fun handleTrainSearch(root: AccessibilityNodeInfo) {
        val trainNum = prefs.getString("train", "") ?: ""
        if (trainNum.isEmpty() || trainNum.length != 5) {
            handleError("Invalid train number")
            return
        }
        
        // ⏱️ Sniper Timing: Wait for exact opening second
        val targetClass = prefs.getString("class", "SL") ?: "SL"
        if (!hasClickedRefresh && !shouldWaitForSniperTime(targetClass)) {
            // 🔍 Find train in search results
            val trainNode = findNodeByTextContains(root, trainNum)
            if (trainNode != null) {
                if (clickByContentDesc(root, targetClass, "🎯 Class: $targetClass")) {
                    automationStep = AutomationStep.PASSENGER_FORM
                    passengersProcessed = 0
                    hasClickedRefresh = true
                    retryCount = 0
                    Log.d(TAG, "Train selected → Passenger Form")
                } else {
                    handleRetry("Class selection")
                }
            } else {
                // 🔄 Refresh if train not found
                if (clickByText(root, "Refresh", "🔄 Refreshing results")) {
                    Log.d(TAG, "Refreshed train list")
                } else {
                    handleRetry("Train search")
                }
            }
        }
    }

    private fun shouldWaitForSniperTime(targetClass: String): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        
        val isAC = targetClass in listOf("1A", "2A", "3A", "3E", "CC", "EC")
        val targetHour = if (isAC) 10 else 11  // AC Tatkal: 10 AM, SL Tatkal: 11 AM
        
        // Wait quietly in the last 5 minutes before opening
        return hour == targetHour - 1 && minute >= 55
    }

    private fun handlePassengerForm(root: AccessibilityNodeInfo) {
        // ✅ Smart: Find editable Name field
        val nameField = findEditableNodeByHint(root, "Name") 
                     ?: findEditableNodeByText(root, "Name")
        
        if (nameField != null) {
            if (!isCurrentlyFilling && passengersProcessed < totalPassengers) {
                fillCurrentPassenger(root, nameField)
            }
        } else if (clickByContentDesc(root, "PASSENGER DETAILS", "🎯 Opening passenger section")) {
            Log.d(TAG, "Clicked PASSENGER DETAILS")
        } else if (passengersProcessed == 0) {
            handleRetry("Passenger form detection")
        }
    }

    private fun fillCurrentPassenger(root: AccessibilityNodeInfo, nameField: AccessibilityNodeInfo) {
        isCurrentlyFilling = true
        val index = passengersProcessed.coerceIn(0, 5)  // Support up to 6 passengers
        
        val name = prefs.getString("name_$index", "") ?: ""
        val age = prefs.getString("age_$index", "") ?: "0"
        val gender = prefs.getString("gender_$index", "Male") ?: "Male"
        val meal = prefs.getString("meal_$index", "No Food") ?: "No Food"
        val latency = prefs.getInt("latency_ms", DEFAULT_LATENCY_MS)
            .coerceIn(MIN_LATENCY_MS, MAX_LATENCY_MS).toLong()

        if (name.isEmpty()) {
            // Skip empty passenger slots
            passengersProcessed++
            checkAllPassengersDone()
            isCurrentlyFilling = false
            return
        }

        scope.launch {
            try {
                // 📝 Fill Name
                inputText(nameField, name, "📝 P${index+1}: Name")
                delay(latency)

                // 🔢 Fill Age
                findEditableNodeByHint(root, "Age")?.let {
                    inputText(it, age, "🔢 P${index+1}: Age")
                    delay(latency)
                }

                // 👤 Select Gender
                clickByContentDesc(root, gender, "👤 P${index+1}: Gender")
                delay(latency / 2)

                // 🍴 Smart Meal Selection (Skip if "No Food")
                if (meal != "No Food" && meal.isNotEmpty()) {
                    if (clickByContentDesc(root, "Meal Preference", "🍴 Opening meal options")) {
                        delay(300)  // Wait for dropdown
                        clickByContentDesc(root, meal, "🍴 P${index+1}: $meal")
                        delay(latency / 2)
                    }
                }

                // ✅ Mark passenger as processed
                passengersProcessed++
                Log.d(TAG, "✅ Passenger ${index+1} filled ($passengersProcessed/$totalPassengers)")

                // 🔄 Add next passenger or continue
                if (passengersProcessed < totalPassengers) {
                    if (clickByText(root, "Add Passenger", "➕ Adding next passenger") ||
                        clickByText(root, "+ Add New", "➕ Adding next passenger")) {
                        delay(500)  // Wait for next form to load
                    }
                }
                
                checkAllPassengersDone()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Fill passenger error: ${e.message}", e)
                handleError("Passenger fill failed")
            } finally {
                isCurrentlyFilling = false
                lastActionTime = System.currentTimeMillis()
            }
        }
    }

    private fun checkAllPassengersDone() {
        if (passengersProcessed >= totalPassengers) {
            automationStep = AutomationStep.ADVANCED_OPTIONS
            retryCount = 0
            Log.d(TAG, "All passengers filled → Advanced Options")
        }
    }

    private fun handleAdvancedOptions(root: AccessibilityNodeInfo) {
        // ⬆️ Auto Upgradation
        if (prefs.getBoolean("auto_upgrade", false)) {
            clickByContentDesc(root, "Consider Auto upgradation", "⬆️ Auto-upgrade enabled")
            delayAction(100)
        }

        // ✅ Confirm Only
        if (prefs.getBoolean("confirm_only", false)) {
            clickByContentDesc(root, "Book only if confirm berths", "✅ Confirm-only enabled")
            delayAction(100)
        }

        // 🚀 Click Review Journey
        if (clickByContentDesc(root, "REVIEW JOURNEY", "🚀 Review Journey clicked") ||
            clickByContentDesc(root, "REVIEW", "🚀 Review clicked") ||
            clickByText(root, "Continue", "🚀 Continue clicked")) {
            
            automationStep = AutomationStep.REVIEW_CLICKED
            hasClickedReview = true
            captchaPaused = true  // ⏸️ Pause for manual CAPTCHA
            showToast("⏸️ Bot paused. Solve CAPTCHA manually, then continue booking.")
            Log.d(TAG, "Review clicked → Waiting for CAPTCHA (paused)")
        } else {
            handleRetry("Review button")
        }
    }

    private fun handleReviewStage(root: AccessibilityNodeInfo) {
        // 🔍 Detect if user has solved CAPTCHA and continued
        if (findNodeByTextContains(root, "PAYMENT") != null ||
            findNodeByTextContains(root, "SELECT A PAYMENT") != null ||
            findNodeByText(root, "MAKE PAYMENT") != null) {
            
            automationStep = AutomationStep.PAYMENT_PAGE
            captchaPaused = false  // ▶️ Resume automation
            hasClickedReview = false
            Log.d(TAG, "Payment page detected → Resuming automation")
        }
    }

    private fun handlePaymentPage(root: AccessibilityNodeInfo) {
        val payMethod = prefs.getString("payment_method", "PhonePe") ?: "PhonePe"
        val latency = prefs.getInt("latency_ms", DEFAULT_LATENCY_MS)
            .coerceIn(MIN_LATENCY_MS, MAX_LATENCY_MS).toLong()
        
        scope.launch {
            try {
                // 💳 Select Payment Tab
                when {
                    payMethod.contains("Wallet", true) || payMethod.contains("Mobikwik", true) -> {
                        clickByContentDesc(root, "Wallet", "💸 Wallet tab")
                    }
                    payMethod.contains("UPI", true) || payMethod.contains("PhonePe", true) || 
                    payMethod.contains("Paytm", true) || payMethod.contains("GPay", true) -> {
                        clickByContentDesc(root, "BHIM", "💸 UPI tab")
                        delay(latency)
                        clickByContentDesc(root, "UPI", "💸 UPI option")
                    }
                    else -> {
                        clickByContentDesc(root, "BHIM", "💸 Default UPI tab")
                    }
                }
                delay(latency)

                // 🎯 Select Specific Payment App
                clickByContentDesc(root, payMethod, "🎯 Payment app: $payMethod")
                delay(latency * 2)

                // 🔥 FINAL STRIKE: Click Pay ₹ button
                if (findNodeByTextContains(root, "Pay ₹")?.let { 
                        clickNode(it, "🔥 FINAL STRIKE: Payment initiated!"); true 
                    } == true ||
                    findNodeByTextContains(root, "PROCEED TO PAY")?.let {
                        clickNode(it, "🔥 FINAL STRIKE: Proceed to Pay!"); true
                    } == true) {
                    
                    automationStep = AutomationStep.COMPLETED
                    isBotActive = false
                    showToast("✅ Payment flow started! Complete manually.")
                    Log.d(TAG, "🎉 Automation completed successfully!")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Payment automation error: ${e.message}", e)
                showToast("⚠️ Payment step failed. Complete manually.")
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🛠️ NODE FINDING UTILS (Memory-Safe & Robust)
    // ═══════════════════════════════════════════════════════

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return root.findAccessibilityNodeInfosByText(text)
            .firstOrNull { 
                it.text?.toString().equals(text, ignoreCase = true) && 
                (it.isClickable || it.parent?.isClickable == true)
            }
    }

    private fun findNodeByTextContains(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return root.findAccessibilityNodeInfosByText(text)
            .firstOrNull { 
                it.text?.toString()?.contains(text, ignoreCase = true) == true && 
                (it.isClickable || it.parent?.isClickable == true)
            }
    }

    private fun findNodeByContentDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        return traverseAndFind(root) { node ->
            node.contentDescription?.toString()?.equals(desc, ignoreCase = true) == true && 
            (node.isClickable || node.parent?.isClickable == true)
        }
    }

    private fun findEditableNodeByHint(root: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        return traverseAndFind(root) { node ->
            node.isEditable && (
                node.hintText?.toString()?.contains(hint, ignoreCase = true) == true ||
                node.contentDescription?.toString()?.contains(hint, ignoreCase = true) == true
            )
        }
    }

    private fun findEditableNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return traverseAndFind(root) { node ->
            node.isEditable && node.text?.toString()?.contains(text, ignoreCase = true) == true
        }
    }

    // ✅ Memory-Safe Recursive Traversal with Node Recycling
    private fun traverseAndFind(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        
        if (predicate(node)) {
            return AccessibilityNodeInfo.obtain(node)  // Safe copy
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = traverseAndFind(child, predicate)
            child?.recycle()  // ✅ Critical: Prevent memory leaks
            if (found != null) return found
        }
        return null
    }

    // ═══════════════════════════════════════════════════════
    // ⚡ ACTION EXECUTION (Click + Input)
    // ═══════════════════════════════════════════════════════

    private fun clickByText(root: AccessibilityNodeInfo, text: String, logMsg: String): Boolean {
        return findNodeByText(root, text)?.let { clickNode(it, logMsg); true } ?: false
    }

    private fun clickByContentDesc(root: AccessibilityNodeInfo, desc: String, logMsg: String): Boolean {
        return findNodeByContentDesc(root, desc)?.let { clickNode(it, logMsg); true } ?: false
    }

    private fun clickNode(node: AccessibilityNodeInfo, logMsg: String): Boolean {
        return try {
            var target: AccessibilityNodeInfo? = node
            while (target != null) {
                if (target.isClickable) {
                    val success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) {
                        Log.d(TAG, "✅ $logMsg")
                        lastActionTime = System.currentTimeMillis()
                        return true
                    }
                }
                target = target.parent
            }
            // Fallback: Try focus + click
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            delayAction(100)
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) {
                Log.d(TAG, "✅ $logMsg (fallback)")
                lastActionTime = System.currentTimeMillis()
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Click failed: $logMsg - ${e.message}", e)
            false
        }
    }

    private fun inputText(node: AccessibilityNodeInfo, text: String, logMsg: String = "") {
        try {
            if (node.isEditable) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (success && logMsg.isNotEmpty()) {
                    Log.d(TAG, "✅ $logMsg: '$text'")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Input failed: ${e.message}", e)
        }
    }

    private fun delayAction(ms: Long) {
        lastActionTime = System.currentTimeMillis() + ms
    }

    // ═══════════════════════════════════════════════════════
    // 🔄 ERROR HANDLING & RETRY LOGIC
    // ═══════════════════════════════════════════════════════
    private fun handleRetry(step: String) {
        retryCount++
        if (retryCount < MAX_RETRIES) {
            Log.d(TAG, "🔄 $step retry $retryCount/$MAX_RETRIES")
            scope.launch { delay(RETRY_DELAY_MS) }
        } else {
            Log.e(TAG, "❌ $step failed after $MAX_RETRIES retries")
            handleError("$step failed")
        }
    }

    private fun handleError(message: String) {
        automationStep = AutomationStep.ERROR
        captchaPaused = false
        isBotActive = false
        Log.e(TAG, "🚨 Error: $message")
        showToast("⚠️ Bot paused: $message")
    }

    private fun resetAutomation() {
        isBotActive = false
        automationStep = AutomationStep.IDLE
        passengersProcessed = 0
        totalPassengers = 1
        retryCount = 0
        captchaPaused = false
        hasClickedReview = false
        isCurrentlyFilling = false
        hasClickedRefresh = false // ✅ FIX: इसे भी रीसेट कर दिया
        lastActionTime = 0
        Log.d(TAG, "🔄 Automation reset")
    }

    // ═══════════════════════════════════════════════════════
    // 📢 USER FEEDBACK
    // ═══════════════════════════════════════════════════════
    private fun showToast(message: String) {
        scope.launch {
            try {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w(TAG, "Toast failed: ${e.message}")
            }
        }
    }
}
