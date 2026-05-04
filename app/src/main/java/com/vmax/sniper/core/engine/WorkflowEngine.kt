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

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var isProcessing = false
    private var hasRefreshed = false
    private var currentPassengerIndex = 0
    private var watchdogJob: Job? = null
    private var isReviewClicked = false

    // ==================== FAST DELAYS ====================
    private suspend fun fastDelay() = delay(Random.nextLong(30, 70))
    private suspend fun mediumDelay() = delay(Random.nextLong(60, 120))
    private suspend fun addDelay() = delay(Random.nextLong(80, 150))

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 10
        }
        createNotificationChannel()
        startForeground(1, buildNotification("🎯 Super Fast Sniper Ready"))
        Log.d(TAG, "VMAX Super Fast Sniper Connected")
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
                updateNotification("⏳ Waiting for ${activeTask!!.triggerTime} (Advance: ${advanceMs}ms)")
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
    }

    // ==================== 🎯 REFRESH ENGINE ====================
    private fun triggerPreciseRefresh() {
        val root = rootInActiveWindow ?: return
        try {
            val refreshBtn = findNodeFast(root, listOf("Search", "रिफ्रेश", "Refresh"), IRCTC.SEARCH_BTN)
            if (refreshBtn?.isClickable == true) {
                humanClickFast(refreshBtn)
                updateNotification("🔄 Refresh Triggered!")
                Log.d(TAG, "Refresh clicked at ${System.currentTimeMillis()}")
            }
        } finally { root.recycle() }
    }

    private suspend fun selectSpecificClassAfterRefresh() {
        val task = activeTask ?: return
        delay(Random.nextLong(50, 100))
        val root = rootInActiveWindow ?: return
        try {
            val classNode = findNodeFast(root, listOf(task.travelClass.code), "")
            if (classNode?.isClickable == true) {
                humanClickFast(classNode)
                Log.d(TAG, "Class selected: ${task.travelClass.code}")
                mediumDelay()
                findNodeFast(root, listOf("Book Now", "अभी बुक करें"), IRCTC.BOOK_NOW_BTN)?.let { 
                    humanClickFast(it)
                    Log.d(TAG, "Book Now clicked after class selection")
                }
            }
        } finally { root.recycle() }
    }

    // ==================== 🚀 MAIN ENGINE ====================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isArmed || isProcessing) return
        val root = rootInActiveWindow ?: return

        try {
            if (root.packageName == IRCTC.PKG) {
                
                // 1. Popup Clearer
                findNodeFast(root, listOf("OK", "YES", "ठीक है", "हाँ"), "")?.let {
                    humanClickFast(it)
                    fastDelay()
                    return
                }

                // 2. Post-Refresh Class Selection
                if (hasRefreshed && findNodeFast(root, listOf("SL", "3A", "2A"), "") != null && !isReviewClicked) {
                    isProcessing = true
                    serviceScope.launch {
                        try { 
                            selectSpecificClassAfterRefresh() 
                            findPassengerFormAndFill()
                        } finally { isProcessing = false }
                    }
                    return
                }

                // 3. Passenger Form Filling
                if (!isReviewClicked && findNodeFast(root, listOf("Name"), IRCTC.NAME_INPUT) != null) {
                    isProcessing = true
                    serviceScope.launch {
                        try { fillAllDetailsSuperFast() } 
                        catch (e: Exception) { Log.e(TAG, "Fill Error: ${e.message}") }
                        finally { isProcessing = false }
                    }
                    return
                }
                
                // 4. Payment Selection
                if (root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS).isNotEmpty()) {
                    isProcessing = true
                    serviceScope.launch {
                        try { selectPaymentFast(root) } 
                        finally { isProcessing = false }
                    }
                    return
                }
                
                // 5. Captcha Detection
                val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
                if (captchaInput.isNotEmpty() && activeTask?.captchaAutofill == true) {
                    val captchaImage = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE)
                    if (captchaImage.isNotEmpty()) {
                        CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage[0], captchaInput[0])
                    }
                    return
                }
                
                // 6. Final Book Now
                findNodeFast(root, listOf("Book Now", "Proceed to Pay", "Pay"), IRCTC.BOOK_NOW_BTN)?.let {
                    humanClickFast(it)
                    updateNotification("✅ Booking Submitted!")
                    isArmed = false
                    watchdogJob?.cancel()
                    return
                }
                
                if (isReviewClicked) return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Event Error: ${e.message}")
            isProcessing = false
        } finally { root.recycle() }
    }

    // ==================== 🎯 WATCHDOG ====================
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            var retryCount = 0
            while (isArmed && isReviewClicked) {
                val root = rootInActiveWindow
                if (root != null && root.packageName == IRCTC.PKG) {
                    
                    // Retry if Continue button still visible
                    val proceedBtn = findNodeFast(root, listOf("Continue", "अभी बुक करें"), IRCTC.PROCEED_BTN)
                    if (proceedBtn != null && proceedBtn.isVisibleToUser && retryCount < 3) {
                        retryCount++
                        Log.d(TAG, "🔄 Retry #$retryCount: Continue button still visible")
                        humanClickFast(proceedBtn)
                        delay(200)
                    } else {
                        retryCount = 0
                    }
                    
                    // Payment Page Detection
                    if (root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS).isNotEmpty()) {
                        Log.d(TAG, "🎯 Payment Page Detected by Watchdog!")
                        isReviewClicked = false
                        isProcessing = true
                        selectPaymentFast(root)
                        isProcessing = false
                        watchdogJob?.cancel()
                        return@launch
                    }
                    
                    // Captcha Detection
                    val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
                    if (captchaInput.isNotEmpty() && activeTask?.captchaAutofill == true) {
                        val captchaImage = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE)
                        if (captchaImage.isNotEmpty()) {
                            Log.d(TAG, "🔐 Captcha Detected by Watchdog!")
                            CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage[0], captchaInput[0])
                        }
                    }
                }
                root?.recycle()
                delay(250)
            }
        }
    }

    // ==================== 🚀 SUPER FAST FILL ====================
    private suspend fun findPassengerFormAndFill() {
        repeat(10) {
            val root = rootInActiveWindow
            if (root != null && findNodeFast(root, listOf("Name"), IRCTC.NAME_INPUT) != null) {
                fillAllDetailsSuperFast()
                return
            }
            delay(100)
        }
    }

    private suspend fun fillAllDetailsSuperFast() {
        val task = activeTask ?: return
        var currentRoot = rootInActiveWindow ?: return
        
        // 👤 ADULTS
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

            findNodeFast(currentRoot, listOf("Name"), IRCTC.NAME_INPUT)?.let { setTextFast(it, passenger.name); fastDelay() }
            findNodeFast(currentRoot, listOf("Age"), IRCTC.AGE_INPUT)?.let { setTextFast(it, passenger.age); fastDelay() }
            
            if (passenger.gender.isNotBlank()) selectSpinnerFast(currentRoot, IRCTC.GENDER_SPINNER, passenger.gender)
            if (passenger.berthPreference != "No Preference") selectSpinnerFast(currentRoot, IRCTC.BERTH_SPINNER, passenger.berthPreference)
            if (passenger.meal != "No Food") selectSpinnerFast(currentRoot, IRCTC.MEAL_SPINNER, passenger.meal)
            
            if (i > 0) {
                findNodeFast(currentRoot, listOf("Save", "जोड़ें"), IRCTC.SAVE_BTN)?.let {
                    humanClickFast(it)
                    mediumDelay()
                    currentRoot = rootInActiveWindow ?: return
                }
            }
            currentPassengerIndex = i + 1
        }

        // 👶 CHILDREN
        for (child in task.children) {
            findNodeFast(currentRoot, listOf("Add Infant", "शिशु जोड़ें"), IRCTC.ADD_CHILD_BTN)?.let {
                humanClickFast(it)
                addDelay()
                currentRoot = rootInActiveWindow ?: return
            }
            findNodeFast(currentRoot, listOf("Infant Name"), IRCTC.CHILD_NAME)?.let { setTextFast(it, child.name); fastDelay() }
            if (child.ageRange.isNotBlank()) selectSpinnerFast(currentRoot, IRCTC.CHILD_AGE, child.ageRange)
            if (child.gender.isNotBlank()) selectSpinnerFast(currentRoot, IRCTC.CHILD_GENDER, child.gender)
        }

        // ⚙️ ADVANCED OPTIONS
        awaitAdvanceOptionsFast()
        awaitCoachAndMobileFast()
        
        // 🚀 PROCEED
        mediumDelay()
        currentRoot = rootInActiveWindow ?: return
        findNodeFast(currentRoot, listOf("Review Journey Details", "Continue", "अभी बुक करें"), IRCTC.PROCEED_BTN)?.let {
            humanClickFast(it)
            isReviewClicked = true
            startWatchdog()
            updateNotification("🔄 Waiting for IRCTC Queue...")
            Log.d(TAG, "Proceed clicked, Watchdog started")
        }
    }

    private suspend fun awaitAdvanceOptionsFast() {
        val task = activeTask ?: return
        val root = rootInActiveWindow ?: return
        
        if (task.autoUpgradation) {
            findNodeFast(root, emptyList(), IRCTC.AUTO_UPGRADE_CHECK)?.let { 
                if (!it.isChecked) humanClickFast(it)
                fastDelay()
            }
        }
        if (task.confirmBerthsOnly) {
            findNodeFast(root, emptyList(), IRCTC.CONFIRM_BERTH_CHECK)?.let { 
                if (!it.isChecked) humanClickFast(it)
                fastDelay()
            }
        }
        if (task.insurance) {
            findNodeFast(root, emptyList(), IRCTC.INSURANCE_YES)?.let { 
                if (!it.isChecked) humanClickFast(it)
            }
        } else {
            findNodeFast(root, emptyList(), IRCTC.INSURANCE_NO)?.let { 
                if (!it.isChecked) humanClickFast(it)
            }
        }
        fastDelay()
        
        if (task.bookingOption.value > 0) {
            selectSpinnerFast(root, IRCTC.BOOKING_OPT_SPINNER, task.bookingOption.display)
        }
    }
    
    private suspend fun awaitCoachAndMobileFast() {
        val task = activeTask ?: return
        val root = rootInActiveWindow ?: return
        
        if (task.coachPreferred && task.coachId.isNotBlank()) {
            findNodeFast(root, emptyList(), IRCTC.COACH_PREF_INPUT)?.let { 
                setTextFast(it, task.coachId.uppercase())
                fastDelay()
            }
        }
        if (task.mobileNo.isNotBlank()) {
            findNodeFast(root, emptyList(), IRCTC.MOBILE_INPUT)?.let { 
                setTextFast(it, task.mobileNo)
                fastDelay()
            }
        }
    }

    // ==================== 💳 FAST PAYMENT ====================
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
                    findNodeFast(root, listOf(task.payment.walletType.display), "")?.let { opt -> humanClickFast(opt) }
                }
            }
            PaymentCategory.UPI_ID -> {
                findNodeFast(root, emptyList(), IRCTC.PAYMENT_UPI_ID)?.let { 
                    humanClickFast(it)
                    mediumDelay()
                    if (task.payment.upiId.isNotBlank()) {
                        findNodeFast(root, emptyList(), IRCTC.UPI_ID_INPUT)?.let { input -> setTextFast(input, task.payment.upiId) }
                    }
                }
            }
            PaymentCategory.UPI_APPS -> {
                findNodeFast(root, emptyList(), IRCTC.PAYMENT_UPI_APPS)?.let { 
                    humanClickFast(it)
                    mediumDelay()
                    findNodeFast(root, listOf(task.payment.upiApp.display), "")?.let { opt -> humanClickFast(opt) }
                }
            }
        }
        mediumDelay()
        findNodeFast(root, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)?.let { humanClickFast(it) }
        updateNotification("💳 Payment Selected: ${task.payment.category.display}")
    }

    // ==================== 🛠️ HELPER FUNCTIONS ====================
    
    private fun humanClickFast(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val finalX = bounds.centerX().toFloat()
        val finalY = bounds.centerY().toFloat()
        val path = Path().apply { moveTo(finalX, finalY) }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, Random.nextLong(10, 25)))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun setTextFast(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private suspend fun selectSpinnerFast(root: AccessibilityNodeInfo, spinnerId: String, optionText: String) {
        findNodeFast(root, emptyList(), spinnerId)?.let { spinner ->
            humanClickFast(spinner)
            fastDelay()
            findNodeFast(rootInActiveWindow ?: return, listOf(optionText), "")?.let { 
                humanClickFast(it)
                fastDelay()
            }
        }
    }

    private fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        // Priority 1: View ID (Fastest)
        if (viewId.isNotEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty() && nodes[0].isVisibleToUser) return nodes[0]
        }
        // Priority 2: Text Labels
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isVisibleToUser && (node.isClickable || node.parent?.isClickable == true)) return node
            }
        }
        // Priority 3: Partial Text Match
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText("")
            for (node in nodes) {
                val nodeText = node.text?.toString()?.uppercase() ?: continue
                if (nodeText.contains(label.uppercase())) {
                    if (node.isVisibleToUser && (node.isClickable || node.parent?.isClickable == true)) return node
                }
            }
        }
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "VMAX Super Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 VMAX Super Fast Sniper")
        .setContentText(content)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(content))
        Log.d(TAG, content)
    }

    override fun onInterrupt() { 
        isArmed = false
        isProcessing = false
        isReviewClicked = false
        watchdogJob?.cancel()
    }
    
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
