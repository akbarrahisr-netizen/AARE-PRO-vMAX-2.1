package com.aare.vmax.core.engine

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
import com.aare.vmax.core.model.*
import com.aare.vmax.core.network.TimeSyncManager
import kotlinx.coroutines.*
import java.util.Calendar
import kotlin.math.min
import kotlin.random.Random

class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Sniper_77"
        const val ACTION_START_SNIPER = "com.aare.vmax.START_SNIPER"
        const val EXTRA_TASK = "extra_task"
        private const val NOTIF_CHANNEL_ID = "vmax_sniper"
        private const val NOTIF_ID = 1
        
        object IRCTC {
            const val PKG = "cris.org.in.prs.ima"
            
            // Passenger Section
            const val NAME_INPUT = "$PKG:id/et_passenger_name"
            const val AGE_INPUT = "$PKG:id/et_passenger_age"
            const val GENDER_SPINNER = "$PKG:id/et_gender"
            const val BERTH_SPINNER = "$PKG:id/et_berth_preference"
            const val MEAL_SPINNER = "$PKG:id/et_meal"
            const val ADD_PASSENGER_BTN = "$PKG:id/tv_add_passanger"
            const val CHILD_NAME = "$PKG:id/et_child_name"
            const val CHILD_AGE = "$PKG:id/spinner_child_age"
            const val CHILD_GENDER = "$PKG:id/spinner_child_gender"
            const val AUTO_UPGRADE_CHECK = "$PKG:id/checkbox_auto_upgrade"
            const val CONFIRM_BERTH_CHECK = "$PKG:id/checkbox_confirm_berth"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
            const val BOOKING_OPT_SPINNER = "$PKG:id/spinner_booking_option"
            const val COACH_PREF_INPUT = "$PKG:id/et_coach_preference"
            const val MOBILE_INPUT = "$PKG:id/et_mobile_number"
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_EWALLET = "$PKG:id/radio_ewallet"
            const val PAYMENT_UPI_ID = "$PKG:id/radio_upi_id"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            const val UPI_ID_INPUT = "$PKG:id/et_upi_id"
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            const val BOOK_NOW_BTN = "$PKG:id/btn_book_now"
            const val PROCEED_BTN = "$PKG:id/btn_proceed"
            const val CONTINUE_BTN = "$PKG:id/btn_continue"
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var activeTask: SniperTask? = null
    private var isArmed = false 
    private var currentPassengerIndex = 0
    private var isProcessing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
        }
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("🎯 Sniper Ready | 77 Points Loaded"))
        Log.d(TAG, "✅ VMAX Sniper 77 - Online")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SNIPER) {
            activeTask = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_TASK, SniperTask::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_TASK)
            }
            
            if (activeTask != null) {
                isArmed = false
                currentPassengerIndex = 0
                updateNotification("⏳ Countdown Started: ${activeTask!!.triggerTime}")
                serviceScope.launch { executeWithPrecision(activeTask!!) }
            }
        }
        return START_STICKY
    }

    private suspend fun executeWithPrecision(task: SniperTask) {
        if (!TimeSyncManager.isSynced()) {
            TimeSyncManager.syncWithNetwork()
            delay(1000)
        }
        
        val targetHour = if (task.triggerTime.startsWith("10")) 10 else 11
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val triggerTime = targetTime - task.msAdvance
        
        while (true) {
            val now = TimeSyncManager.currentTimeMillis()
            val diff = triggerTime - now
            if (diff <= 0) break 
            when {
                diff > 1000 -> delay(500)
                diff > 50 -> delay(10)
                else -> delay(1)
            }
        }
        
        Log.d(TAG, "🎯 FIRING at: ${TimeSyncManager.getPreciseTimeString()}")
        isArmed = true 
        updateNotification("🔥 ARMED & ACTIVE! Filling Forms...")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isArmed || isProcessing) return
        val root = rootInActiveWindow ?: return

        try {
            if (root.packageName == IRCTC.PKG) {
                
                val nameFields = root.findAccessibilityNodeInfosByViewId(IRCTC.NAME_INPUT)
                if (nameFields.isNotEmpty()) {
                    isProcessing = true
                    serviceScope.launch {
                        try {
                            fillPassengerDetails(root)
                        } finally {
                            isProcessing = false
                        }
                    }
                    return
                }
                
                val childNameFields = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_NAME)
                if (childNameFields.isNotEmpty() && activeTask?.children?.isNotEmpty() == true) {
                    isProcessing = true
                    serviceScope.launch {
                        try {
                            fillChildDetails(root)
                        } finally {
                            isProcessing = false
                        }
                    }
                    return
                }
                
                val autoUpgradeCheck = root.findAccessibilityNodeInfosByViewId(IRCTC.AUTO_UPGRADE_CHECK)
                if (autoUpgradeCheck.isNotEmpty()) {
                    isProcessing = true
                    serviceScope.launch {
                        try {
                            setBookingOptions(root)
                        } finally {
                            isProcessing = false
                        }
                    }
                    return
                }
                
                val coachInput = root.findAccessibilityNodeInfosByViewId(IRCTC.COACH_PREF_INPUT)
                if (coachInput.isNotEmpty()) {
                    isProcessing = true
                    serviceScope.launch {
                        try {
                            setCoachAndMobile(root)
                        } finally {
                            isProcessing = false
                        }
                    }
                    return
                }
                
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
                
                val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
                if (captchaInput.isNotEmpty() && activeTask?.captchaAutofill == true) {
                    handleCaptcha(root)
                    return
                }
                
                val bookBtn = root.findAccessibilityNodeInfosByViewId(IRCTC.BOOK_NOW_BTN)
                if (bookBtn.isNotEmpty()) {
                    isProcessing = true
                    serviceScope.launch {
                        try {
                            humanClickAdvanced(bookBtn[0])
                            updateNotification("✅ Booking Submitted (Human Click)!")
                            isArmed = false
                        } finally {
                            isProcessing = false
                        }
                    }
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        } finally {
            root.recycle()
        }
    }

    private suspend fun fillPassengerDetails(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        val nameFields = root.findAccessibilityNodeInfosByViewId(IRCTC.NAME_INPUT)
        val ageFields = root.findAccessibilityNodeInfosByViewId(IRCTC.AGE_INPUT)
        val genderSpinners = root.findAccessibilityNodeInfosByViewId(IRCTC.GENDER_SPINNER)
        
        for (i in currentPassengerIndex until min(nameFields.size, task.passengers.size)) {
            val passenger = task.passengers[i]
            
            // ✅ उस्ताद का फिक्स: Scroll के साथ AccessibilityService. लगा हुआ है
            if (Random.nextInt(100) < 20 && i > 0) {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_SCROLL_FORWARD)
                delay(Random.nextLong(200, 450))
            }
            
            if (nameFields[i].text.isNullOrBlank()) {
                delay(Random.nextLong(50, 150))
                setTextToNode(nameFields[i], passenger.name)
                delay(Random.nextLong(30, 80))
            }
            if (i < ageFields.size && ageFields[i].text.isNullOrBlank()) {
                delay(Random.nextLong(30, 100))
                setTextToNode(ageFields[i], passenger.age)
                delay(Random.nextLong(30, 80))
            }
            if (i < genderSpinners.size) {
                delay(Random.nextLong(50, 120))
                selectSpinnerValue(genderSpinners[i], passenger.gender, root)
                delay(Random.nextLong(40, 100))
            }
            currentPassengerIndex = i + 1
        }
        
        if (currentPassengerIndex < task.passengers.size) {
            val addBtn = root.findAccessibilityNodeInfosByViewId(IRCTC.ADD_PASSENGER_BTN)
                .ifEmpty { root.findAccessibilityNodeInfosByText("Add Passenger") }
            if (addBtn.isNotEmpty()) {
                delay(Random.nextLong(200, 400))
                humanClickAdvanced(addBtn[0])
                delay(Random.nextLong(500, 900))
            }
        }
        
        if (currentPassengerIndex >= task.passengers.size) {
            delay(Random.nextLong(500, 1000))
            val proceedBtn = findNodeByLabels(root, listOf("Continue", "Review Journey", "Proceed"), IRCTC.PROCEED_BTN)
            proceedBtn?.let { 
                delay(Random.nextLong(200, 400))
                humanClickAdvanced(it) 
            }
        }
    }

    private suspend fun fillChildDetails(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        val childNameFields = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_NAME)
        val childAgeSpinners = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_AGE)
        val childGenderSpinners = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_GENDER)
        
        for (i in 0 until min(childNameFields.size, task.children.size)) {
            val child = task.children[i]
            
            if (childNameFields[i].text.isNullOrBlank()) {
                delay(Random.nextLong(50, 120))
                setTextToNode(childNameFields[i], child.name)
                delay(Random.nextLong(30, 80))
            }
            if (i < childAgeSpinners.size && child.ageRange.isNotBlank()) {
                delay(Random.nextLong(40, 100))
                selectSpinnerValue(childAgeSpinners[i], child.ageRange, root)
                delay(Random.nextLong(30, 80))
            }
            if (i < childGenderSpinners.size && child.gender.isNotBlank()) {
                delay(Random.nextLong(40, 100))
                selectSpinnerValue(childGenderSpinners[i], child.gender, root)
                delay(Random.nextLong(30, 80))
            }
        }
    }

    private suspend fun setBookingOptions(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        
        val autoUpgrade = root.findAccessibilityNodeInfosByViewId(IRCTC.AUTO_UPGRADE_CHECK)
        if (autoUpgrade.isNotEmpty() && task.autoUpgradation) {
            if (!autoUpgrade[0].isChecked) humanClickAdvanced(autoUpgrade[0])
            delay(Random.nextLong(30, 80))
        }
        
        val confirmBerth = root.findAccessibilityNodeInfosByViewId(IRCTC.CONFIRM_BERTH_CHECK)
        if (confirmBerth.isNotEmpty() && task.confirmBerthsOnly) {
            if (!confirmBerth[0].isChecked) humanClickAdvanced(confirmBerth[0])
            delay(Random.nextLong(30, 80))
        }
        
        if (task.insurance) {
            val insuranceYes = root.findAccessibilityNodeInfosByViewId(IRCTC.INSURANCE_YES)
            if (insuranceYes.isNotEmpty() && !insuranceYes[0].isChecked) humanClickAdvanced(insuranceYes[0])
        } else {
            val insuranceNo = root.findAccessibilityNodeInfosByViewId(IRCTC.INSURANCE_NO)
            if (insuranceNo.isNotEmpty() && !insuranceNo[0].isChecked) humanClickAdvanced(insuranceNo[0])
        }
        delay(Random.nextLong(40, 100))
        
        val bookingOptSpinner = root.findAccessibilityNodeInfosByViewId(IRCTC.BOOKING_OPT_SPINNER)
        if (bookingOptSpinner.isNotEmpty() && task.bookingOption != "None" && task.bookingOption.isNotBlank()) {
            selectSpinnerValue(bookingOptSpinner[0], task.bookingOption, root)
            delay(Random.nextLong(40, 100))
        }
    }

    private suspend fun setCoachAndMobile(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        
        if (task.coachPreferred && task.coachId.isNotBlank()) {
            val coachInput = root.findAccessibilityNodeInfosByViewId(IRCTC.COACH_PREF_INPUT)
            if (coachInput.isNotEmpty() && coachInput[0].text.isNullOrBlank()) {
                delay(Random.nextLong(50, 120))
                setTextToNode(coachInput[0], task.coachId.uppercase())
                delay(Random.nextLong(30, 80))
            }
        }
        
        if (task.mobileNo.isNotBlank()) {
            val mobileInput = root.findAccessibilityNodeInfosByViewId(IRCTC.MOBILE_INPUT)
            if (mobileInput.isNotEmpty() && mobileInput[0].text.isNullOrBlank()) {
                delay(Random.nextLong(50, 120))
                setTextToNode(mobileInput[0], task.mobileNo)
                delay(Random.nextLong(30, 80))
            }
        }
    }

    private suspend fun selectPaymentMethod(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        
        when (task.payment.category) {
            PaymentCategory.CARDS_NETBANKING -> {
                val cardsRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS)
                if (cardsRadio.isNotEmpty()) humanClickAdvanced(cardsRadio[0])
            }
            PaymentCategory.BHIM_UPI -> {
                val upiRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_BHIM_UPI)
                if (upiRadio.isNotEmpty()) humanClickAdvanced(upiRadio[0])
            }
            PaymentCategory.E_WALLETS -> {
                val walletRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_EWALLET)
                if (walletRadio.isNotEmpty()) humanClickAdvanced(walletRadio[0])
                delay(Random.nextLong(80, 150))
                val walletSpinner = root.findAccessibilityNodeInfosByText(task.payment.walletType)
                if (walletSpinner.isNotEmpty()) humanClickAdvanced(walletSpinner[0])
            }
            PaymentCategory.UPI_ID -> {
                val upiIdRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_UPI_ID)
                if (upiIdRadio.isNotEmpty()) humanClickAdvanced(upiIdRadio[0])
                delay(Random.nextLong(80, 150))
                if (task.payment.upiId.isNotBlank()) {
                    val upiIdInput = root.findAccessibilityNodeInfosByViewId(IRCTC.UPI_ID_INPUT)
                    if (upiIdInput.isNotEmpty()) {
                        delay(Random.nextLong(50, 100))
                        setTextToNode(upiIdInput[0], task.payment.upiId)
                    }
                }
            }
            PaymentCategory.UPI_APPS -> {
                val upiAppsRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_UPI_APPS)
                if (upiAppsRadio.isNotEmpty()) humanClickAdvanced(upiAppsRadio[0])
                delay(Random.nextLong(80, 150))
                val upiAppSpinner = root.findAccessibilityNodeInfosByText(task.payment.upiApp)
                if (upiAppSpinner.isNotEmpty()) humanClickAdvanced(upiAppSpinner[0])
            }
        }
        delay(Random.nextLong(80, 150))
        
        if (task.payment.manualPayment) {
            val manualCheckbox = root.findAccessibilityNodeInfosByText("I will fill payment information manually")
            if (manualCheckbox.isNotEmpty() && !manualCheckbox[0].isChecked) {
                humanClickAdvanced(manualCheckbox[0])
            }
        }
        
        val proceedBtn = root.findAccessibilityNodeInfosByViewId(IRCTC.PROCEED_BTN)
            .ifEmpty { root.findAccessibilityNodeInfosByViewId(IRCTC.CONTINUE_BTN) }
        if (proceedBtn.isNotEmpty()) {
            delay(Random.nextLong(200, 400))
            humanClickAdvanced(proceedBtn[0])
        }
    }

    private fun handleCaptcha(root: AccessibilityNodeInfo) {
        val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
        val captchaImage = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE)
        
        if (captchaInput.isNotEmpty() && captchaImage.isNotEmpty()) {
            updateNotification("🔐 Auto-Bypassing Captcha...")
            CaptchaSolver.executeBypass(this, captchaImage[0], captchaInput[0])
        }
    }

    private fun humanClickAdvanced(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        val randomX = (-12..12).random()
        val randomY = (-12..12).random()
        
        val finalX = (bounds.centerX() + randomX).toFloat()
        val finalY = (bounds.centerY() + randomY).toFloat()
        
        val path = Path().apply {
            moveTo(finalX, finalY)
            lineTo(finalX + (1..3).random().toFloat(), finalY + (1..3).random().toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, Random.nextLong(40, 60)))
            .build()
        
        dispatchGesture(gesture, null, null)
        Log.d(TAG, "🤖 Anti-Bot Advanced Tap at: X=$finalX, Y=$finalY")
    }

    private fun findNodeByLabels(root: AccessibilityNodeInfo, labels: List<String>, fallbackId: String): AccessibilityNodeInfo? {
        if (fallbackId.isNotEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(fallbackId)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isClickable || node.isEditable || (node.parent?.isClickable == true)) {
                    return node
                }
            }
        }
        return null
    }

    private fun setTextToNode(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private suspend fun selectSpinnerValue(spinner: AccessibilityNodeInfo, value: String, root: AccessibilityNodeInfo) {
        humanClickAdvanced(spinner)
        delay(Random.nextLong(120, 200))
        
        var options = root.findAccessibilityNodeInfosByText(value)
        if (options.isEmpty()) {
            options = root.findAccessibilityNodeInfosByText(value.take(10))
        }
        
        if (options.isNotEmpty()) {
            humanClickAdvanced(options[0])
        } else {
            Log.w(TAG, "⚠️ Spinner option not found: $value")
        }
        delay(Random.nextLong(40, 80))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIF_CHANNEL_ID, "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String) = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
        .setContentTitle("🎯 VMAX Sniper Pro")
        .setContentText(content)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(content))
    }

    override fun onInterrupt() {
        isArmed = false
        isProcessing = false
    }

    override fun onDestroy() {
        serviceScope.cancel() 
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

// Helper function for MainScreen to check accessibility service
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, WorkflowEngine::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(expectedComponentName.flattenToString())
}

fun getDefaultJourneyDate(): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_MONTH, 1) // Tomorrow
    return String.format("%02d-%02d-%04d", 
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.YEAR)
    )
}
