package com.aare.vmax.domain.orchestrator

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.engine.*
import kotlinx.coroutines.*

class AutomationOrchestrator(
    private val spatial: SpatialHeuristicEngine,
    private val human: HumanMimeticEngine,
    private val chrono: ChronoEngine,
    private val safety: SafetyClutchEngine
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 🔥 THE MEMORY LOCKS (दिमाग का लॉक)
    private var isProcessing = false
    private var lastProcessedState: BookingState = BookingState.IDLE

    fun startBookingFlow(rootNode: AccessibilityNodeInfo?) {
        // 1. अगर सिस्टम बंद है, या बॉट पहले से कुछ टाइप कर रहा है, तो वापस जाओ
        if (!AutomationCommandCenter.isRunning() || rootNode == null || isProcessing) return

        // 2. स्क्रीन पहचानो
        val currentState = detectCurrentScreen(rootNode)

        // 3. अगर स्क्रीन समझ नहीं आई, या इस स्क्रीन का काम पहले ही हो चुका है, तो इग्नोर करो
        if (currentState == BookingState.IDLE || currentState == lastProcessedState) return

        // 4. लॉक लगाओ और एक्शन शुरू करो
        scope.launch {
            isProcessing = true
            Log.d("VMAX_BRAIN", "🚀 Action Started for State: \$currentState")

            // --- असली ऑटोमेशन हाइब्रिड लॉजिक ---
            when (currentState) {
                BookingState.PASSENGER_DETAILS -> {
                    val nameLabel = spatial.findNodeByText(rootNode, "Passenger Name")
                    if (nameLabel != null) {
                        val inputField = spatial.findInputNextToLabel(nameLabel)
                        human.humanDelay(120, 300)
                        if (inputField != null) {
                            // (अगले स्टेप में हम इस नाम को UI वाले डेटाबेस से जोड़ेंगे)
                            human.typeHumanLike(inputField, "MD AKBAR")
                        }
                    }
                }
                BookingState.PAYMENT_PAGE -> {
                    Log.d("VMAX_BRAIN", "Payment Page Detected! Striking UPI...")
                    val upiOption = spatial.findNodeByText(rootNode, "BHIM/ UPI")
                    if (upiOption != null) {
                        human.performHumanClick(upiOption)
                    }
                }
                else -> {
                    Log.d("VMAX_BRAIN", "Waiting for target screen...")
                }
            }

            // 5. काम खत्म, अब मेमोरी में सेव कर लो कि यह पेज हो गया
            lastProcessedState = currentState
            isProcessing = false
            Log.d("VMAX_BRAIN", "✅ Action Completed. Lock Released.")
        }
    }

    // स्क्रीन पहचानने वाला रडार
    private fun detectCurrentScreen(rootNode: AccessibilityNodeInfo): BookingState {
        return when {
            spatial.findNodeByText(rootNode, "Plan My Journey") != null -> BookingState.PLAN_JOURNEY
            spatial.findNodeByText(rootNode, "Passenger Details") != null -> BookingState.PASSENGER_DETAILS
            spatial.findNodeByText(rootNode, "Review Journey") != null -> BookingState.REVIEW_JOURNEY
            spatial.findNodeByText(rootNode, "BHIM/ UPI") != null -> BookingState.PAYMENT_PAGE
            else -> BookingState.IDLE
        }
    }

    fun stopSystem() {
        isProcessing = false
        lastProcessedState = BookingState.IDLE
        scope.cancel()
        Log.d("VMAX_BRAIN", "🛑 Orchestrator Stopped & Memory Cleared.")
    }
}
