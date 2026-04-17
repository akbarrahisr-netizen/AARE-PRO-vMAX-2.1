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
    private var isRunning = false

    // -----------------------------
    // MAIN FLOW CONTROLLER
    // -----------------------------
    fun startBookingFlow(rootNode: AccessibilityNodeInfo?) {

        if (isRunning || rootNode == null) return

        isRunning = true

        scope.launch {

            Log.d("VMAX_BRAIN", "🚀 Automation Flow Started")

            // STEP 1: SAFETY CHECK
            if (safety.isSystemBusy(rootNode)) {
                Log.d("VMAX_BRAIN", "System busy → aborting cycle")
                isRunning = false
                return@launch
            }

            if (safety.hasCriticalError(rootNode)) {
                Log.e("VMAX_BRAIN", "Critical error → stopping system")
                isRunning = false
                return@launch
            }

            if (safety.isCaptchaVisible(rootNode)) {
                Log.d("VMAX_BRAIN", "Captcha detected → waiting for user")
                isRunning = false
                return@launch
            }

            // STEP 2: TIMING SYNC
            val now = chrono.getCurrentExactTime()
            Log.d("VMAX_BRAIN", "Current Time: $now")

            // (Future: targetTime = 10:00:00 logic here)

            // STEP 3: HUMAN PAUSE
            human.thinkingPause()

            // STEP 4: FIELD DETECTION
            val nameLabel = spatial.findNodeByText(rootNode, "Passenger Name")

            if (nameLabel != null) {

                val inputField = spatial.findInputNextToLabel(nameLabel)

                human.humanDelay(120, 300)

                if (inputField != null) {
                    human.typeHumanLike(inputField, "MD AKBAR")
                } else {
                    Log.e("VMAX_BRAIN", "Input field not found")
                }
            }

            isRunning = false
            Log.d("VMAX_BRAIN", "Flow Completed")
        }
    }

    // -----------------------------
    // STOP SYSTEM SAFELY
    // -----------------------------
    fun stopSystem() {
        isRunning = false
        scope.cancel()
        Log.d("VMAX_BRAIN", "🛑 System Stopped")
    }
}
