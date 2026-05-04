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
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.vmax.sniper.core.model.*
import com.vmax.sniper.core.network.TimeSniper
import com.vmax.sniper.core.network.TimeSyncManager
import kotlinx.coroutines.*
import kotlin.math.min
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * VMAX SNIPER - ULTIMATE MERGED VERSION
 * तीनों वर्जन की बेस्ट चीज़ें | फर्स्ट-विनर लॉक | पैरेलल अटैक | स्टेबल
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

    // ==================== VARIABLES ====================
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val isProcessing = AtomicBoolean(false)
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var hasRefreshed = false
    private var isReviewClicked = false
    private var lastEventTime = 0L
    private val clipboard: ClipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    // ==================== SERVICE LIFECYCLE ====================
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 10
        }
        createNotificationChannel()
        startForeground(1, buildNotification("⚡ VMAX SNIPER ULTIMATE"))
        Log.d(TAG, "✅ ULTIMATE SNIPER ACTIVE")
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
                val advanceMs = activeTask!!.msAdvance.toLong().coerceIn(80, 200)
                
                serviceScope.launch {
                    TimeSyncManager.syncTime()
                    TimeSniper.scheduleFire(targetHour, advanceMs) {
                        isArmed = true
                        serviceScope.launch {
                            if (!hasRefreshed) {
                                triggerPreciseRefresh()
                                hasRefreshed = true
                            }
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
        lastEventTime = 0
        AttackLock.reset()
        Log.d(TAG, "🔄 Engine Reset")
    }

    private fun triggerPreciseRefresh() {
        val root = rootInActiveWindow ?: return
        try {
            val refreshBtn = findNodeFast(root, listOf("Search", "Refresh"), IRCTC.SEARCH_BTN)
            if (refreshBtn?.isClickable == true) {
                stableClick(refreshBtn)
                Log.d(TAG, "🔥 Refresh Triggered")
            }
        } finally { root.recycle() }
    }

    // ==================== NODE SEARCH ====================
    fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        for (label in labels) {
            root.findAccessibilityNodeInfosByText(label).firstOrNull { 
                it.text?.toString()?.trim().equals(label, ignoreCase = true) && it.isVisibleToUser 
            }?.let { return it }
        }
        return findNodeBFS(root, labels.map { it.uppercase() })
    }

    private fun findNodeBFS(root: AccessibilityNodeInfo, targetTexts: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.isVisibleToUser) continue
            
            val nodeText = node.text?.toString()?.trim()?.uppercase() ?: ""
            val nodeDesc = node.contentDescription?.toString()?.trim()?.uppercase() ?: ""
            
            if (targetTexts.any { nodeText.contains(it) || nodeDesc.contains(it) }) {
                return node
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    // ==================== DIRECT NODE ACCESS ====================
    private fun findNodeDirect(viewId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }
    }
    
    private fun findNodeDirectByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isVisibleToUser }
    }

    // ==================== MAIN EVENT HANDLER ====================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (event == null || !isArmed) return
        if (now - lastEventTime < 40) return
        lastEventTime = now
        
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && 
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            
        val packageName = event.packageName?.toString() ?: return
        if (packageName != IRCTC.PKG) return
        
        if (!isProcessing.compareAndSet(false, true)) return
        
        serviceScope.launch {
            try {
                val targetHour = if (activeTask?.triggerTime?.startsWith("10") == true) 10 else 11
                
                // Check for final pay button
                if (handleFinalPay()) return@launch
                
                // AC Attack (10 AM)
                if (targetHour == 10 && hasRefreshed && !isReviewClicked) {
                    val success = parallelAttackWithLock(TrainPriorityManager.getAcAttackOrder(), isAc = true)
                    if (success) {
                        fillPassengerFormUltraFast()
                    } else {
                        delay(30)
                        AttackLock.reset()
                        parallelAttackWithLock(TrainPriorityManager.getAcAttackOrder(), isAc = true)
                        fillPassengerFormUltraFast()
                    }
                    return@launch
                }
                
                // Sleeper Attack (11 AM)
                if (targetHour == 11 && hasRefreshed && !isReviewClicked) {
                    val success = parallelAttackWithLock(TrainPriorityManager.getSleeperAttackOrder(), isAc = false)
                    if (success) {
                        fillPassengerFormUltraFast()
                    } else {
                        delay(30)
                        AttackLock.reset()
                        parallelAttackWithLock(TrainPriorityManager.getSleeperAttackOrder(), isAc = false)
                        fillPassengerFormUltraFast()
                    }
                    return@launch
                }
                
                // Captcha Handling
                if (isReviewClicked) {
                    handleCaptcha()
                    return@launch
                }
                
                // Payment Handling
                if (handlePaymentPage()) {
                    selectPaymentUltraFast()
                    return@launch
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Event Error: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }
    }
    
    // ==================== PARALLEL ATTACK WITH LOCK ====================
    private suspend fun parallelAttackWithLock(
        targets: List<TrainPriorityManager.TrainTarget>,
        isAc: Boolean
    ): Boolean = coroutineScope {
        
        Log.d(TAG, "⚡ PARALLEL ATTACK (${if (isAc) "AC" else "SLEEPER"})")
        AttackLock.reset()
        
        val winnerDeferred = CompletableDeferred<Boolean>()
        val attackJobs = mutableListOf<Job>()
        
        for (train in targets) {
            val classes = if (isAc) train.acClasses else listOf(train.sleeperClass)
            for (className in classes) {
                val job = launch(Dispatchers.Default) {
                    if (!isActive) return@launch
                    if (attackTrainClassWithLock(train, className)) {
                        if (!winnerDeferred.isCompleted) {
                            winnerDeferred.complete(true)
                        }
                    }
                }
                attackJobs.add(job)
            }
        }
        
        val success = withTimeoutOrNull(150L) { winnerDeferred.await() } ?: false
        attackJobs.forEach { it.cancel() }
        
        if (success) Log.d(TAG, "🔥 FIRST SUCCESSFUL HIT!")
        return@coroutineScope success
    }
    
    private suspend fun attackTrainClassWithLock(
        train: TrainPriorityManager.TrainTarget,
        className: String
    ): Boolean {
        if (!isActive || AttackLock.isLocked()) return false
        
        val root = rootInActiveWindow ?: return false
        
        try {
            val trainNode = findNodeFast(root, listOf(train.trainNumber), "")
                ?: return false
            
            val classNode = findNodeFast(trainNode, listOf(className), "")
                ?: return false
            
            if (isClassAvailable(classNode)) {
                if (!AttackLock.tryLock(train.trainNumber, className)) {
                    return false
                }
                
                Log.d(TAG, "🎯 LOCKED: ${train.trainName} - $className")
                
                val bookBtn = findNodeFast(
                    classNode,
                    listOf("Book Now", "अभी बुक करें"),
                    IRCTC.BOOK_NOW_BTN
                )
                
                if (bookBtn != null && bookBtn.isClickable) {
                    return stableClick(bookBtn)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Attack failed: ${e.message}")
        } finally {
            root.recycle()
        }
        
        return false
    }
    
    private fun isClassAvailable(classNode: AccessibilityNodeInfo): Boolean {
        val text = classNode.text?.toString()?.lowercase() ?: return false
        if (text.contains("available") || text.contains("avail")) return true
        if (text.contains("wl")) {
            val wlNumber = extractWaitListNumber(text)
            return wlNumber < 15
        }
        return false
    }
    
    private fun extractWaitListNumber(text: String): Int {
        var num = 0
        var found = false
        for (c in text) {
            if (c in '0'..'9') {
                num = num * 10 + (c - '0')
                found = true
            } else if (found) break
        }
        return if (found) num else 99
    }
    
    // ==================== FAST FORM FILL ====================
    private suspend fun fillPassengerFormUltraFast() {
        val task = activeTask ?: return
        
        val passengers = task.passengers.take(4)
        
        for ((index, passenger) in passengers.withIndex()) {
            if (index > 0) {
                val addBtn = findNodeDirect(IRCTC.ADD_PASSENGER_BTN)
                addBtn?.let { stableClick(it) }
                delay(10)
            }
            
            val nameField = findNodeDirect(IRCTC.NAME_INPUT)
            val ageField = findNodeDirect(IRCTC.AGE_INPUT)
            
            nameField?.let { setTextFast(it, passenger.name) }
            ageField?.let { setTextFast(it, passenger.age) }
            
            if (passenger.gender.isNotBlank()) {
                selectGenderFast(passenger.gender)
            }
            delay(8)
        }
        
        selectInsuranceFast(task.insurance)
        clickProceedFast()
    }
    
    private suspend fun selectGenderFast(gender: String) {
        val spinner = findNodeDirect(IRCTC.GENDER_SPINNER)
        spinner?.let {
            stableClick(it)
            delay(10)
            val option = findNodeDirectByText(gender)
            option?.let { stableClick(it) }
        }
    }
    
    private suspend fun selectInsuranceFast(takeInsurance: Boolean) {
        val insuranceBtn = if (takeInsurance) {
            findNodeDirect(IRCTC.INSURANCE_YES)
        } else {
            findNodeDirect(IRCTC.INSURANCE_NO)
        }
        insuranceBtn?.let { stableClick(it) }
    }
    
    private suspend fun clickProceedFast() {
        val proceedBtn = findNodeDirect(IRCTC.PROCEED_BTN)
        proceedBtn?.let { 
            stableClick(it)
            isReviewClicked = true
            Log.d(TAG, "📋 Proceed to Review")
        }
    }
    
    // ==================== CAPTCHA HANDLING ====================
    private suspend fun handleCaptcha() {
        val captchaImage = findNodeDirect(IRCTC.CAPTCHA_IMAGE)
        val captchaInput = findNodeDirect(IRCTC.CAPTCHA_INPUT)
        
        if (captchaImage != null && captchaInput != null && activeTask?.captchaAutofill == true) {
            if (captchaInput.text.isNullOrBlank()) {
                Log.d(TAG, "🔐 Solving Captcha...")
                CaptchaSolver.executeBypass(this, captchaImage, captchaInput)
            }
        }
    }
    
    // ==================== PAYMENT HANDLING ====================
    private suspend fun handlePaymentPage(): Boolean {
        val paymentCards = findNodeDirect(IRCTC.PAYMENT_CARDS)
        return paymentCards != null
    }
    
    private suspend fun selectPaymentUltraFast() {
        Log.d(TAG, "💳 Payment Selection")
        
        // Try UPI Apps first
        var paymentNode = findNodeDirect(IRCTC.PAYMENT_UPI_APPS)
        if (paymentNode != null) {
            stableClick(paymentNode)
            delay(20)
            selectUPIAppFast()
            return
        }
        
        // Try BHIM UPI
        paymentNode = findNodeDirect(IRCTC.PAYMENT_BHIM_UPI)
        if (paymentNode != null) {
            stableClick(paymentNode)
            delay(20)
            selectUPIAppFast()
            return
        }
        
        // Try Cards/Netbanking
        paymentNode = findNodeDirect(IRCTC.PAYMENT_CARDS)
        paymentNode?.let { stableClick(it) }
        
        delay(30)
        val proceedBtn = findNodeDirect(IRCTC.PROCEED_BTN)
        proceedBtn?.let { stableClick(it) }
    }
    
    private suspend fun selectUPIAppFast() {
        val upiApps = listOf("Google Pay", "PhonePe", "Paytm", "Amazon Pay")
        
        for (app in upiApps) {
            val appNode = findNodeDirectByText(app)
            if (appNode != null) {
                stableClick(appNode)
                return
            }
        }
        
        val firstApp = findNodeDirect(IRCTC.PAYMENT_UPI_APPS)
        firstApp?.let { stableClick(it) }
    }
    
    private suspend fun handleFinalPay(): Boolean {
        val payBtn = findNodeDirect(IRCTC.PROCEED_BTN)
        if (payBtn != null && payBtn.isClickable) {
            val text = payBtn.text?.toString()?.lowercase() ?: ""
            if (text.contains("pay") || text.contains("proceed") || text.contains("भुगतान")) {
                stableClick(payBtn)
                updateNotification("✅ BOOKING SUCCESSFUL!")
                isArmed = false
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
                return true
            }
        }
        return false
    }
    
    // ==================== CLICK & TEXT ACTIONS ====================
    private fun stableClick(node: AccessibilityNodeInfo): Boolean {
        var target = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        
        if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            return true
        }
        return target?.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
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
                delay(50)
                val pasteBtn = findNodeDirectByText("Paste")
                pasteBtn?.let { stableClick(it) }
            }
        }
    }
    
    // ==================== NOTIFICATIONS ====================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("vmax_channel", "VMAX Sniper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(message: String) = NotificationCompat.Builder(this, "vmax_channel")
        .setContentTitle("🎯 VMAX SNIPER ULTIMATE")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()
    
    private fun updateNotification(message: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(1, buildNotification(message))
            Log.d(TAG, message)
        } catch (e: Exception) {}
    }
    
    override fun onInterrupt() { resetEngineState() }
    
    override fun onDestroy() { 
        serviceScope.cancel() 
        super.onDestroy()
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains(expected.flattenToString())
}
