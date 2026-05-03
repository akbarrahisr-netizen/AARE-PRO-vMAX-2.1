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
            // Adult Fields
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
            const val ADD_CHILD_BTN = "$PKG:id/btn_add_child"  // ✅ FIX 1
            const val SAVE_BTN = "$PKG:id/btn_save"           // ✅ FIX 2
            const val PROCEED_BTN = "$PKG:id/btn_proceed"
            const val BOOK_NOW_BTN = "$PKG:id/btn_book_now"
            // Captcha
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            // Payment
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_EWALLET = "$PKG:id/radio_ewallet"
            const val PAYMENT_UPI_ID = "$PKG:id/radio_upi_id"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            // Advanced Options
            const val AUTO_UPGRADE_CHECK = "$PKG:id/checkbox_auto_upgrade"
            const val CONFIRM_BERTH_CHECK = "$PKG:id/checkbox_confirm_berth"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
            const val COACH_PREF_INPUT = "$PKG:id/et_coach_preference"
            const val MOBILE_INPUT = "$PKG:id/et_mobile_number"
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var isProcessing = false
    private var currentPassengerIndex = 0  // ✅ FIX 3

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        createNotificationChannel()
        startForeground(1, buildNotification("🎯 Sniper Ready"))
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
                isArmed = false
                currentPassengerIndex = 0  // ✅ FIX 4
                val targetHour = if (activeTask!!.triggerTime.startsWith("10")) 10 else 11
                TimeSniper.scheduleFire(targetHour, activeTask!!.msAdvance.toLong()) {
                    isArmed = true
                }
            }
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isArmed || isProcessing) return
        val root = rootInActiveWindow ?: return

        try {
            if (root.packageName == IRCTC.PKG) {
                
                // 🆕 POPUP HANDLING
                findNodeByLabelsOrId(root, listOf("OK", "ठीक है", "YES", "हाँ"), "")?.let {
                    humanClickAdvanced(it)
                    return
                }

                // Step 1: Passenger Form
                val nameField = findNodeByLabelsOrId(root, listOf("Name", "यात्री का नाम"), IRCTC.NAME_INPUT)
                if (nameField != null) {
                    isProcessing = true
                    serviceScope.launch {
                        try {
                            fillAllDetailsAndProceed()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error: ${e.message}")
                        } finally {
                            isProcessing = false  // ✅ FIX 5
                        }
                    }
                    return
                }
                
                // Step 2: Payment Selection (NEW)
                val paymentCards = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS)
                if (paymentCards.isNotEmpty()) {
                    isProcessing = true
                    serviceScope.launch {
                        try {
                            selectPaymentMethod(root)
                        } finally {
                            isProcessing = false
                        }
                    }
                    return
                }
                
                // Step 3: Captcha Bypass
                val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
                if (captchaInput.isNotEmpty() && activeTask?.captchaAutofill == true) {
                    val captchaImage = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE)
                    if (captchaImage.isNotEmpty()) {
                        CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage[0], captchaInput[0])
                    }
                    return
                }
                
                // Step 4: Book Now Button
                val bookBtn = findNodeByLabelsOrId(root, listOf("Book Now", "Proceed to Pay"), IRCTC.BOOK_NOW_BTN)
                if (bookBtn != null) {
                    humanClickAdvanced(bookBtn)
                    updateNotification("✅ Booking Finalized!")
                    isArmed = false
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Event Error: ${e.message}")
            isProcessing = false  // ✅ FIX 6
        } finally { 
            root.recycle() 
        }
    }

    // ==================== 🎯 MAIN FILL FUNCTION (WITH ALL OPTIONS) ====================
    private suspend fun fillAllDetailsAndProceed() {
        val task = activeTask ?: return
        var currentRoot = rootInActiveWindow ?: return
        
        // 🚄 1. Fill Adults (With Index Tracking)
        for (i in currentPassengerIndex until min(currentRoot.findAccessibilityNodeInfosByViewId(IRCTC.NAME_INPUT).size, task.passengers.size)) {
            val passenger = task.passengers[i]
            
            if (i > 0) {
                val addBtn = findNodeByLabelsOrId(currentRoot, listOf("Add New", "Add Passenger"), IRCTC.ADD_PASSENGER_BTN)
                addBtn?.let { 
                    humanClickAdvanced(it)
                    delay(Random.nextLong(100, 200))
                    currentRoot = rootInActiveWindow ?: return
                }
            }

            // Name
            findNodeByLabelsOrId(currentRoot, listOf("Name", "यात्री का नाम"), IRCTC.NAME_INPUT)?.let { 
                setText(it, passenger.name)
                delay(Random.nextLong(100, 150))
            }
            // Age
            findNodeByLabelsOrId(currentRoot, listOf("Age", "आयु"), IRCTC.AGE_INPUT)?.let { 
                setText(it, passenger.age)
                delay(Random.nextLong(100, 200))
            }
            // Gender
            if (passenger.gender.isNotBlank()) {
                selectSpinnerOptionSafe(currentRoot, IRCTC.GENDER_SPINNER, passenger.gender)
            }
            // Berth Preference
            if (passenger.berthPreference != "No Preference") {
                selectSpinnerOptionSafe(currentRoot, IRCTC.BERTH_SPINNER, passenger.berthPreference)
            }
            // Meal
            if (passenger.meal != "No Food") {
                selectSpinnerOptionSafe(currentRoot, IRCTC.MEAL_SPINNER, passenger.meal)
            }
            
            // Save button after each passenger (except last)
            if (i > 0) {
                findNodeByLabelsOrId(currentRoot, listOf("Save", "जोड़ें"), IRCTC.SAVE_BTN)?.let { 
                    humanClickAdvanced(it)
                    delay(Random.nextLong(100, 300))
                    currentRoot = rootInActiveWindow ?: return
                }
            }
            
            currentPassengerIndex = i + 1
        }

        // 👶 2. Fill Children (If Any)
        for ((index, child) in task.children.withIndex()) {
            val addChildBtn = findNodeByLabelsOrId(currentRoot, listOf("Add Infant", "शिशु जोड़ें"), IRCTC.ADD_CHILD_BTN)
            addChildBtn?.let { 
                humanClickAdvanced(it)
                delay(Random.nextLong(200, 450))
                currentRoot = rootInActiveWindow ?: return
            }

            findNodeByLabelsOrId(currentRoot, listOf("Infant Name", "शिशु का नाम"), IRCTC.CHILD_NAME)?.let { 
                setText(it, child.name)
                delay(Random.nextLong(100, 150))
            }
            if (child.ageRange.isNotBlank()) {
                selectSpinnerOptionSafe(currentRoot, IRCTC.CHILD_AGE, child.ageRange)
            }
            if (child.gender.isNotBlank()) {
                selectSpinnerOptionSafe(currentRoot, IRCTC.CHILD_GENDER, child.gender)
            }
        }
        
        // 🛡️ 3. Advanced Booking Options
        awaitAdvanceOptionsSetup()
        
        // 📱 4. Coach & Mobile
        awaitCoachAndMobileSetup()
        
        // 🚀 5. Proceed to Review/Captcha Page
        delay(Random.nextLong(100, 350))
        currentRoot = rootInActiveWindow ?: return
        findNodeByLabelsOrId(currentRoot, listOf("Review Journey Details", "Continue", "अभी बुक करें"), IRCTC.PROCEED_BTN)?.let { 
            humanClickAdvanced(it)
            updateNotification("📝 Proceeding to Captcha/Payment...")
        }
    }

    // ==================== 🎯 ADVANCED OPTIONS ====================
    private suspend fun awaitAdvanceOptionsSetup() {
        val task = activeTask ?: return
        var root = rootInActiveWindow ?: return
        
        // Auto Upgradation
        val autoUpgrade = findNodeByLabelsOrId(root, emptyList(), IRCTC.AUTO_UPGRADE_CHECK)
        if (autoUpgrade != null && task.autoUpgradation) {
            if (!autoUpgrade.isChecked) humanClickAdvanced(autoUpgrade)
            delay(Random.nextLong(50, 80))
        }
        
        // Confirm Berths Only
        val confirmBerth = findNodeByLabelsOrId(root, emptyList(), IRCTC.CONFIRM_BERTH_CHECK)
        if (confirmBerth != null && task.confirmBerthsOnly) {
            if (!confirmBerth.isChecked) humanClickAdvanced(confirmBerth)
            delay(Random.nextLong(50, 80))
        }
        
        // Travel Insurance
        if (task.insurance) {
            findNodeByLabelsOrId(root, emptyList(), IRCTC.INSURANCE_YES)?.let { 
                if (!it.isChecked) humanClickAdvanced(it)
            }
        } else {
            findNodeByLabelsOrId(root, emptyList(), IRCTC.INSURANCE_NO)?.let { 
                if (!it.isChecked) humanClickAdvanced(it)
            }
        }
        delay(Random.nextLong(100, 150))
        
        // Booking Option Spinner
        if (task.bookingOption.value > 0) {
            val bookingOptSpinner = findNodeByLabelsOrId(root, emptyList(), IRCTC.BOOKING_OPT_SPINNER)
            bookingOptSpinner?.let {
                selectSpinnerOptionSafe(root, IRCTC.BOOKING_OPT_SPINNER, task.bookingOption.display)
                delay(Random.nextLong(150, 200))
            }
        }
    }
    
    private suspend fun awaitCoachAndMobileSetup() {
        val task = activeTask ?: return
        var root = rootInActiveWindow ?: return
        
        // Coach Preference
        if (task.coachPreferred && task.coachId.isNotBlank()) {
            findNodeByLabelsOrId(root, emptyList(), IRCTC.COACH_PREF_INPUT)?.let { 
                setText(it, task.coachId.uppercase())
                delay(Random.nextLong(150, 200))
            }
        }
        
        // Mobile Number
        if (task.mobileNo.isNotBlank()) {
            findNodeByLabelsOrId(root, emptyList(), IRCTC.MOBILE_INPUT)?.let { 
                setText(it, task.mobileNo)
                delay(Random.nextLong(150, 200))
            }
        }
    }
    
    // ==================== 🎯 PAYMENT SELECTION ====================
    private suspend fun selectPaymentMethod(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        
        when (task.payment.category) {
            PaymentCategory.CARDS_NETBANKING -> {
                findNodeByLabelsOrId(root, emptyList(), IRCTC.PAYMENT_CARDS)?.let { humanClickAdvanced(it) }
            }
            PaymentCategory.BHIM_UPI -> {
                findNodeByLabelsOrId(root, emptyList(), IRCTC.PAYMENT_BHIM_UPI)?.let { humanClickAdvanced(it) }
            }
            PaymentCategory.E_WALLETS -> {
                findNodeByLabelsOrId(root, emptyList(), IRCTC.PAYMENT_EWALLET)?.let { 
                    humanClickAdvanced(it)
                    delay(Random.nextLong(100, 250))
                    val walletSpinner = findNodeByLabelsOrId(root, listOf(task.payment.walletType.display), "")
                    walletSpinner?.let { opt -> humanClickAdvanced(opt) }
                }
            }
            PaymentCategory.UPI_ID -> {
                findNodeByLabelsOrId(root, emptyList(), IRCTC.PAYMENT_UPI_ID)?.let { 
                    humanClickAdvanced(it)
                    delay(Random.nextLong(100, 250))
                    if (task.payment.upiId.isNotBlank()) {
                        findNodeByLabelsOrId(root, emptyList(), IRCTC.UPI_ID_INPUT)?.let { input ->
                            setText(input, task.payment.upiId)
                        }
                    }
                }
            }
            PaymentCategory.UPI_APPS -> {
                findNodeByLabelsOrId(root, emptyList(), IRCTC.PAYMENT_UPI_APPS)?.let { 
                    humanClickAdvanced(it)
                    delay(Random.nextLong(100, 250))
                    val upiAppSpinner = findNodeByLabelsOrId(root, listOf(task.payment.upiApp.display), "")
                    upiAppSpinner?.let { opt -> humanClickAdvanced(opt) }
                }
            }
        }
        delay(Random.nextLong(200, 350))
        
        // Click Continue after payment selection
        val proceedBtn = findNodeByLabelsOrId(root, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)
        proceedBtn?.let { humanClickAdvanced(it) }
    }

    // ==================== 🛠️ HELPER FUNCTIONS ====================
    
    private fun humanClickAdvanced(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val finalX = (bounds.centerX() + (-12..12).random()).toFloat()
        val finalY = (bounds.centerY() + (-12..12).random()).toFloat()
        val path = Path().apply {
            moveTo(finalX, finalY)
            lineTo(finalX + (1..3).random().toFloat(), finalY + (1..3).random().toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, Random.nextLong(40, 60)))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private suspend fun selectSpinnerOptionSafe(root: AccessibilityNodeInfo, spinnerId: String, optionText: String) {
        val spinner = findNodeByLabelsOrId(root, emptyList(), spinnerId)
        spinner?.let {
            humanClickAdvanced(it) 
            delay(Random.nextLong(100, 300))
            val newRoot = rootInActiveWindow ?: return
            findNodeByLabelsOrId(newRoot, listOf(optionText), "")?.let { opt -> 
                humanClickAdvanced(opt) 
                delay(Random.nextLong(150, 300))
            }
        }
    }

    private fun findNodeByLabelsOrId(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isClickable || node.isEditable || (node.parent?.isClickable == true)) return node
            }
        }
        // Partial text match (for dynamic amounts like "PROCEED TO PAY ₹623.15")
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText("")
            for (node in nodes) {
                val nodeText = node.text?.toString()?.uppercase() ?: continue
                if (nodeText.contains(label.uppercase())) {
                    if (node.isClickable || node.isEditable || (node.parent?.isClickable == true)) return node
                }
            }
        }
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 VMAX Sniper Pro")
        .setContentText(content)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(content))
    }

    override fun onInterrupt() { isArmed = false; isProcessing = false }
    override fun onDestroy() { serviceScope.cancel(); super.onDestroy() }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains(expected.flattenToString())
}
