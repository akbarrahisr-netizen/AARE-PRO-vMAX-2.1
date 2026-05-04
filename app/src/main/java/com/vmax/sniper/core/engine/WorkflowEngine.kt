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
 * VMAX SNIPER - ULTIMATE FINAL VERSION
 * स्टेट मशीन | स्मार्ट अटैक प्लान | फर्स्ट-विनर लॉक | एंटी-डिटेक्शन | मेमोरी सेफ
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

    // ==================== STATE MACHINE ====================
    enum class EngineState {
        IDLE, REFRESH, ATTACK, FORM, CAPTCHA, PAYMENT, DONE
    }
    private var currentState = EngineState.IDLE
    private var lastStateTransition = 0L

    // ==================== VARIABLES ====================
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val isProcessing = AtomicBoolean(false)
    private var activeTask: SniperTask? = null
    private var isArmed = false
    private var hasRefreshed = false
    private var isReviewClicked = false
    private var lastEventTime = 0L
    private var lastWindowHash = 0
    private val clipboard: ClipboardManager by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    // ==================== HELPERS ====================
    private inline fun <T> withRoot(block: (AccessibilityNodeInfo) -> T?): T? {
        val root = rootInActiveWindow ?: return null
        return try { block(root) } finally { root.recycle() }
    }

    private fun transitionTo(newState: EngineState) {
        val now = System.currentTimeMillis()
        if (now - lastStateTransition < 100) return
        Log.d(TAG, "📍 State: $currentState → $newState")
        currentState = newState
        lastStateTransition = now
    }

    private fun isState(expected: EngineState): Boolean = currentState == expected

    private suspend fun humanDelay() = delay(Random.nextLong(8, 18))
    private suspend fun adaptiveDelay() = delay(Random.nextLong(5, 15))

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
                        transitionTo(EngineState.REFRESH)
                        serviceScope.launch {
                            if (!hasRefreshed) {
                                triggerPreciseRefresh()
                                hasRefreshed = true
                                transitionTo(EngineState.ATTACK)
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
        lastWindowHash = 0
        AttackLock.reset()
        TrainPriorityManager.clearCache()
        transitionTo(EngineState.IDLE)
        Log.d(TAG, "🔄 Engine Reset")
    }

    private fun triggerPreciseRefresh() {
        withRoot { root ->
            val refreshBtn = findNodeFast(root, listOf("Search", "Refresh"), IRCTC.SEARCH_BTN)
            if (refreshBtn?.isClickable == true) {
                stableClick(refreshBtn)
                Log.d(TAG, "🔥 Refresh Triggered")
            }
        }
    }

    // ==================== OPTIMIZED NODE SEARCH ====================
    fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        for (label in labels) {
            root.findAccessibilityNodeInfosByText(label).firstOrNull { 
                it.text?.toString()?.trim().equals(label, ignoreCase = true) && it.isVisibleToUser 
            }?.let { return it }
        }
        for (label in labels) {
            root.findAccessibilityNodeInfosByText(label).firstOrNull { 
                it.text?.toString()?.trim().contains(label, ignoreCase = true) && it.isVisibleToUser 
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
        return withRoot { root ->
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }
        }
    }
    
    private fun findNodeDirectByText(text: String): AccessibilityNodeInfo? {
        return withRoot { root ->
            root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isVisibleToUser }
        }
    }

    // ==================== EVENT DEDUPLICATION ====================
    private fun isDuplicateWindow(root: AccessibilityNodeInfo): Boolean {
        val hash = root.hashCode()
        if (hash == lastWindowHash) return true
        lastWindowHash = hash
        return false
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
                
                if (handleFinalPay()) return@launch
                
                // 🔥 AC ATTACK (10 AM) with SMART PLAN
                if (targetHour == 10 && isState(EngineState.ATTACK) && !isReviewClicked) {
                    val liveSnapshot = TrainPriorityManager.getAvailabilitySnapshot()
                    val smartPlan = TrainPriorityManager.getFullAttackPlan(isAc = true, availabilitySnapshot = liveSnapshot)
                    val success = smartAttackWithLock(smartPlan, isAc = true)
                    
                    if (success) {
                        transitionTo(EngineState.FORM)
                        fillPassengerFormUltraFast()
                    } else {
                        delay(50)
                        AttackLock.reset()
                        val retryPlan = TrainPriorityManager.getFullAttackPlan(isAc = true)
                        smartAttackWithLock(retryPlan, isAc = true)
                        fillPassengerFormUltraFast()
                    }
                    return@launch
                }
                
                // 🔥 SLEEPER ATTACK (11 AM) with SMART PLAN
                if (targetHour == 11 && isState(EngineState.ATTACK) && !isReviewClicked) {
                    val liveSnapshot = TrainPriorityManager.getAvailabilitySnapshot()
                    val smartPlan = TrainPriorityManager.getFullAttackPlan(isAc = false, availabilitySnapshot = liveSnapshot)
                    val success = smartAttackWithLock(smartPlan, isAc = false)
                    
                    if (success) {
                        transitionTo(EngineState.FORM)
                        fillPassengerFormUltraFast()
                    } else {
                        delay(50)
                        AttackLock.reset()
                        val retryPlan = TrainPriorityManager.getFullAttackPlan(isAc = false)
                        smartAttackWithLock(retryPlan, isAc = false)
                        fillPassengerFormUltraFast()
                    }
                    return@launch
                }
                
                if (isReviewClicked && isState(EngineState.CAPTCHA)) {
                    handleCaptcha()
                    return@launch
                }
                
                if (isState(EngineState.PAYMENT)) {
                    if (handlePaymentPage()) {
                        selectPaymentUltraFast()
                    }
                    return@launch
                }
                
                if (isReviewClicked && currentState != EngineState.CAPTCHA && currentState != EngineState.PAYMENT) {
                    transitionTo(EngineState.CAPTCHA)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Event Error: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }
    }
    
    // ==================== SMART ATTACK WITH LOCK ====================
    private suspend fun smartAttackWithLock(
        targets: List<TrainPriorityManager.TrainTarget>,
        isAc: Boolean
    ): Boolean = coroutineScope {
        
        Log.d(TAG, "⚡ SMART ATTACK (${if (isAc) "AC" else "SLEEPER"})")
        Log.d(TAG, TrainPriorityManager.getStats())
        
        AttackLock.reset()
        
        val availabilitySnapshot = TrainPriorityManager.getAvailabilitySnapshot()
        
        if (targets.isEmpty()) {
            Log.w(TAG, "⚠️ No targets available")
            return@coroutineScope false
        }
        
        val winnerDeferred = CompletableDeferred<Boolean>()
        val attackJobs = mutableListOf<Job>()
        
        for (train in targets) {
            TrainPriorityManager.markAttacked(train.trainNumber)
            val classOrder = TrainPriorityManager.getSmartClassOrder(train, isAc, availabilitySnapshot)
            
            for (className in classOrder) {
                val job = launch(Dispatchers.Default) {
                    if (!isActive || AttackLock.isLocked()) return@launch
                    
                    if (attackTrainClassWithLock(train, className)) {
                        if (!winnerDeferred.isCompleted) {
                            TrainPriorityManager.incrementSuccess(train.trainNumber)
                            TrainPriorityManager.updateAvailability(train.trainNumber, className, 0)
                            winnerDeferred.complete(true)
                        }
                    }
                }
                attackJobs.add(job)
            }
        }
        
        val success = withTimeoutOrNull(350L) { winnerDeferred.await() } ?: false
        attackJobs.forEach { it.cancel() }
        
        if (success) {
            Log.d(TAG, "🔥 FIRST SUCCESSFUL HIT!")
            Log.d(TAG, TrainPriorityManager.getStats())
        } else {
            Log.w(TAG, "⚠️ No hits in this attack cycle")
        }
        
        return@coroutineScope success
    }
    
    private suspend fun attackTrainClassWithLock(
        train: TrainPriorityManager.TrainTarget,
        className: String
    ): Boolean {
        if (!isActive) return false
        
        return withRoot { root ->
            val trainNode = findNodeFast(root, listOf(train.trainNumber), "")
                ?: return@withRoot false
            
            val classNode = findNodeFast(trainNode, listOf(className), "")
                ?: return@withRoot false
            
            if (isClassAvailable(classNode, train.trainNumber, className)) {
                if (!AttackLock.tryLock(train.trainNumber, className)) {
                    return@withRoot false
                }
                
                Log.d(TAG, "🎯 LOCKED: ${train.trainName} - $className")
                
                val bookBtn = findNodeFast(
                    classNode,
                    listOf("Book Now", "अभी बुक करें"),
                    IRCTC.BOOK_NOW_BTN
                )
                
                if (bookBtn != null && bookBtn.isClickable) {
                    return@withRoot stableClick(bookBtn)
                }
            }
            false
        } ?: false
    }
    
    private fun isClassAvailable(classNode: AccessibilityNodeInfo, trainNumber: String, className: String): Boolean {
        val text = classNode.text?.toString()?.lowercase() ?: return false
        
        var wlNumber = 999
        if (text.contains("wl")) {
            wlNumber = extractWaitListNumber(text)
            TrainPriorityManager.updateAvailability(trainNumber, className, wlNumber)
        } else if (text.contains("available") || text.contains("avail")) {
            wlNumber = 0
            TrainPriorityManager.updateAvailability(trainNumber, className, 0)
        } else if (text.contains("rac")) {
            wlNumber = -1
            TrainPriorityManager.updateAvailability(trainNumber, className, -1)
        }
        
        return wlNumber < 15
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
                adaptiveDelay()
            }
            
            val nameField = findNodeDirect(IRCTC.NAME_INPUT)
            val ageField = findNodeDirect(IRCTC.AGE_INPUT)
            
            nameField?.let { setTextFast(it, passenger.name) }
            ageField?.let { setTextFast(it, passenger.age) }
            
            if (passenger.gender.isNotBlank()) {
                selectGenderFast(passenger.gender)
            }
            adaptiveDelay()
        }
        
        selectInsuranceFast(task.insurance)
        clickProceedFast()
    }
    
    private suspend fun selectGenderFast(gender: String) {
        val spinner = findNodeDirect(IRCTC.GENDER_SPINNER)
        spinner?.let {
            stableClick(it)
            adaptiveDelay()
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
            transitionTo(EngineState.CAPTCHA)
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
                transitionTo(EngineState.PAYMENT)
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
        
        var paymentNode = findNodeDirect(IRCTC.PAYMENT_UPI_APPS)
        if (paymentNode != null) {
            stableClick(paymentNode)
            adaptiveDelay()
            selectUPIAppFast()
            return
        }
        
        paymentNode = findNodeDirect(IRCTC.PAYMENT_BHIM_UPI)
        if (paymentNode != null) {
            stableClick(paymentNode)
            adaptiveDelay()
            selectUPIAppFast()
            return
        }
        
        paymentNode = findNodeDirect(IRCTC.PAYMENT_CARDS)
        paymentNode?.let { stableClick(it) }
        
        humanDelay()
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
            if ((text.contains("pay") && text.contains("₹")) || 
                text.contains("proceed") || 
                text.contains("भुगतान")) {
                stableClick(payBtn)
                updateNotification("✅ BOOKING SUCCESSFUL!")
                transitionTo(EngineState.DONE)
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
                humanDelay()
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
