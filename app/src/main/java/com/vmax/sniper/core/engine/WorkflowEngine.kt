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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var isProcessing = false
    private var hasRefreshed = false
    private var currentPassengerIndex = 0
    private var watchdogJob: Job? = null
    private var isReviewClicked = false
    private var lastActionTime = 0L

    // ✅ Tatkal-Optimized Delays (Super Fast - दूसरे कोड से बेहतर)
    private suspend fun fastDelay() = delay(Random.nextLong(20, 40))
    private suspend fun mediumDelay() = delay(Random.nextLong(50, 80))
    private suspend fun addDelay() = delay(Random.nextLong(40, 60))

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

    private fun triggerPreciseRefresh() {
        val root = rootInActiveWindow ?: return
        try {
            val refreshBtn = findNodeFast(root, listOf("Search", "Refresh"), IRCTC.SEARCH_BTN)
            if (refreshBtn?.isClickable == true) humanClickFast(refreshBtn)
        } finally { root.recycle() }
    }

    // ==================== POWERFUL findNodeFast WITH RECURSIVE SEARCH ====================
    private fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
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

    private fun findNodeRecursive(node: AccessibilityNodeInfo, targetTexts: List<String>, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 15) return null
        
        val nodeText = node.text?.toString()?.trim()?.uppercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.trim()?.uppercase() ?: ""
        
        if (targetTexts.any { nodeText.contains(it) || nodeDesc.contains(it) }) {
            if (node.isVisibleToUser) return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, targetTexts, depth + 1)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    // ==================== MAIN onAccessibilityEvent (मर्ज्ड वर्जन) ====================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
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
                
                if (handlePopups(root)) return@launch
                
                // ✅ CAPTCHA HANDLING (दूसरे कोड से लिया गया)
                val captchaImageNodes = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_IMAGE)
                val captchaInputNodes = root.findAccessibilityNodeInfosByViewId(IRCTC.CAPTCHA_INPUT)
                
                if (captchaImageNodes.isNotEmpty() && captchaInputNodes.isNotEmpty() && activeTask?.captchaAutofill == true) {
                    Log.d(TAG, "Captcha detected!")
                    delay(200)
                    CaptchaSolver.executeBypass(this@WorkflowEngine, captchaImageNodes[0], captchaInputNodes[0])
                    return@launch
                }

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
                
                val payBtn = findNodeFast(root, listOf("PAY", "PROCEED", "CONTINUE", "भुगतान"), IRCTC.PROCEED_BTN)
                payBtn?.let {
                    humanClickFast(it)
                    updateNotification("Booking Submitted!")
                    isArmed = false
                    LocalBroadcastManager.getInstance(this@WorkflowEngine).sendBroadcast(
                        Intent(ACTION_SERVICE_STOPPED)
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Event Error: ${e.message}")
            } finally {
                isProcessing = false
                try { root?.recycle() } catch (e: Exception) {}
            }
        }
    }

    private suspend fun handlePopups(root: AccessibilityNodeInfo): Boolean {
        val okButton = findNodeFast(root, listOf("OK", "ठीक है"), "")
        if (okButton != null && okButton.isClickable) {
            humanClickFast(okButton)
            fastDelay()
            return true
        }
        return false
    }

    // ==================== PASSENGER DETAILS FILLING ====================
    private suspend fun findPassengerFormAndFill() {
        repeat(6) {
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
            delay(80)
        }
    }

    private suspend fun fillAllDetailsSuperFast() {
        val task = activeTask ?: return
        var currentRoot = rootInActiveWindow ?: return
        
        for (i in currentPassengerIndex until task.passengers.size) {
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
            isReviewClicked = true
            startWatchdog()
            updateNotification("Proceeding to Review...")
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
        val dateField = findNodeFast(root, emptyList(), IRCTC.DATE_INPUT) ?: return
        humanClickFast(dateField)
        mediumDelay()
        
        repeat(3) {
            val calRoot = rootInActiveWindow ?: return@repeat
            val currentMonthYear = findNodeFast(calRoot, emptyList(), IRCTC.CAL_MONTH_TEXT)?.text?.toString()?.uppercase() ?: ""
            
            if (currentMonthYear.contains(targetMonth)) {
                val dayNode = findNodeFast(calRoot, listOf(day), "")
                dayNode?.let {
                    humanClickFast(it)
                    delay(100)
                    findNodeFast(calRoot, emptyList(), IRCTC.CAL_OK_BTN)?.let { ok ->
                        humanClickFast(ok)
                    }
                }
                calRoot.recycle()
                return
            } else {
                findNodeFast(calRoot, emptyList(), IRCTC.CAL_NEXT_MONTH)?.let { next ->
                    humanClickFast(next)
                    delay(150)
                }
            }
            calRoot.recycle()
        }
    }

    private suspend fun selectPopupOption(spinnerId: String, optionText: String): Boolean {
        var root = rootInActiveWindow ?: return false
        val spinner = findNodeFast(root, emptyList(), spinnerId) ?: return false
        
        humanClickFast(spinner)
        mediumDelay()
        
        repeat(4) {
            root = rootInActiveWindow ?: return false
            val optionNode = findNodeFast(root, listOf(optionText), "")
            if (optionNode != null) {
                humanClickFast(optionNode)
                mediumDelay()
                return true
            }
            delay(100)
        }
        return false
    }

    private suspend fun selectSpecificClassAfterRefresh() {
        val task = activeTask ?: return
        
        repeat(15) {
            val root = rootInActiveWindow ?: return@repeat
            val classNode = findNodeFast(root, listOf(task.travelClass.code), "")
            if (classNode != null && classNode.isClickable) {
                humanClickFast(classNode)
                mediumDelay()
                findNodeFast(root, listOf("Book Now"), IRCTC.BOOK_NOW_BTN)?.let { btn ->
                    humanClickFast(btn)
                }
                root.recycle()
                return
            }
            root.recycle()
            delay(100)
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
            else -> {}
        }
        
        mediumDelay()
        findNodeFast(root, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)?.let { 
            humanClickFast(it)
            updateNotification("Payment Selected")
        }
    }

    // ✅ WATCHDOG: 4 seconds (Tatkal Optimized)
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            delay(4000)
            if (isReviewClicked && !isProcessing) {
                val root = rootInActiveWindow
                if (root != null) {
                    val proceedBtn = findNodeFast(root, listOf("Continue", "Proceed"), IRCTC.PROCEED_BTN)
                    if (proceedBtn?.isClickable == true) {
                        humanClickFast(proceedBtn)
                        Log.d(TAG, "Watchdog activated")
                    }
                    root.recycle()
                }
            }
        }
    }

    private fun humanClickFast(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, Random.nextLong(20, 40)))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    private fun setTextFast(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { 
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) 
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ==================== NOTIFICATION FUNCTIONS ====================
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
