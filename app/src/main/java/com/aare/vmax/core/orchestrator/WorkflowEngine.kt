package com.aare.vmax.core.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.aare.vmax.core.model.*
import com.aare.vmax.core.network.TimeSyncManager
import kotlinx.coroutines.*
import java.util.Calendar
import kotlin.math.min

class WorkflowEngine : AccessibilityService() {

    companion object {
        private const val TAG = "VMAX_Sniper_77"
        const val ACTION_START_SNIPER = "com.aare.vmax.START_SNIPER"
        const val EXTRA_TASK = "extra_task"
        private const val NOTIF_CHANNEL_ID = "vmax_sniper"
        private const val NOTIF_ID = 1
        
        object IRCTC {
            const val PKG = "cris.org.in.prs.ima"
            
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
        if (!isArmed) return
        val root = rootInActiveWindow ?: return

        try {
            if (root.packageName == IRCTC.PKG) {
                
                val nameFields = root.findAccessibilityNodeInfosByViewId(IRCTC.NAME_INPUT)
                if (nameFields.isNotEmpty()) {
                    fillPassengerDetails(root)
                    return
                }
                
                val childNameFields = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_NAME)
                if (childNameFields.isNotEmpty() && activeTask?.children?.isNotEmpty() == true) {
                    fillChildDetails(root)
                    return
                }
                
                val autoUpgradeCheck = root.findAccessibilityNodeInfosByViewId(IRCTC.AUTO_UPGRADE_CHECK)
                if (autoUpgradeCheck.isNotEmpty()) {
                    setBookingOptions(root)
                    return
                }
                
                val coachInput = root.findAccessibilityNodeInfosByViewId(IRCTC.COACH_PREF_INPUT)
                if (coachInput.isNotEmpty()) {
                    setCoachAndMobile(root)
                    return
                }
                
                val paymentCards = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS)
                if (paymentCards.isNotEmpty()) {
                    selectPaymentMethod(root)
                    return
                }
                
                val captchaInput = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
                if (captchaInput.isNotEmpty() && activeTask?.captchaAutofill == true) {
                    handleCaptcha(root)
                    return
                }
                
                val bookBtn = root.findAccessibilityNodeInfosByViewId(IRCTC.BOOK_NOW_BTN)
                if (bookBtn.isNotEmpty()) {
                    clickNode(bookBtn[0])
                    updateNotification("✅ Booking Submitted!")
                    isArmed = false 
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        } finally {
            root.recycle()
        }
    }

    private fun fillPassengerDetails(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        val nameFields = root.findAccessibilityNodeInfosByViewId(IRCTC.NAME_INPUT)
        val ageFields = root.findAccessibilityNodeInfosByViewId(IRCTC.AGE_INPUT)
        val genderSpinners = root.findAccessibilityNodeInfosByViewId(IRCTC.GENDER_SPINNER)
        
        for (i in currentPassengerIndex until min(nameFields.size, task.passengers.size)) {
            val passenger = task.passengers[i]
            
            if (nameFields[i].text.isNullOrBlank()) {
                setTextToNode(nameFields[i], passenger.name)
                Thread.sleep(30)
            }
            if (i < ageFields.size && ageFields[i].text.isNullOrBlank()) {
                setTextToNode(ageFields[i], passenger.age)
                Thread.sleep(30)
            }
            if (i < genderSpinners.size) {
                selectSpinnerValue(genderSpinners[i], passenger.gender, root)
                Thread.sleep(50)
            }
            currentPassengerIndex = i + 1
        }
        
        if (currentPassengerIndex < task.passengers.size) {
            val addBtn = root.findAccessibilityNodeInfosByViewId(IRCTC.ADD_PASSENGER_BTN)
                .ifEmpty { root.findAccessibilityNodeInfosByText("Add Passenger") }
            if (addBtn.isNotEmpty()) {
                clickNode(addBtn[0])
                Thread.sleep(150)
            }
        }
    }

    private fun fillChildDetails(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        val childNameFields = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_NAME)
        val childAgeSpinners = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_AGE)
        val childGenderSpinners = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_GENDER)
        
        for (i in 0 until min(childNameFields.size, task.children.size)) {
            val child = task.children[i]
            
            if (childNameFields[i].text.isNullOrBlank()) {
                setTextToNode(childNameFields[i], child.name)
                Thread.sleep(30)
            }
            if (i < childAgeSpinners.size && child.ageRange.isNotBlank()) {
                selectSpinnerValue(childAgeSpinners[i], child.ageRange, root)
                Thread.sleep(30)
            }
            if (i < childGenderSpinners.size && child.gender.isNotBlank()) {
                selectSpinnerValue(childGenderSpinners[i], child.gender, root)
                Thread.sleep(30)
            }
        }
    }

    private fun setBookingOptions(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        
        val autoUpgrade = root.findAccessibilityNodeInfosByViewId(IRCTC.AUTO_UPGRADE_CHECK)
        if (autoUpgrade.isNotEmpty() && task.autoUpgradation) {
            if (!autoUpgrade[0].isChecked) clickNode(autoUpgrade[0])
            Thread.sleep(30)
        }
        
        val confirmBerth = root.findAccessibilityNodeInfosByViewId(IRCTC.CONFIRM_BERTH_CHECK)
        if (confirmBerth.isNotEmpty() && task.confirmBerthsOnly) {
            if (!confirmBerth[0].isChecked) clickNode(confirmBerth[0])
            Thread.sleep(30)
        }
        
        if (task.insurance) {
            val insuranceYes = root.findAccessibilityNodeInfosByViewId(IRCTC.INSURANCE_YES)
            if (insuranceYes.isNotEmpty() && !insuranceYes[0].isChecked) clickNode(insuranceYes[0])
        } else {
            val insuranceNo = root.findAccessibilityNodeInfosByViewId(IRCTC.INSURANCE_NO)
            if (insuranceNo.isNotEmpty() && !insuranceNo[0].isChecked) clickNode(insuranceNo[0])
        }
        Thread.sleep(50)
        
        val bookingOptSpinner = root.findAccessibilityNodeInfosByViewId(IRCTC.BOOKING_OPT_SPINNER)
        // ✅ यहाँ बग फिक्स किया गया है (String Check)
        if (bookingOptSpinner.isNotEmpty() && task.bookingOption != "None" && task.bookingOption.isNotBlank()) {
            selectSpinnerValue(bookingOptSpinner[0], task.bookingOption, root)
            Thread.sleep(50)
        }
    }

    private fun setCoachAndMobile(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        
        if (task.coachPreferred && task.coachId.isNotBlank()) {
            val coachInput = root.findAccessibilityNodeInfosByViewId(IRCTC.COACH_PREF_INPUT)
            if (coachInput.isNotEmpty() && coachInput[0].text.isNullOrBlank()) {
                setTextToNode(coachInput[0], task.coachId.uppercase())
                Thread.sleep(30)
            }
        }
        
        if (task.mobileNo.isNotBlank()) {
            val mobileInput = root.findAccessibilityNodeInfosByViewId(IRCTC.MOBILE_INPUT)
            if (mobileInput.isNotEmpty() && mobileInput[0].text.isNullOrBlank()) {
                setTextToNode(mobileInput[0], task.mobileNo)
                Thread.sleep(30)
            }
        }
    }

    private fun selectPaymentMethod(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        
        // ✅ यहाँ Payment वाला बग फिक्स किया गया है
        when (task.payment.category) {
            PaymentCategory.CARDS_NETBANKING -> {
                val cardsRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS)
                if (cardsRadio.isNotEmpty()) clickNode(cardsRadio[0])
            }
            PaymentCategory.BHIM_UPI -> {
                val upiRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_BHIM_UPI)
                if (upiRadio.isNotEmpty()) clickNode(upiRadio[0])
            }
            PaymentCategory.E_WALLETS -> {
                val walletRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_EWALLET)
                if (walletRadio.isNotEmpty()) clickNode(walletRadio[0])
                Thread.sleep(100)
                val walletSpinner = root.findAccessibilityNodeInfosByText(task.payment.walletType)
                if (walletSpinner.isNotEmpty()) clickNode(walletSpinner[0])
            }
            PaymentCategory.UPI_ID -> {
                val upiIdRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_UPI_ID)
                if (upiIdRadio.isNotEmpty()) clickNode(upiIdRadio[0])
                Thread.sleep(100)
                if (task.payment.upiId.isNotBlank()) {
                    val upiIdInput = root.findAccessibilityNodeInfosByViewId(IRCTC.UPI_ID_INPUT)
                    if (upiIdInput.isNotEmpty()) setTextToNode(upiIdInput[0], task.payment.upiId)
                }
            }
            PaymentCategory.UPI_APPS -> {
                val upiAppsRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_UPI_APPS)
                if (upiAppsRadio.isNotEmpty()) clickNode(upiAppsRadio[0])
                Thread.sleep(100)
                val upiAppSpinner = root.findAccessibilityNodeInfosByText(task.payment.upiApp)
                if (upiAppSpinner.isNotEmpty()) clickNode(upiAppSpinner[0])
            }
        }
        Thread.sleep(100)
        
        if (task.payment.manualPayment) {
            val manualCheckbox = root.findAccessibilityNodeInfosByText("I will fill payment information manually")
            if (manualCheckbox.isNotEmpty() && !manualCheckbox[0].isChecked) {
                clickNode(manualCheckbox[0])
            }
        }
        
        val proceedBtn = root.findAccessibilityNodeInfosByViewId(IRCTC.PROCEED_BTN)
            .ifEmpty { root.findAccessibilityNodeInfosByViewId(IRCTC.CONTINUE_BTN) }
        if (proceedBtn.isNotEmpty()) clickNode(proceedBtn[0])
    }

    private fun handleCaptcha(root: AccessibilityNodeInfo) {
        updateNotification("🔐 Captcha Detected - Manual Entry Required")
        Log.d(TAG, "Captcha Phase Triggered")
        // TODO: ML Kit Captcha integration 
    }

    private fun setTextToNode(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun selectSpinnerValue(spinner: AccessibilityNodeInfo, value: String, root: AccessibilityNodeInfo) {
        clickNode(spinner)
        Thread.sleep(150) 
        
        var options = root.findAccessibilityNodeInfosByText(value)
        if (options.isEmpty()) {
            options = root.findAccessibilityNodeInfosByText(value.take(10))
        }
        
        if (options.isNotEmpty()) {
            clickNode(options[0])
        } else {
            Log.w(TAG, "⚠️ Spinner option not found: $value")
        }
        Thread.sleep(50)
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
    }

    override fun onDestroy() {
        serviceScope.cancel() 
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
