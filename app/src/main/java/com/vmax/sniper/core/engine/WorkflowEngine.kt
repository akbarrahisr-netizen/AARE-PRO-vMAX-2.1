package com.vmax.sniper.core.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.vmax.sniper.core.model.*
import com.vmax.sniper.core.network.TimeSniper
import com.vmax.sniper.core.network.TimeSyncManager
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.random.Random

class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Sniper"
        const val ACTION_START_SNIPER = "com.vmax.sniper.START_SNIPER"
        const val EXTRA_TASK = "extra_task"
        const val ACTION_SERVICE_STOPPED = "com.vmax.sniper.SERVICE_STOPPED"
        
        object IRCTC {
            const val PKG = "cris.org.in.prs.ima"
            
            // Input Fields
            const val NAME_INPUT = "$PKG:id/et_passenger_name"
            const val AGE_INPUT = "$PKG:id/et_passenger_age"
            const val GENDER_SPINNER = "$PKG:id/et_gender"
            const val BERTH_SPINNER = "$PKG:id/et_berth_preference"
            const val MEAL_SPINNER = "$PKG:id/et_meal"
            
            // Child Fields
            const val CHILD_NAME = "$PKG:id/et_child_name"
            const val CHILD_AGE = "$PKG:id/spinner_child_age"
            const val CHILD_GENDER = "$PKG:id/spinner_child_gender"
            
            // Buttons
            const val ADD_PASSENGER_BTN = "$PKG:id/tv_add_passanger"
            const val ADD_CHILD_BTN = "$PKG:id/btn_add_child"
            const val SAVE_BTN = "$PKG:id/btn_save"
            const val PROCEED_BTN = "$PKG:id/btn_proceed"
            const val BOOK_NOW_BTN = "$PKG:id/btn_book_now"
            const val SEARCH_BTN = "$PKG:id/btn_search_trains"
            
            // Calendar
            const val DATE_INPUT = "$PKG:id/jDate"
            const val CAL_MONTH_TEXT = "$PKG:id/month_year"
            const val CAL_NEXT_MONTH = "$PKG:id/btn_next_month"
            const val CAL_OK_BTN = "$PKG:id/btn_ok"
            
            // Captcha
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            
            // Payment
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_EWALLET = "$PKG:id/radio_ewallet"
            const val PAYMENT_UPI_ID = "$PKG:id/radio_upi_id"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            const val UPI_ID_INPUT = "$PKG:id/et_upi_id"
            
            // Advanced Options
            const val AUTO_UPGRADE_CHECK = "$PKG:id/checkbox_auto_upgrade"
            const val CONFIRM_BERTH_CHECK = "$PKG:id/checkbox_confirm_berth"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
            const val BOOKING_OPT_SPINNER = "$PKG:id/spinner_booking_option"
            const val COACH_PREF_INPUT = "$PKG:id/et_coach_preference"
            const val MOBILE_INPUT = "$PKG:id/et_mobile_number"
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var isProcessing = false
    private var hasRefreshed = false
    private var currentPassengerIndex = 0
    private var watchdogJob: Job? = null
    private var isReviewClicked = false
    private var lastActionTime = 0L

    // Optimized delays for Tatkal
    private suspend fun fastDelay() = delay(Random.nextLong(30, 60))
    private suspend fun mediumDelay() = delay(Random.nextLong(80, 150))
    private suspend fun addDelay() = delay(Random.nextLong(60, 100))

    // ==================== WAIT FOR NODE ====================
    private suspend fun waitForNode(viewId: String, timeoutMs: Long = 5000): AccessibilityNodeInfo? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow ?: continue
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty() && nodes[0].isVisibleToUser) {
                return nodes[0]
            }
            root.recycle()
            delay(100)
        }
        return null
    }

    // ==================== POPUP SELECTION ====================
    private suspend fun selectPopupOption(spinnerId: String, optionText: String): Boolean {
        var root = rootInActiveWindow ?: return false
        val spinner = findNodeFast(root, emptyList(), spinnerId) ?: return false
        
        humanClickFast(spinner)
        mediumDelay()
        
        var retry = 0
        while (retry < 6) {
            root = rootInActiveWindow ?: return false
            val optionNode = findNodeFast(root, listOf(optionText), "")
            if (optionNode != null) {
                humanClickFast(optionNode)
                mediumDelay()
                Log.d(TAG, "Selected: $optionText")
                return true
            }
            delay(150)
            retry++
        }
        return false
    }

    // ==================== CALENDAR SELECTION ====================
    private suspend fun selectDateWithCalendar(targetDate: String) {
        val parts = targetDate.split("-")
        val day = parts[0].toInt().toString()
        val monthNum = parts[1].toInt()
        
        val monthNames = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", 
                                 "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val targetMonth = monthNames[monthNum - 1]
        
        var root = rootInActiveWindow ?: return
        val dateField = findNodeFast(root, emptyList(), IRCTC.DATE_INPUT) ?: return
        humanClickFast(dateField)
        mediumDelay()
        
        root = rootInActiveWindow ?: return
        repeat(4) {
            val calRoot = rootInActiveWindow ?: return@repeat
            val currentMonthYear = findNodeFast(calRoot, emptyList(), IRCTC.CAL_MONTH_TEXT)?.text?.toString()?.uppercase() ?: ""
            
            if (currentMonthYear.contains(targetMonth)) {
                val dayNode = findNodeFast(calRoot, listOf(day), "")
                dayNode?.let {
                    humanClickFast(it)
                    delay(150)
                    findNodeFast(calRoot, emptyList(), IRCTC.CAL_OK_BTN)?.let { ok ->
                        humanClickFast(ok)
                        Log.d(TAG, "Date selected: $targetDate")
                    }
                }
                calRoot.recycle()
                return
            } else {
                findNodeFast(calRoot, emptyList(), IRCTC.CAL_NEXT_MONTH)?.let { next ->
                    humanClickFast(next)
                    delay(250)
                }
            }
            calRoot.recycle()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 10
        }
        createNotificationChannel()
        startForeground(1, buildNotification("VMAX Sniper Ready"))
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
                updateNotification("Waiting for ${activeTask!!.triggerTime}")
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
        watchdogJob?.cancel()
        lastActionTime = 0
    }

    // ==================== REFRESH WITH WAIT ====================
    private fun triggerPreciseRefresh() {
        val root = rootInActiveWindow ?: return
        try {
            val refreshBtn = findNodeFast(root, listOf("Search", "Refresh"), IRCTC.SEARCH_BTN)
            if (refreshBtn?.isClickable == true) humanClickFast(refreshBtn)
        } finally { root.recycle() }
    }

    private suspend fun selectSpecificClassAfterRefresh() {
        val task = activeTask ?: return
        
        var classNode: AccessibilityNodeInfo? = null
        repeat(20) {
            if (classNode != null) return@repeat
            val root = rootInActiveWindow ?: return@repeat
            classNode = findNodeFast(root, listOf(task.travelClass.code), "")
            root.recycle()
            delay(150)
        }
        
        classNode?.let {
            if (it.isClickable) {
                humanClickFast(it)
                mediumDelay()
                findNodeFast(rootInActiveWindow ?: return, listOf("Book Now", "अभी बुक करें"), IRCTC.BOOK_NOW_BTN)?.let { btn ->
                    humanClickFast(btn)
                }
            }
        }
    }

    // ==================== onAccessibilityEvent with Event Filtering ====================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Battle-Proof: Event Filtering
        if (!isArmed || isProcessing || event == null) return
        
        // Only process relevant event types
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && 
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        
        // Only process IRCTC package
        val packageName = event.packageName?.toString() ?: return
        if (packageName != IRCTC.PKG) return
        
        val eventRoot = rootInActiveWindow ?: return

        serviceScope.launch {
            isProcessing = true
            var root: AccessibilityNodeInfo? = null
            try {
                root = rootInActiveWindow ?: return@launch
                
                if (handlePopups(root)) return@launch

                if (hasRefreshed && findNodeFast(root, listOf("SL", "3A"), "") != null && !isReviewClicked) {
                    selectSpecificClassAfterRefresh()
                    findPassengerFormAndFill()
                    return@launch
                }

                if (!isReviewClicked && findNodeFast(root, listOf("Name"), IRCTC.NAME_INPUT) != null) {
                    fillAllDetailsSuperFast()
                    return@launch
                }
                
                if (root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS).isNotEmpty()) {
                    selectPaymentFast(root)
                    return@launch
                }
                
                val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
                if (captchaInput.isNotEmpty() && activeTask?.captchaAutofill == true) {
                    val captchaImage = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE)
                    if (captchaImage.isNotEmpty()) {
                        if (isCaptchaImageLoaded(captchaImage[0])) {
                            CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage[0], captchaInput[0])
                        } else {
                            Log.w(TAG, "Captcha image not loaded yet, waiting...")
                            delay(500)
                            if (captchaImage[0].isVisibleToUser) {
                                CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage[0], captchaInput[0])
                            }
                        }
                    }
                    return@launch
                }
                
                val payBtn = findNodeFast(root, listOf("PAY", "PROCEED", "CONTINUE", "PROCEED TO PAY", "भुगतान"), IRCTC.PROCEED_BTN)
                payBtn?.let {
                    humanClickFast(it)
                    updateNotification("Booking Submitted!")
                    isArmed = false
                    android.support.v4.content.LocalBroadcastManager.getInstance(this@WorkflowEngine).sendBroadcast(
                        Intent(ACTION_SERVICE_STOPPED)
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Event Error: ${e.message}")
            } finally {
                isProcessing = false
                try { root?.recycle() } catch (e: Exception) {}
                try { eventRoot.recycle() } catch (e: Exception) {}
            }
        }
    }

    private fun isCaptchaImageLoaded(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds.width() > 50 && bounds.height() > 20 && (node.contentDescription != null || node.isVisibleToUser)
    }

    private suspend fun handlePopups(root: AccessibilityNodeInfo): Boolean {
        val okButton = findNodeFast(root, listOf("OK", "ठीक है", "YES", "हाँ"), "")
        if (okButton != null && okButton.isClickable) {
            val parentText = okButton.parent?.text?.toString()?.lowercase() ?: ""
            if (parentText.contains("session") && parentText.contains("expired")) {
                Log.w(TAG, "Session Expired Detected! Alerting user...")
                vibrateDevice()
                updateNotification("Session Expired! Please login manually.")
                return true
            }
            humanClickFast(okButton)
            fastDelay()
            return true
        }
        return false
    }

    private fun vibrateDevice() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(500)
        }
    }

    // ==================== WATCHDOG ====================
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            var retryCount = 0
            val startTime = System.currentTimeMillis()
            val globalTimeout = 15000L
            
            while (isArmed && isReviewClicked && (System.currentTimeMillis() - startTime) < globalTimeout) {
                val root = rootInActiveWindow
                if (root != null && root.packageName == IRCTC.PKG) {
                    val isReviewPage = findNodeFast(root, listOf("Review Journey", "Review"), "") != null
                    
                    if (isReviewPage) {
                        val proceedBtn = findNodeFast(root, listOf("Continue", "अभी बुक करें"), IRCTC.PROCEED_BTN)
                        if (proceedBtn?.isVisibleToUser == true && retryCount < 3) {
                            retryCount++
                            humanClickFast(proceedBtn)
                            delay(150)
                        }
                    } else {
                        retryCount = 0
                    }
                    
                    if (root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS).isNotEmpty()) {
                        isReviewClicked = false
                        isProcessing = true
                        selectPaymentFast(root)
                        isProcessing = false
                        watchdogJob?.cancel()
                        return@launch
                    }
                    
                    val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
                    if (captchaInput.isNotEmpty() && activeTask?.captchaAutofill == true) {
                        val captchaImage = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE)
                        if (captchaImage.isNotEmpty()) {
                            CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage[0], captchaInput[0])
                        }
                    }
                }
                try { root?.recycle() } catch (e: Exception) {}
                delay(100)
            }
            
            if (System.currentTimeMillis() - startTime >= globalTimeout && isReviewClicked) {
                Log.w(TAG, "Global timeout reached, attempting recovery...")
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(500)
                isReviewClicked = false
                isProcessing = false
            }
        }
    }

    private suspend fun findPassengerFormAndFill() {
        repeat(10) {
            val root = rootInActiveWindow
            if (root != null) {
                val found = findNodeFast(root, listOf("Name"), IRCTC.NAME_INPUT) != null
                if (found) {
                    fillAllDetailsSuperFast()
                    root.recycle()
                    return
                }
                root.recycle()
            }
            delay(100)
        }
    }

    // ==================== SUPER FAST FILL ====================
    private suspend fun fillAllDetailsSuperFast() {
        val task = activeTask ?: return
        var currentRoot = rootInActiveWindow ?: return
        
        val nameFields = currentRoot.findAccessibilityNodeInfosByViewId(IRCTC.NAME_INPUT)
        for (i in currentPassengerIndex until min(nameFields.size, task.passengers.size)) {
            val passenger = task.passengers[i]
            if (i > 0) {
                findNodeFast(currentRoot, listOf("Add New", "Add Passenger"), IRCTC.ADD_PASSENGER_BTN)?.let {
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
            
            if (passenger.gender.isNotBlank()) selectPopupOption(IRCTC.GENDER_SPINNER, passenger.gender)
            if (passenger.berthPreference != "No Preference") selectPopupOption(IRCTC.BERTH_SPINNER, passenger.berthPreference)
            if (passenger.meal != "No Food") selectPopupOption(IRCTC.MEAL_SPINNER, passenger.meal)
            
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
            findNodeFast(currentRoot, listOf("Add Infant", "शिशु जोड़ें"), IRCTC.ADD_CHILD_BTN)?.let {
                humanClickFast(it)
                addDelay()
                currentRoot = rootInActiveWindow ?: return
            }
            findNodeFast(currentRoot, listOf("Infant Name"), IRCTC.CHILD_NAME)?.let { 
                if (it.text.isNullOrBlank()) setTextFast(it, child.name)
                fastDelay()
            }
            if (child.ageRange.isNotBlank()) selectPopupOption(IRCTC.CHILD_AGE, child.ageRange)
            if (child.gender.isNotBlank()) selectPopupOption(IRCTC.CHILD_GENDER, child.gender)
        }

        if (task.journeyDate.isNotBlank()) {
            selectDateWithCalendar(task.journeyDate)
        }

        if (task.coachPreferred && task.coachId.isNotBlank()) {
            findNodeFast(currentRoot, emptyList(), IRCTC.COACH_PREF_INPUT)?.let { 
                setTextFast(it, task.coachId.uppercase())
                fastDelay()
            }
        }
        if (task.mobileNo.isNotBlank()) {
            findNodeFast(currentRoot, emptyList(), IRCTC.MOBILE_INPUT)?.let { 
                setTextFast(it, task.mobileNo)
                fastDelay()
            }
        }
        
        awaitAdvanceOptionsFast()
        
        mediumDelay()
        currentRoot = rootInActiveWindow ?: return
        findNodeFast(currentRoot, listOf("Review Journey", "Continue"), IRCTC.PROCEED_BTN)?.let {
            humanClickFast(it)
            isReviewClicked = true
            lastActionTime = System.currentTimeMillis()
            startWatchdog()
            updateNotification("Waiting for Queue...")
        }
    }

    // ==================== ADVANCED OPTIONS ====================
    private suspend fun awaitAdvanceOptionsFast() {
        val task = activeTask ?: return
        val root = rootInActiveWindow ?: return
        
        if (task.autoUpgradation) {
            setCheckboxWithRetry(root, IRCTC.AUTO_UPGRADE_CHECK, true)
        }
        if (task.confirmBerthsOnly) {
            setCheckboxWithRetry(root, IRCTC.CONFIRM_BERTH_CHECK, true)
        }
        if (task.insurance) {
            setCheckboxWithRetry(root, IRCTC.INSURANCE_YES, true)
        } else {
            setCheckboxWithRetry(root, IRCTC.INSURANCE_NO, true)
        }
        fastDelay()
        
        if (task.bookingOption.value > 0) {
            selectPopupOption(IRCTC.BOOKING_OPT_SPINNER, task.bookingOption.display)
        }
    }

    private suspend fun setCheckboxWithRetry(root: AccessibilityNodeInfo, viewId: String, targetChecked: Boolean) {
        val checkbox = findNodeFast(root, emptyList(), viewId) ?: return
        var retry = 0
        while (retry < 3 && checkbox.isChecked != targetChecked) {
            humanClickFast(checkbox)
            delay(100)
            retry++
        }
        if (checkbox.isChecked == targetChecked) {
            Log.d(TAG, "Checkbox toggled successfully: $viewId")
        } else {
            Log.w(TAG, "Checkbox toggle may have failed: $viewId")
        }
    }

    private suspend fun selectPaymentFast(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        isReviewClicked = false
        watchdogJob?.cancel()
        
        when (task.payment.category) {
            PaymentCategory.CARDS_NETBANKING -> {
                findNodeFast(root, emptyList(), IRCTC.PAYMENT_CARDS)?.let { humanClickFast(it) }
            }
            PaymentCategory.BHIM_UPI -> {
                findNodeFast(root, emptyList(), IRCTC.PAYMENT_BHIM_UPI)?.let { humanClickFast(it) }
            }
            PaymentCategory.E_WALLETS -> {
                findNodeFast(root, emptyList(), IRCTC.PAYMENT_EWALLET)?.let { 
                    humanClickFast(it)
                    mediumDelay()
                    selectPopupOption("", task.payment.walletType.display)
                }
            }
            PaymentCategory.UPI_ID -> {
                findNodeFast(root, emptyList(), IRCTC.PAYMENT_UPI_ID)?.let { 
                    humanClickFast(it)
                    mediumDelay()
                    if (task.payment.upiId.isNotBlank()) {
                        findNodeFast(root, emptyList(), IRCTC.UPI_ID_INPUT)?.let { input ->
                            setTextFast(input, task.payment.upiId)
                        }
                    }
                }
            }
            PaymentCategory.UPI_APPS -> {
                findNodeFast(root, emptyList(), IRCTC.PAYMENT_UPI_APPS)?.let { 
                    humanClickFast(it)
                    mediumDelay()
                    selectPopupOption("", task.payment.upiApp.display)
                }
            }
        }
        mediumDelay()
        val proceedBtn = findNodeFast(root, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)
            ?: findNodeFast(root, listOf("Continue", "Proceed to Pay"), "")
        proceedBtn?.let { humanClickFast(it) }
        updateNotification("Payment Selected")
    }

    // ==================== OPTIMIZED findNodeFast ====================
    private fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        // Priority 1: View ID (Fastest)
        if (viewId.isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        
        // Priority 2: Exact Match
        for (label in labels) {
            root.findAccessibilityNodeInfosByText(label).firstOrNull { 
                it.text?.toString()?.trim().equals(label, ignoreCase = true) && it.isVisibleToUser 
            }?.let { return it }
        }
        
        // Priority 3: Recursive Search (Memory Optimized)
        return findNodeRecursiveOptimized(root, labels.map { it.uppercase() }, 0)
    }

    // MEMORY OPTIMIZED: Recursive search with proper recycling
    private fun findNodeRecursiveOptimized(node: AccessibilityNodeInfo, targetTexts: List<String>, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 15) return null
        
        val nodeText = node.text?.toString()?.trim()?.uppercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.trim()?.uppercase() ?: ""
        
        // Check if current node matches
        if (targetTexts.any { nodeText == it || nodeDesc == it || nodeText.contains(it) || nodeDesc.contains(it) }) {
            if (node.isVisibleToUser) {
                // Found node - return without recycling
                return node
            }
        }
        
        // Search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursiveOptimized(child, targetTexts, depth + 1)
            if (found != null) {
                // Found in child - return immediately, do NOT recycle child here
                return found
            }
            // Child not needed - recycle immediately to prevent memory leak
            child.recycle()
        }
        return null
    }

    private fun humanClickFast(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val finalX = bounds.centerX().toFloat()
            val finalY = bounds.centerY().toFloat()
            val path = Path().apply { moveTo(finalX, finalY) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, Random.nextLong(30, 50)))
                .build()
            dispatchGesture(gesture, null, null)
        } else {
            node.parent?.let { parent ->
                if (parent.isClickable) {
                    val bounds = Rect()
                    parent.getBoundsInScreen(bounds)
                    val finalX = bounds.centerX().toFloat()
                    val finalY = bounds.centerY().toFloat()
                    val path = Path().apply { moveTo(finalX, finalY) }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, Random.nextLong(30, 50)))
                        .build()
                    dispatchGesture(gesture, null, null)
                }
            }
        }
    }

    private fun setTextFast(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ==================== NOTIFICATION FUNCTIONS (FIXED) ====================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("VMAX Sniper")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun updateNotification(message: String) {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(1, buildNotification(message))
            Log.d(TAG, message)
        } catch (e: Exception) {
            Log.e(TAG, "Notification error: ${e.message}")
        }
    }

    override fun onInterrupt() { resetEngineState() }
    
    override fun onDestroy() { 
        watchdogJob?.cancel()
        serviceScope.cancel() 
        super.onDestroy()
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains(expected.flattenToString())
}
