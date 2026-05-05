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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * VMAX SNIPER - ELITE ULTIMATE FINAL
 * Production Ready | Dynamic Train Detection | Fast Text Input | Zero Lag
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
            const val ADD_PASSENGER_BTN = "$PKG:id/tv_add_passanger"
            const val PROCEED_BTN = "$PKG:id/btn_proceed"
            const val BOOK_NOW_BTN = "$PKG:id/btn_book_now"
            const val SEARCH_BTN = "$PKG:id/btn_search_trains"
            const val CAPTCHA_INPUT = "$PKG:id/et_captcha"
            const val CAPTCHA_IMAGE = "$PKG:id/iv_captcha"
            const val PAYMENT_CARDS = "$PKG:id/radio_cards_netbanking"
            const val PAYMENT_BHIM_UPI = "$PKG:id/radio_bhim_upi"
            const val PAYMENT_UPI_APPS = "$PKG:id/radio_upi_apps"
            const val INSURANCE_YES = "$PKG:id/radio_insurance_yes"
            const val INSURANCE_NO = "$PKG:id/radio_insurance_no"
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
        startForeground(1, buildNotification("⚡ VMAX ELITE ULTIMATE"))
        Log.d(TAG, "✅ PRODUCTION READY SNIPER ACTIVE")
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
        Log.d(TAG, "🔄 Elite Ultimate Reset")
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

    // ==================== NODE SEARCH (Optimized) ====================
    fun findNodeFast(root: AccessibilityNodeInfo, labels: List<String>, viewId: String): AccessibilityNodeInfo? {
        if (viewId.isNotEmpty()) {
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        for (label in labels) {
            root.findAccessibilityNodeInfosByText(label).firstOrNull { 
                it.text?.toString()?.trim().equals(label, ignoreCase = true) && it.isVisibleToUser 
            }?.let { return it }
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

    // ==================== FAST CHILD SCAN (Zero BFS) ====================
    private fun findClassNodeElite(trainNode: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        for (i in 0 until trainNode.childCount) {
            val child = trainNode.getChild(i) ?: continue
            val text = child.text?.toString()?.uppercase() ?: ""
            if (text.contains(className)) return child
        }
        return null
    }

    // ==================== SMART WEIGHT CALCULATION ====================
    private fun getSmartWeight(text: String, className: String): Int {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("available") || lowerText.contains("avail") -> 0
            lowerText.contains("rac") -> -1
            lowerText.contains("wl") -> extractWaitListNumber(lowerText)
            else -> 999
        }
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

    // ==================== EVENT DEDUPLICATION ====================
    private fun isDuplicateWindow(root: AccessibilityNodeInfo): Boolean {
        val hash = root.hashCode()
        if (hash == lastWindowHash) return true
        lastWindowHash = hash
        return false
    }

    // ==================== UI STABILIZER ====================
    private suspend fun ensureListIsReady() {
        performGlobalAction(GLOBAL_ACTION_SCROLL_FORWARD)
        delay(20)
        performGlobalAction(GLOBAL_ACTION_SCROLL_BACKWARD)
        delay(20)
    }

    // ==================== ✅ DYNAMIC TRAIN DETECTION (Regex Based) ====================
    private fun buildLocalNodeCache(root: AccessibilityNodeInfo): Map<String, AccessibilityNodeInfo> {
        val cache = mutableMapOf<String, AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString() ?: ""
            
            // ✅ Dynamic detection - कोई भी 5-अंकीय संख्या वाली ट्रेन पकड़ लेगा
            val trainNoMatch = Regex("\\d{5}").find(text)
            if (trainNoMatch != null) {
                val trainNo = trainNoMatch.value
                cache[trainNo] = node
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return cache
    }

    // ==================== SURGICAL STRIKE LOGIC ====================
    private suspend fun executeEliteStrike(trainNode: AccessibilityNodeInfo, trainNo: String, className: String): Boolean {
        val classNode = findClassNodeElite(trainNode, className) ?: return false
        
        val weight = getSmartWeight(classNode.text?.toString() ?: "", className)
        TrainPriorityManager.updateAvailability(trainNo, className, weight)

        if (weight <= 20) {
            if (AttackLock.tryLock(trainNo, className)) {
                val delayMs = if (className.contains("2A")) 6L else 8L
                delay(delayMs)
                
                val bookBtn = findClassNodeElite(classNode, "BOOK NOW") 
                    ?: findClassNodeElite(classNode, "अभी बुक करें")
                
                if (bookBtn != null && bookBtn.isVisibleToUser) {
                    val success = stableClick(bookBtn)
                    if (!success) {
                        AttackLock.reset()
                    }
                    return success
                }
            }
        }
        return false
    }

    // ==================== ELITE TUNED ENGINE ====================
    private suspend fun eliteAttack(isAc: Boolean): Boolean = coroutineScope {
        
        Log.d(TAG, "⚡ ELITE ULTIMATE ATTACK (${if (isAc) "AC" else "SLEEPER"})")
        
        ensureListIsReady()
        
        val root = rootInActiveWindow ?: return@coroutineScope false
        
        val localNodeCache = buildLocalNodeCache(root)
        val snapshot = TrainPriorityManager.getAvailabilitySnapshot()
        val targets = TrainPriorityManager.getFullAttackPlan(isAc, snapshot)
        
        if (targets.isEmpty()) {
            root.recycle()
            return@coroutineScope false
        }

        val winnerDeferred = CompletableDeferred<Boolean>()
        val attackJobs = mutableListOf<Job>()
        
        for (train in targets) {
            if (AttackLock.isLocked()) break

            val classOrder = TrainPriorityManager.getSmartClassOrder(train, isAc, snapshot)
            val bestClass = classOrder.firstOrNull() ?: continue

            val job = launch(Dispatchers.Default) {
                if (!isActive || AttackLock.isLocked()) return@launch
                
                val trainNode = localNodeCache[train.trainNumber] ?: return@launch
                
                if (executeEliteStrike(trainNode, train.trainNumber, bestClass)) {
                    runCatching { winnerDeferred.complete(true) }
                }
            }
            attackJobs.add(job)
        }

        val result = withTimeoutOrNull(250L) { winnerDeferred.await() } ?: false
        attackJobs.forEach { it.cancel() }
        
        if (result) {
            Log.d(TAG, "🔥 ELITE ULTIMATE STRIKE SUCCESSFUL!")
        }
        
        root.recycle()
        return@coroutineScope result
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
                
                if (targetHour == 10 && isState(EngineState.ATTACK) && !isReviewClicked) {
                    val success = eliteAttack(isAc = true)
                    if (success) {
                        transitionTo(EngineState.FORM)
                        fillPassengerFormUltraFast()
                    } else {
                        delay(50)
                        AttackLock.reset()
                        eliteAttack(isAc = true)
                        fillPassengerFormUltraFast()
                    }
                    return@launch
                }
                
                if (targetHour == 11 && isState(EngineState.ATTACK) && !isReviewClicked) {
                    val success = eliteAttack(isAc = false)
                    if (success) {
                        transitionTo(EngineState.FORM)
                        fillPassengerFormUltraFast()
                    } else {
                        delay(50)
                        AttackLock.reset()
                        eliteAttack(isAc = false)
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
    
    // ==================== ✅ IMPROVED CLICK WITH FOCUS ====================
    private fun stableClick(node: AccessibilityNodeInfo): Boolean {
        var target = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }
    
    // ==================== ✅ IMPROVED TEXT INPUT WITH FOCUS ====================
    fun setTextFast(node: AccessibilityNodeInfo, text: String) {
        // पहले focus दें (IRCTC keyboard के लिए जरूरी)
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        
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
        .setContentTitle("🎯 VMAX ELITE ULTIMATE")
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
    override fun onDestroy() { serviceScope.cancel(); super.onDestroy() }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WorkflowEngine::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains(expected.flattenToString())
}
