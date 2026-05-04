package com.vmax.sniper.core.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.vmax.sniper.core.model.*
import com.vmax.sniper.core.network.TimeSniper
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.random.Random

/**
 * VMAX SNIPER - Tatkal Booking Engine
 * वॉर रेडी | 100% बुलेटप्रूफ | सुपरफास्ट
 */
class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Sniper"
        const val ACTION_START_SNIPER = "com.vmax.sniper.START_SNIPER"
        const val EXTRA_TASK = "extra_task"
        const val ACTION_SERVICE_STOPPED = "com.vmax.sniper.SERVICE_STOPPED"
        
        object IRCTC {
            const val PKG = "cris.org.in.prs.ima"
            const val NAME_INPUT = "$PKG:id/et_passenger_name"
            const val AGE_INPUT = "$PKG:id/et_passenger_age"
            const val GENDER_SPINNER = "$PKG:id/et_gender"
            const val BERTH_SPINNER = "$PKG:id/et_berth_preference"
            const val MEAL_SPINNER = "$PKG:id/et_meal"
            const val CHILD_NAME = "$PKG:id/et_child_name"
            const val CHILD_AGE = "$PKG:id/spinner_child_age"
            const val CHILD_GENDER = "$PKG:id/spinner_child_gender"
            const val ADD_PASSENGER_BTN = "$PKG:id/tv_add_passanger"
            const val ADD_CHILD_BTN = "$PKG:id/btn_add_child"
            const val SAVE_BTN = "$PKG:id/btn_save"
            const val PROCEED_BTN = "$PKG:id/btn_proceed"
            const val BOOK_NOW_BTN = "$PKG:id/btn_book_now"
            const val SEARCH_BTN = "$PKG:id/btn_search_trains"
            const val DATE_INPUT = "$PKG:id/jDate"
            const val CAL_MONTH_TEXT = "$PKG:id/month_year"
            const val CAL_NEXT_MONTH = "$PKG:id/btn_next_month"
            const val CAL_OK_BTN = "$PKG:id/btn_ok"
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_EWALLET = "$PKG:id/radio_ewallet"
            const val PAYMENT_UPI_ID = "$PKG:id/radio_upi_id"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            const val UPI_ID_INPUT = "$PKG:id/et_upi_id"
            const val AUTO_UPGRADE_CHECK = "$PKG:id/checkbox_auto_upgrade"
            const val CONFIRM_BERTH_CHECK = "$PKG:id/checkbox_confirm_berth"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
            const val BOOKING_OPT_SPINNER = "$PKG:id/spinner_booking_option"
            const val COACH_PREF_INPUT = "$PKG:id/et_coach_preference"
            const val MOBILE_INPUT = "$PKG:id/et_mobile_number"
        }
    }

    // ==================== MEMORY SAFE VARIABLES ====================
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var isProcessing = false
    private var hasRefreshed = false
    private var currentPassengerIndex = 0
    private var watchdogJob: Job? = null
    private var isReviewClicked = false
    private var lastActionTime = 0L
    private val clipboard: ClipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private var captchaRetryCount = 0

    // ==================== TATKAL OPTIMIZED DELAYS ====================
    private suspend fun fastDelay() = delay(Random.nextLong(15, 30))
    private suspend fun mediumDelay() = delay(Random.nextLong(30, 50))
    private suspend fun addDelay() = delay(Random.nextLong(25, 40))

    // ==================== SERVICE LIFECYCLE ====================
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 10
        }
        createNotificationChannel()
        startForeground(1, buildNotification("⚡ VMAX Sniper Ready"))
        Log.d(TAG, "✅ Service Connected - WAR READY")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SNIPER) {
            activeTask = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_TASK, SniperTask::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_TASK)
            }
            if (activeTask != null) {
                resetEngineState()
                val targetHour = if (activeTask!!.triggerTime.startsWith("10")) 10 else 11
                val advanceMs = activeTask!!.msAdvance.toLong().coerceIn(100, 250)
                TimeSniper.scheduleFire(targetHour, advanceMs) {
                    isArmed = true
                    serviceScope.launch {
                        if (!hasRefreshed) {
                            triggerPreciseRefresh()
                            hasRefreshed = true
                        }
                    }
                }
                updateNotification("⏳ Waiting for ${activeTask!!.triggerTime}")
            }
        }
        return START_STICKY
    }

    private fun resetEngineState() {
        isArmed = false
        hasRefreshed = false
        isReviewClicked = false
        isProcessing = false
        currentPassengerIndex = 0
        captchaRetryCount = 0
        watchdogJob?.cancel()
        lastActionTime = 0
        Log.d(TAG, "🔄 Engine State Reset")
    }

    private fun triggerPreciseRefresh() {
        val root = rootInActiveWindow ?: return
        try {
            val refreshBtn = findNodeFast(root, listOf("Search", "Refresh"), IRCTC.SEARCH_BTN)
            if (refreshBtn?.isClickable == true) {
                humanClickFast(refreshBtn)
                Log.d(TAG, "🔄 Refresh Triggered")
            }
        } finally { 
            root.recycle() 
        }
    }

    // ==================== 🔍 NODE SEARCH ====================
    fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        for (label in labels) {
            root.findAccessibilityNodeInfosByText(label).firstOrNull { 
                it.text?.toString()?.trim().equals(label, ignoreCase = true) && it.isVisibleToUser 
            }?.let { return it }
        }
        return findNodeRecursive(root, labels.map { it.uppercase() }, 0)
    }

    private fun findNodeRecursive(node: AccessibilityNodeInfo?, targetTexts: List<String>, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 8) return null
        
        val nodeText = node.text?.toString()?.trim()?.uppercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.trim()?.uppercase() ?: ""
        
        if (targetTexts.any { nodeText.contains(it) || nodeDesc.contains(it) }) {
            if (node.isVisibleToUser) return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, targetTexts, depth + 1)
            if (found != null) return found
        }
        return null
    }

    // ==================== 🎯 MAIN EVENT HANDLER (वॉर रेडी) ====================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < 100) return
        lastActionTime = now
        
        if (!isArmed || isProcessing || event == null) return
        
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && 
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            
        val packageName = event.packageName?.toString() ?: return
        if (packageName != IRCTC.PKG) return
        
        serviceScope.launch {
            isProcessing = true
            var root: AccessibilityNodeInfo? = null
            try {
                root = rootInActiveWindow ?: return@launch
                
                // 1. सबसे पहले किसी भी फालतू Popup को हटाओ
                if (handlePopups(root)) return@launch
                
                // 2. ✅ कैप्चा स्क्रीन का स्मार्ट डिटेक्शन (Retry capable)
                if (isReviewClicked) {
                    val captchaImage = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE).firstOrNull()
                    val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT).firstOrNull()
                    
                    if (captchaImage != null && captchaInput != null && activeTask?.captchaAutofill == true) {
                        if (captchaInput.text.isNullOrBlank()) {
                            Log.d(TAG, "🔐 Solving Captcha... (Attempt: ${captchaRetryCount + 1})")
                            CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage, captchaInput)
                            captchaRetryCount++
                            return@launch
                        } else {
                            // Captcha already filled, just verify
                            captchaRetryCount = 0
                        }
                    }
                }

                // 3. रिफ्रेश के बाद क्लास चुनना
                if (hasRefreshed && findNodeFast(root, listOf("SL", "3A", "2A", "1A"), "") != null && !isReviewClicked) {
                    selectSpecificClassAfterRefresh()
                    findPassengerFormAndFill()
                    return@launch
                }

                // 4. पैसेंजर फॉर्म भरना
                if (!isReviewClicked && findNodeFast(root, listOf("Name"), IRCTC.NAME_INPUT) != null) {
                    fillAllDetailsSuperFast()
                    return@launch
                }
                
                // 5. पेमेंट सेलेक्ट करना
                if (root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS).isNotEmpty()) {
                    isReviewClicked = false  // ✅ Payment screen - smart switch OFF
                    selectPaymentFast(root)
                    return@launch
                }
                
                // 6. फाइनल पेमेंट बटन
                val payBtn = findNodeFast(root, listOf("PAY", "PROCEED", "CONTINUE", "भुगतान"), IRCTC.PROCEED_BTN)
                payBtn?.let {
                    humanClickFast(it)
                    updateNotification("✅ Booking Submitted!")
                    isArmed = false
                    LocalBroadcastManager.getInstance(this@WorkflowEngine).sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Event Error: ${e.message}")
            } finally {
                isProcessing = false
                try { root?.recycle() } catch (e: Exception) { Log.e(TAG, "Recycle error: ${e.message}") }
            }
        }
    }

    // ✅ SMART POPUP HANDLER (Confirm + I Agree)
    private suspend fun handlePopups(root: AccessibilityNodeInfo): Boolean {
        val okButton = findNodeFast(root, listOf("OK", "ठीक है", "YES", "हाँ", "CONFIRM", "I AGREE"), "")
        if (okButton != null && okButton.isClickable) {
            humanClickFast(okButton)
            fastDelay()
            Log.d(TAG, "🔘 Popup Bypassed Automatically")
            return true
        }
        return false
    }

    private suspend fun findPassengerFormAndFill() {
        repeat(6) {
            val root = rootInActiveWindow
            if (root != null) {
                try {
                    if (findNodeFast(root, listOf("Name"), IRCTC.NAME_INPUT) != null) {
                        fillAllDetailsSuperFast()
                        return
                    }
                } finally { root.recycle() }
            }
            delay(60)
        }
    }

    private suspend fun fillAllDetailsSuperFast() {
        val task = activeTask ?: return
        var currentRoot = rootInActiveWindow ?: return
        
        try {
            // ✅ IRCTC allows max 4 passengers in Tatkal
            val maxPassengers = min(task.passengers.size, 4)
            
            for (i in currentPassengerIndex until maxPassengers) {
                val passenger = task.passengers[i]
                
                if (i > 0) {
                    findNodeFast(currentRoot, listOf("Add Passenger", "Add New"), IRCTC.ADD_PASSENGER_BTN)?.let {
                        humanClickFast(it)
                        addDelay()
                        currentRoot = rootInActiveWindow ?: return
                    }
                }
                
                findNodeFast(currentRoot, listOf("Name"), IRCTC.NAME_INPUT)?.let {
                    if (it.text.isNullOrBlank()) setTextFast(it, passenger.name)
                    fastDelay()
                }
                
                findNodeFast(currentRoot, listOf("Age"), IRCTC.AGE_INPUT)?.let {
                    if (it.text.isNullOrBlank()) setTextFast(it, passenger.age)
                    fastDelay()
                }
                
                if (passenger.gender.isNotBlank()) {
                    selectPopupOption(IRCTC.GENDER_SPINNER, passenger.gender)
                }
                
                if (i > 0) {
                    findNodeFast(currentRoot, listOf("Save"), IRCTC.SAVE_BTN)?.let {
                        humanClickFast(it)
                        addDelay()
                        currentRoot = rootInActiveWindow ?: return
                    }
                }
                currentPassengerIndex = i + 1
            }
            
            for (child in task.children) {
                findNodeFast(currentRoot, listOf("Add Infant"), IRCTC.ADD_CHILD_BTN)?.let {
                    humanClickFast(it)
                    addDelay()
                    currentRoot = rootInActiveWindow ?: return
                }
                findNodeFast(currentRoot, listOf("Infant Name"), IRCTC.CHILD_NAME)?.let {
                    if (it.text.isNullOrBlank()) setTextFast(it, child.name)
                    fastDelay()
                }
            }
            
            if (task.journeyDate.isNotBlank()) {
                selectDateWithCalendar(task.journeyDate)
            }
            
            mediumDelay()
            currentRoot = rootInActiveWindow ?: return
            findNodeFast(currentRoot, listOf("Review Journey", "Continue"), IRCTC.PROCEED_BTN)?.let {
                humanClickFast(it)
                isReviewClicked = true  // ✅ स्मार्ट स्विच ON
                startWatchdog()
                updateNotification("📋 Review Page - Ready for Captcha")
            }
        } finally {
            currentRoot.recycle()
        }
    }

    private suspend fun selectDateWithCalendar(targetDate: String) {
        val parts = targetDate.split("-")
        val day = parts[0].toInt().toString()
        val monthNum = parts[1].toInt()
        
        val monthNames = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", 
                                 "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val targetMonth = monthNames[monthNum - 1]
        
        var root = rootInActiveWindow ?: return
        try {
            val dateField = findNodeFast(root, emptyList(), IRCTC.DATE_INPUT) ?: return
            humanClickFast(dateField)
            mediumDelay()
        } finally { root.recycle() }
        
        repeat(3) {
            val calRoot = rootInActiveWindow ?: return@repeat
            try {
                val currentMonthYear = findNodeFast(calRoot, emptyList(), IRCTC.CAL_MONTH_TEXT)?.text?.toString()?.uppercase() ?: ""
                
                if (currentMonthYear.contains(targetMonth)) {
                    val dayNode = findNodeFast(calRoot, listOf(day), "")
                    dayNode?.let {
                        humanClickFast(it)
                        delay(80)
                        findNodeFast(calRoot, emptyList(), IRCTC.CAL_OK_BTN)?.let { ok ->
                            humanClickFast(ok)
                        }
                    }
                    return
                } else {
                    findNodeFast(calRoot, emptyList(), IRCTC.CAL_NEXT_MONTH)?.let { next ->
                        humanClickFast(next)
                        delay(100)
                    }
                }
            } finally { calRoot.recycle() }
        }
    }

    private suspend fun selectPopupOption(spinnerId: String, optionText: String): Boolean {
        var root = rootInActiveWindow ?: return false
        try {
            val spinner = findNodeFast(root, emptyList(), spinnerId) ?: return false
            humanClickFast(spinner)
            mediumDelay()
        } finally { root.recycle() }
        
        repeat(4) {
            root = rootInActiveWindow ?: return false
            try {
                findNodeFast(root, listOf(optionText), "")?.let {
                    humanClickFast(it)
                    mediumDelay()
                    return true
                }
            } finally { root.recycle() }
            delay(80)
        }
        return false
    }

    private suspend fun selectSpecificClassAfterRefresh() {
        val task = activeTask ?: return
        repeat(20) {
            val root = rootInActiveWindow ?: return@repeat
            try {
                findNodeFast(root, listOf(task.travelClass.code), "")?.let { classNode ->
                    if (classNode.isClickable) {
                        humanClickFast(classNode)
                        delay(30)
                        findNodeFast(root, listOf("Book Now", "अभी बुक करें"), IRCTC.BOOK_NOW_BTN)?.let { 
                            humanClickFast(it)
                        }
                        return
                    }
                }
            } finally { root.recycle() }
            delay(30)
        }
    }

    private suspend fun selectPaymentFast(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        watchdogJob?.cancel()
        
        try {
            when (task.payment.category) {
                PaymentCategory.CARDS_NETBANKING -> findNodeFast(root, emptyList(), IRCTC.PAYMENT_CARDS)?.let { humanClickFast(it) }
                PaymentCategory.BHIM_UPI -> findNodeFast(root, emptyList(), IRCTC.PAYMENT_BHIM_UPI)?.let { humanClickFast(it) }
                PaymentCategory.E_WALLETS -> findNodeFast(root, emptyList(), IRCTC.PAYMENT_EWALLET)?.let { humanClickFast(it) }
                PaymentCategory.UPI_ID -> findNodeFast(root, emptyList(), IRCTC.PAYMENT_UPI_ID)?.let { humanClickFast(it) }
                PaymentCategory.UPI_APPS -> findNodeFast(root, emptyList(), IRCTC.PAYMENT_UPI_APPS)?.let { humanClickFast(it) }
                else -> findNodeFast(root, emptyList(), IRCTC.PAYMENT_CARDS)?.let { humanClickFast(it) }
            }
        } finally { root.recycle() }
        
        mediumDelay()
        val newRoot = rootInActiveWindow ?: return
        try {
            findNodeFast(newRoot, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)?.let { 
                humanClickFast(it)
                updateNotification("💳 Payment Selected")
            }
        } finally { newRoot.recycle() }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            delay(4000)
            if (isReviewClicked && !isProcessing) {
                rootInActiveWindow?.let { root ->
                    try {
                        findNodeFast(root, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)?.let { 
                            humanClickFast(it)
                            Log.d(TAG, "🛡️ Watchdog activated")
                        }
                    } finally { root.recycle() }
                }
            }
        }
    }

    // ==================== 🖱️ ACTIONS (PUBLIC FOR CAPTCHA) ====================
    fun humanClickFast(node: AccessibilityNodeInfo?) {
        var target = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }

        target?.let { clickableNode ->
            val bounds = Rect()
            clickableNode.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, Random.nextLong(15, 30)))
                    .build()
                dispatchGesture(gesture, null, null)
            }
        }
    }

    fun setTextFast(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { 
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) 
        }
        val setTextSuccess = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        
        if (!setTextSuccess) {
            clipboard.setPrimaryClip(ClipData.newPlainText("vmax_sniper", text))
            
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
            
            val longPressGesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
                .build()
            dispatchGesture(longPressGesture, null, null)
            
            serviceScope.launch {
                delay(100)
                rootInActiveWindow?.let { root ->
                    try {
                        findNodeFast(root, listOf("Paste", "पेस्ट", "PASTE"), "")?.let { pasteBtn ->
                            humanClickFast(pasteBtn)
                        }
                    } finally {
                        root.recycle()
                    }
                }
            }
        }
    }

    // ==================== 🔔 NOTIFICATIONS ====================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 VMAX Sniper")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun updateNotification(message: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(1, buildNotification(message))
            Log.d(TAG, message)
        } catch (e: Exception) {
            Log.e(TAG, "Notification error: ${e.message}")
        }
    }

    override fun onInterrupt() { 
        resetEngineState() 
        Log.d(TAG, "⏸️ Service Interrupted")
    }
    
    override fun onDestroy() { 
        watchdogJob?.cancel()
        serviceScope.cancel() 
        super.onDestroy()
        Log.d(TAG, "💀 Service Destroyed")
    }
}

// ==================== 📱 HELPER FUNCTION ====================
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains(expected.flattenToString())
}
