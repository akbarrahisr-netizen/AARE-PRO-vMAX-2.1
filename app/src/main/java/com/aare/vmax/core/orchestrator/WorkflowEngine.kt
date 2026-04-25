package com.aare.vmax.core.orchestrator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.model.PassengerData
import com.aare.vmax.core.model.BookingOptions
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

    // 🎯 100% Merged IRCTC IDs (Advanced Booking + Direct Payment)
    private object IRCTCIds {
        const val PASSENGER_NAME = "cris.org.in.prs.ima:id/et_passenger_name"
        const val PASSENGER_AGE = "cris.org.in.prs.ima:id/et_passenger_age"
        const val ADD_PASSENGER_BTN = "cris.org.in.prs.ima:id/btn_add_passenger"
        const val REVIEW_JOURNEY_BTN = "cris.org.in.prs.ima:id/btn_review"
        
        // Advanced Options
        const val AUTO_UPGRADATION = "cris.org.in.prs.ima:id/cb_auto_upgrade"
        const val BOOK_CONFIRM_ONLY = "cris.org.in.prs.ima:id/cb_confirm_only"
        const val TRAVEL_INSURANCE_YES = "cris.org.in.prs.ima:id/rb_insurance_yes"
        const val BOOKING_OPTION_NONE = "cris.org.in.prs.ima:id/rb_booking_none"
        const val BOOKING_OPTION_SAME_COACH = "cris.org.in.prs.ima:id/rb_booking_same_coach"
        const val BOOKING_OPTION_1_LOWER = "cris.org.in.prs.ima:id/rb_booking_1_lower"
        const val BOOKING_OPTION_2_LOWER = "cris.org.in.prs.ima:id/rb_booking_2_lower"
        const val COACH_PREFERRED = "cris.org.in.prs.ima:id/cb_coach_pref"
        const val COACH_ID = "cris.org.in.prs.ima:id/et_coach_id"
        const val MOBILE_NO = "cris.org.in.prs.ima:id/et_mobile"
        
        // 💳 Advanced Payment Methods
        const val PAYMENT_UPI = "cris.org.in.prs.ima:id/payment_upi"
        const val PAYMENT_NETBANKING = "cris.org.in.prs.ima:id/payment_netbanking"
        const val PAYMENT_CARD = "cris.org.in.prs.ima:id/payment_card"
        const val PAYMENT_WALLET = "cris.org.in.prs.ima:id/payment_wallet"
        const val UPI_BHIM = "cris.org.in.prs.ima:id/bhim_upi"
        const val UPI_PHONEPE = "cris.org.in.prs.ima:id/phonepe"
        const val UPI_PAYTM = "cris.org.in.prs.ima:id/paytm_upi"
        const val UPI_CRED = "cris.org.in.prs.ima:id/cred_upi"
        const val WALLET_IRCTC = "cris.org.in.prs.ima:id/irctc_wallet"
        const val WALLET_MOBIKWIK = "cris.org.in.prs.ima:id/mobikwik"
        const val AUTO_FILL_OTP = "cris.org.in.prs.ima:id/cb_autofill_otp"
        const val MANUAL_PAYMENT = "cris.org.in.prs.ima:id/cb_manual_payment"
        const val BOOK_NOW_BTN = "cris.org.in.prs.ima:id/btn_book_now"
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
            
            // ✅ डिफ़ॉल्ट बुकिंग ऑप्शंस
            val bookingOptions = BookingOptions() 

            // 1. Fill passenger details (Merged Logic)
            for ((index, passenger) in activePassengers.withIndex()) {
                fillPassengerComplete(passenger, index, bookingOptions)
                if (index < activePassengers.lastIndex) {
                    clickAddPassenger()
                    waitForNewForm(index + 1)
                }
            }
            
            // 2. Apply advanced options
            applyBookingOptions(bookingOptions)
            
            // 3. Navigate to payment (Smart Direct IDs)
            triggerPaymentFlow(bookingOptions)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Execution error", e)
        } finally {
            isExecuting = false
            textActionBundle.clear()
            if (lowercaseCache.size > 100) lowercaseCache.clear()
        }
    }

    // ========================================
    // ✍️ 100% MERGED: COMPLETE PASSENGER FILLING
    // ========================================
    private suspend fun fillPassengerComplete(
        passenger: PassengerData,
        index: Int,
        options: BookingOptions
    ) {
        val root = rootInActiveWindow ?: return
        try {
            fillFieldById(root, IRCTCIds.PASSENGER_NAME, passenger.name, index)
            delay(FIELD_FILL_DELAY_MS)
            fillFieldById(root, IRCTCIds.PASSENGER_AGE, passenger.age, index)
            
            selectDropdown("Gender", passenger.gender)
            
            if (passenger.berthPreference != "No Preference") {
                selectDropdown("Berth Preference", passenger.berthPreference)
            }
            
            if (passenger.meal != "No Food") {
                selectDropdown("Meal", passenger.meal)
            }
            
            if (passenger.optBerth) findAndClickByText(listOf("Opt Berth"))
            if (passenger.bedRoll) findAndClickByText(listOf("Bed Roll"))
            if (passenger.availConcession) findAndClickByText(listOf("Concession"))
            
        } finally {
            SafeRecycle.recycle(root)
        }
    }

    // ========================================
    // ⚙️ ADVANCED BOOKING OPTIONS
    // ========================================
    private suspend fun applyBookingOptions(options: BookingOptions) {
        val root = rootInActiveWindow ?: return
        try {
            if (options.considerAutoUpgradation) clickById(root, IRCTCIds.AUTO_UPGRADATION)
            if (options.bookOnlyIfConfirm) clickById(root, IRCTCIds.BOOK_CONFIRM_ONLY)
            if (options.travelInsurance) clickById(root, IRCTCIds.TRAVEL_INSURANCE_YES)
            
            when (options.bookingOption) {
                "Same Coach" -> clickById(root, IRCTCIds.BOOKING_OPTION_SAME_COACH)
                "1 Lower" -> clickById(root, IRCTCIds.BOOKING_OPTION_1_LOWER)
                "2 Lower" -> clickById(root, IRCTCIds.BOOKING_OPTION_2_LOWER)
            }
            
            if (options.coachPreferred && options.coachId.isNotBlank()) {
                clickById(root, IRCTCIds.COACH_PREFERRED)
                fillFieldById(root, IRCTCIds.COACH_ID, options.coachId, 0)
            }
            
            if (options.mobileNo.isNotBlank()) {
                fillFieldById(root, IRCTCIds.MOBILE_NO, options.mobileNo, 0)
            }
        } finally {
            SafeRecycle.recycle(root)
        }
    }

    // ========================================
    // 💳 SMART PAYMENT FLOW (Merged specific IDs)
    // ========================================
    private suspend fun triggerPaymentFlow(options: BookingOptions) {
        delay(200)
        val root = rootInActiveWindow ?: return
        
        try {
            // 1. Select payment category
            when (options.paymentMethod) {
                "UPI" -> clickById(root, IRCTCIds.PAYMENT_UPI)
                "Netbanking", "Net Banking" -> clickById(root, IRCTCIds.PAYMENT_NETBANKING)
                "Card", "Credit/Debit Cards" -> clickById(root, IRCTCIds.PAYMENT_CARD)
                "e-Wallet", "e-Wallets" -> clickById(root, IRCTCIds.PAYMENT_WALLET)
            }
            
            delay(300)
            
            // 2. Select specific UPI app
            if (options.paymentMethod == "UPI") {
                when (options.upiId) { // upiId को App का नाम मानकर चल रहे हैं
                    "BHIM UPI" -> clickById(root, IRCTCIds.UPI_BHIM)
                    "PhonePe" -> clickById(root, IRCTCIds.UPI_PHONEPE)
                    "Paytm" -> clickById(root, IRCTCIds.UPI_PAYTM)
                    "CRED UPI" -> clickById(root, IRCTCIds.UPI_CRED)
                    else -> findAndClickByText(listOf("BHIM/UPI", "UPI apps")) // Fallback
                }
            }
            
            // 3. Select e-Wallet
            if (options.paymentMethod == "e-Wallet" || options.paymentMethod == "e-Wallets") {
                when {
                    options.upiId.contains("IRCTC", ignoreCase = true) -> clickById(root, IRCTCIds.WALLET_IRCTC)
                    options.upiId.contains("MobiKwik", ignoreCase = true) -> clickById(root, IRCTCIds.WALLET_MOBIKWIK)
                }
            }
            
            // 4. Auto-fill OTP & Manual Payment Toggles
            if (options.autofillOTP) {
                clickById(root, IRCTCIds.AUTO_FILL_OTP)
            }
            if (options.manualPayment) {
                clickById(root, IRCTCIds.MANUAL_PAYMENT)
            }
            
            // 5. Final submission
            delay(500)
            val clickedDirectly = clickById(root, IRCTCIds.BOOK_NOW_BTN)
            
            // अगर डायरेक्ट ID से बुक बटन नहीं मिला, तो टेक्स्ट से ढूँढेगा (Safety Fallback)
            if (!clickedDirectly) {
                findAndClickByText(listOf("Review Journey", "Proceed", "Pay Now", "Book Now"))
            }
            
        } finally {
            SafeRecycle.recycle(root)
        }
    }

    // ========================================
    // 🛠️ HELPER FUNCTIONS (Optimized & Safe)
    // ========================================
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
