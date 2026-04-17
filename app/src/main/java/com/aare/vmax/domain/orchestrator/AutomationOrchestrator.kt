package com.aare.vmax.domain.orchestrator

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.aare.vmax.core.engine.*
import com.aare.vmax.data.repository.PassengerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

class AutomationOrchestrator(
    private val spatial: SpatialHeuristicEngine,
    private val human: HumanMimeticEngine,
    private val chrono: ChronoEngine,
    private val safety: SafetyClutchEngine,
    private val passengerRepo: PassengerRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var isProcessing = false
    private var lastProcessedState: BookingState = BookingState.IDLE

    fun startBookingFlow(rootNode: AccessibilityNodeInfo?) {

        // ✅ FIXED: correct system check
        if (rootNode == null) return
        if (!AutomationCommandCenter.isSystemActive.value) return
        if (isProcessing) return

        val currentState = detectCurrentScreen(rootNode)

        if (currentState == BookingState.IDLE ||
            currentState == lastProcessedState
        ) return

        // 🔒 lock immediately (avoid race condition)
        isProcessing = true

        scope.launch {
            try {
                Log.d("VMAX_BRAIN", "🚀 STATE: $currentState")

                // safety check
                if (safety.isSystemBusy(rootNode)) {
                    Log.d("VMAX_BRAIN", "System busy, skipping...")
                    return@launch
                }

                when (currentState) {

                    BookingState.PASSENGER_DETAILS -> {

                        val passenger = withContext(Dispatchers.IO) {
                            passengerRepo.getPassenger().firstOrNull()
                        }

                        val name = passenger?.name ?: ""

                        if (name.isNotEmpty()) {
                            val label = spatial.findNodeByText(rootNode, "Passenger Name")
                            val input = spatial.findInputNextToLabel(label)

                            if (input != null) {
                                human.humanDelay(120, 300)
                                human.typeHumanLike(input, name)
                            }
                        }
                    }

                    BookingState.PAYMENT_PAGE -> {
                        val upi = spatial.findNodeByText(rootNode, "BHIM/ UPI")
                        if (upi != null) {
                            human.humanDelay(80, 150)
                            human.performHumanClick(upi)
                        }
                    }

                    BookingState.REVIEW_JOURNEY -> {
                        Log.d("VMAX_BRAIN", "Review page detected — waiting for confirmation")
                    }

                    BookingState.PLAN_JOURNEY -> {
                        Log.d("VMAX_BRAIN", "Plan Journey detected — idle safe state")
                    }

                    else -> {
                        Log.d("VMAX_BRAIN", "No actionable state")
                    }
                }

                lastProcessedState = currentState

            } catch (e: Exception) {
                Log.e("VMAX_BRAIN", "Error: ${e.message}")
            } finally {
                isProcessing = false
                Log.d("VMAX_BRAIN", "✅ LOCK RELEASED")
            }
        }
    }

    private fun detectCurrentScreen(rootNode: AccessibilityNodeInfo): BookingState {
        return when {

            spatial.findNodeByText(rootNode, "Plan My Journey") != null ->
                BookingState.PLAN_JOURNEY

            spatial.findNodeByText(rootNode, "Passenger Details") != null ->
                BookingState.PASSENGER_DETAILS

            spatial.findNodeByText(rootNode, "Review Journey") != null ->
                BookingState.REVIEW_JOURNEY

            spatial.findNodeByText(rootNode, "BHIM/ UPI") != null ->
                BookingState.PAYMENT_PAGE

            spatial.findNodeByText(rootNode, "Login") != null ->
                BookingState.LOGIN_REQUIRED

            else -> BookingState.IDLE
        }
    }

    fun stopSystem() {
        isProcessing = false
        lastProcessedState = BookingState.IDLE
        scope.cancel()
        Log.d("VMAX_BRAIN", "🛑 Orchestrator STOPPED")
    }
}
