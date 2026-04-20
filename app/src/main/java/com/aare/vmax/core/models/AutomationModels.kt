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
// 🎯 ACTION MODEL (Expanded for real automation)
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
// ❌ ERROR MODEL (More granular)
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
// ⚡ STEP RESULT (Production-grade)
// =========================================================
sealed class StepResult {

    object Success : StepResult()

    object Retry : StepResult()

    data class Failed(
        val reason: String,
        val recoverable: Boolean = true
    ) : StepResult()

    data class Fatal(
        val reason: String
    ) : StepResult()

    object Skipped : StepResult()
}
