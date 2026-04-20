package com.aare.vmax.core.models

// =========================================================
// 🖥 SCREEN MODEL
// =========================================================
enum class ScreenType {
    HOME,
    DASHBOARD,
    SEARCH_RESULTS,
    PASSENGER_DETAILS,
    REVIEW,
    PAYMENT,
    CAPTCHA,
    CONFIRMATION,
    UNKNOWN
}

// =========================================================
// 🎯 ACTION MODEL
// =========================================================
enum class ActionType {
    CLICK,
    TYPE,
    SCROLL,
    LONG_PRESS,
    SET_TEXT,
    VERIFY,
    WAIT,
    NAVIGATE_BACK
}

// =========================================================
// ❌ ERROR MODEL
// =========================================================
enum class AutomationError {
    NODE_NOT_FOUND,
    TIMEOUT,
    ACTION_FAILED,
    NETWORK_ERROR,
    INVALID_STATE,
    PERMISSION_DENIED,
    LOOP_DETECTED
}

// =========================================================
// ⚡ STEP RESULT (CLEAN + DEBUG FRIENDLY)
// =========================================================
sealed class StepResult {

    object Success : StepResult()

    object Retry : StepResult()

    object Skipped : StepResult()

    data class Failed(
        val reason: String,
        val recoverable: Boolean = true
    ) : StepResult()

    data class Fatal(
        val reason: String
    ) : StepResult()
}

// =========================================================
// ⚙️ CONFIG MODEL
// =========================================================
data class StrikeConfig(
    val trainNumber: String,
    val passengerName: String,
    val bookingClass: String = "SL",
    val isTatkal: Boolean = true
)
