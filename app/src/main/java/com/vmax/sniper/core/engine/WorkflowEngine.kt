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

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var isProcessing = false
    private var hasRefreshed = false
    private var currentPassengerIndex = 0
    private var watchdogJob: Job? = null
    private var isReviewClicked = false

    private suspend fun fastDelay() = delay(Random.nextLong(100, 200))
    private suspend fun mediumDelay() = delay(Random.nextLong(300, 500))
    private suspend fun addDelay() = delay(Random.nextLong(200, 350))

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 10
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
                resetEngineState()
                val targetHour = if (activeTask!!.triggerTime.startsWith("10")) 10 else 11
                val advanceMs = activeTask!!.msAdvance.toLong().coerceIn(100, 250)
                TimeSniper.scheduleFire(targetHour, advanceMs) {
                    isArmed = true
                    serviceScope.launch { if (!hasRefreshed) { triggerPreciseRefresh(); hasRefreshed = true } }
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
        watchdogJob?.cancel()
    }

    private fun triggerPreciseRefresh() {
        val root = rootInActiveWindow ?: return
        try {
            val refreshBtn = findNodeFast(root, listOf("Search", "Refresh"), IRCTC.SEARCH_BTN)
            if (refreshBtn?.isClickable == true) humanClickFast(refreshBtn)
        } finally { root.recycle() }
    }

    private suspend fun selectSpecificClassAfterRefresh() {
        val task = activeTask ?: return
        delay(150)
        val root = rootInActiveWindow ?: return
        val classNode = findNodeFast(root, listOf(task.travelClass.code), "")
        if (classNode?.isClickable == true) {
            humanClickFast(classNode)
            mediumDelay()
            findNodeFast(root, listOf("Book Now", "अभी बुक करें"), IRCTC.BOOK_NOW_BTN)?.let { humanClickFast(it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isArmed || isProcessing) return
        val eventRoot = rootInActiveWindow ?: return

        if (eventRoot.packageName != IRCTC.PKG) {
            eventRoot.recycle()
            return
        }

        serviceScope.launch {
            isProcessing = true
            val freshRoot = rootInActiveWindow ?: return@launch
            try {
                if (handlePopups(freshRoot)) return@launch

                if (hasRefreshed && findNodeFast(freshRoot, listOf("SL", "3A"), "") != null && !isReviewClicked) {
                    selectSpecificClassAfterRefresh()
                    findPassengerFormAndFill()
                    return@launch
                }

                if (!isReviewClicked && findNodeFast(freshRoot, listOf("Name"), IRCTC.NAME_INPUT) != null) {
                    fillAllDetailsSuperFast()
                    return@launch
                }
                
                if (freshRoot.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS).isNotEmpty()) {
                    selectPaymentFast(freshRoot)
                    return@launch
                }
                
                val captchaInput = freshRoot.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
                if (captchaInput.isNotEmpty() && activeTask?.captchaAutofill == true) {
                    val captchaImage = freshRoot.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE)
                    if (captchaImage.isNotEmpty()) {
                        CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage[0], captchaInput[0])
                    }
                    return@launch
                }
                
                findNodeFast(freshRoot, listOf("Book Now", "Pay"), IRCTC.BOOK_NOW_BTN)?.let {
                    humanClickFast(it)
                    isArmed = false
                }
            } catch (e: Exception) { Log.e(TAG, "Event Error: ${e.message}") } finally {
                isProcessing = false
                try { freshRoot.recycle() } catch (e: Exception) {}
                try { eventRoot.recycle() } catch (e: Exception) {}
            }
        }
    }

    private suspend fun handlePopups(root: AccessibilityNodeInfo): Boolean {
        val okButton = findNodeFast(root, listOf("OK", "ठीक है", "YES"), "")
        if (okButton != null && okButton.isClickable) {
            humanClickFast(okButton)
            fastDelay()
            return true
        }
        return false
    }

    // ==================== 🎯 WATCHDOG with Screen Verification ====================
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            var retryCount = 0
            while (isArmed && isReviewClicked) {
                val root = rootInActiveWindow
                if (root != null && root.packageName == IRCTC.PKG) {
                    
                    val isReviewPage = findNodeFast(root, listOf("Review Journey", "Review"), "") != null
                    
                    if (isReviewPage) {
                        val proceedBtn = findNodeFast(root, listOf("Continue", "अभी बुक करें"), IRCTC.PROCEED_BTN)
                        if (proceedBtn?.isVisibleToUser == true && retryCount < 3) {
                            retryCount++
                            humanClickFast(proceedBtn)
                            delay(200)
                            Log.d(TAG, "🔄 Watchdog Retry #$retryCount")
                        }
                    } else {
                        retryCount = 0
                    }
                    
                    if (root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS).isNotEmpty()) {
                        Log.d(TAG, "🎯 Payment Page Detected by Watchdog!")
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
                            Log.d(TAG, "🔐 Captcha Detected by Watchdog!")
                            CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImage[0], captchaInput[0])
                        }
                    }
                }
                try { root?.recycle() } catch (e: Exception) {}
                delay(150)
            }
        }
    }

    private suspend fun findPassengerFormAndFill() {
        repeat(5) {
            val root = rootInActiveWindow
            if (root != null && findNodeFast(root, listOf("Name"), IRCTC.NAME_INPUT) != null) {
                fillAllDetailsSuperFast(); return
            }
            delay(200)
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
            selectSpinnerWithRetry(root, IRCTC.BOOKING_OPT_SPINNER, task.bookingOption.display)
        }
    }

    private suspend fun fillAllDetailsSuperFast() {
        val task = activeTask ?: return
        var currentRoot = rootInActiveWindow ?: return
        
        val nameFields = currentRoot.findAccessibilityNodeInfosByViewId(IRCTC.NAME_INPUT)
        for (i in currentPassengerIndex until min(nameFields.size, task.passengers.size)) {
            val passenger = task.passengers[i]
            if (i > 0) {
                findNodeFast(currentRoot, listOf("Add New"), IRCTC.ADD_PASSENGER_BTN)?.let {
                    humanClickFast(it); addDelay(); currentRoot = rootInActiveWindow ?: return
                }
            }

            val nameField = findNodeFast(currentRoot, listOf("Name"), IRCTC.NAME_INPUT)
            if (nameField != null && nameField.text.isNullOrBlank()) {
                setTextFast(nameField, passenger.name); fastDelay()
            }
            
            val ageField = findNodeFast(currentRoot, listOf("Age"), IRCTC.AGE_INPUT)
            if (ageField != null && ageField.text.isNullOrBlank()) {
                setTextFast(ageField, passenger.age); fastDelay()
            }
            
            if (passenger.gender.isNotBlank()) selectSpinnerWithRetry(currentRoot, IRCTC.GENDER_SPINNER, passenger.gender)
            if (passenger.berthPreference != "No Preference") selectSpinnerWithRetry(currentRoot, IRCTC.BERTH_SPINNER, passenger.berthPreference)
            if (passenger.meal != "No Food") selectSpinnerWithRetry(currentRoot, IRCTC.MEAL_SPINNER, passenger.meal)
            
            if (i > 0) {
                findNodeFast(currentRoot, listOf("Save"), IRCTC.SAVE_BTN)?.let {
                    humanClickFast(it); addDelay(); currentRoot = rootInActiveWindow ?: return
                }
            }
            currentPassengerIndex = i + 1
        }

        for (child in task.children) {
            findNodeFast(currentRoot, listOf("Add Infant", "शिशु जोड़ें"), IRCTC.ADD_CHILD_BTN)?.let {
                humanClickFast(it); addDelay(); currentRoot = rootInActiveWindow ?: return
            }
            findNodeFast(currentRoot, listOf("Infant Name"), IRCTC.CHILD_NAME)?.let { setTextFast(it, child.name); fastDelay() }
            if (child.ageRange.isNotBlank()) selectSpinnerWithRetry(currentRoot, IRCTC.CHILD_AGE, child.ageRange)
            if (child.gender.isNotBlank()) selectSpinnerWithRetry(currentRoot, IRCTC.CHILD_GENDER, child.gender)
        }

        awaitAdvanceOptionsFast()
        
        if (task.coachPreferred && task.coachId.isNotBlank()) {
            findNodeFast(currentRoot, emptyList(), IRCTC.COACH_PREF_INPUT)?.let { 
                setTextFast(it, task.coachId.uppercase()); fastDelay()
            }
        }
        if (task.mobileNo.isNotBlank()) {
            findNodeFast(currentRoot, emptyList(), IRCTC.MOBILE_INPUT)?.let { 
                setTextFast(it, task.mobileNo); fastDelay()
            }
        }
        
        mediumDelay()
        currentRoot = rootInActiveWindow ?: return
        findNodeFast(currentRoot, listOf("Review Journey", "Continue"), IRCTC.PROCEED_BTN)?.let {
            humanClickFast(it); isReviewClicked = true; startWatchdog()
        }
    }

    private suspend fun selectSpinnerWithRetry(root: AccessibilityNodeInfo, spinnerId: String, optionText: String): Boolean {
        val spinner = findNodeFast(root, emptyList(), spinnerId) ?: return false
        humanClickFast(spinner); delay(350)
        
        var retry = 0
        while (retry < 5) {
            val newRoot = rootInActiveWindow ?: return false
            val optionNode = findNodeFast(newRoot, listOf(optionText), "")
            if (optionNode != null) {
                humanClickFast(optionNode); delay(150)
                return true
            }
            delay(150); retry++
        }
        return false
    }

    private suspend fun selectPaymentFast(root: AccessibilityNodeInfo) {
        val task = activeTask ?: return
        isReviewClicked = false
        watchdogJob?.cancel()
        
        when (task.payment.category) {
            PaymentCategory.CARDS_NETBANKING -> {
                var node = findNodeFast(root, emptyList(), IRCTC.PAYMENT_CARDS)
                if (node == null) node = findNodeFast(root, listOf("Cards", "Netbanking"), "")
                node?.let { humanClickFast(it) }
            }
            PaymentCategory.BHIM_UPI -> {
                var node = findNodeFast(root, emptyList(), IRCTC.PAYMENT_BHIM_UPI)
                if (node == null) node = findNodeFast(root, listOf("BHIM/UPI", "UPI"), "")
                node?.let { humanClickFast(it) }
            }
            PaymentCategory.E_WALLETS -> {
                var node = findNodeFast(root, emptyList(), IRCTC.PAYMENT_EWALLET)
                if (node == null) node = findNodeFast(root, listOf("e-Wallets", "Wallet"), "")
                node?.let { 
                    humanClickFast(it); mediumDelay()
                    selectSpinnerWithRetry(root, "", task.payment.walletType.display)
                }
            }
            PaymentCategory.UPI_ID -> {
                var node = findNodeFast(root, emptyList(), IRCTC.PAYMENT_UPI_ID)
                if (node == null) node = findNodeFast(root, listOf("UPI ID"), "")
                node?.let { 
                    humanClickFast(it); mediumDelay()
                    if (task.payment.upiId.isNotBlank()) {
                        val inputNode = findNodeFast(root, emptyList(), IRCTC.UPI_ID_INPUT)
                            ?: findNodeFast(root, listOf("Enter UPI ID"), "")
                        inputNode?.let { setTextFast(it, task.payment.upiId) }
                    }
                }
            }
            PaymentCategory.UPI_APPS -> {
                var node = findNodeFast(root, emptyList(), IRCTC.PAYMENT_UPI_APPS)
                if (node == null) node = findNodeFast(root, listOf("UPI apps"), "")
                node?.let { 
                    humanClickFast(it); mediumDelay()
                    selectSpinnerWithRetry(root, "", task.payment.upiApp.display)
                }
            }
        }
        mediumDelay()
        val proceedBtn = findNodeFast(root, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)
            ?: findNodeFast(root, listOf("Continue", "Proceed to Pay"), "")
        proceedBtn?.let { humanClickFast(it) }
        updateNotification("💳 Payment Selected")
    }

    private fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty() && nodes[0].isVisibleToUser) return nodes[0]
        }
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) if (node.isVisibleToUser) return node
        }
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText("")
            for (node in nodes) {
                if (node.text?.toString()?.contains(label, true) == true && node.isVisibleToUser) return node
            }
        }
        return null
    }

    private fun humanClickFast(node: AccessibilityNodeInfo) {
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, Random.nextLong(80, 120))).build()
        dispatchGesture(gesture, null, null)
    }

    private fun setTextFast(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(content: String) = getSystemService(NotificationManager::class.java).notify(1, buildNotification(content))

    private fun buildNotification(content: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 VMAX Sniper").setContentText(content).setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()

    override fun onInterrupt() { resetEngineState() }
    override fun onDestroy() { serviceScope.cancel(); super.onDestroy() }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains(expected.flattenToString())
}
