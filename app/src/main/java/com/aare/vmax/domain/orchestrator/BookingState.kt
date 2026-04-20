package com.aare.vmax.domain.orchestrator

// 🔥 Booking flow control (State Machine)
enum class BookingState {

    IDLE,
    LOGIN_REQUIRED,
    PLAN_JOURNEY,
    REVIEW_JOURNEY,
    PASSENGER_DETAILS,
    PAYMENT_PAGE,
    COMPLETED,
    FAILED;

    // ✅ Check if process finished
    fun isFinalState(): Boolean {
        return this == COMPLETED || this == FAILED
    }

    // ✅ Next logical state (basic flow)
    fun next(): BookingState {
        return when (this) {
            IDLE -> LOGIN_REQUIRED
            LOGIN_REQUIRED -> PLAN_JOURNEY
            PLAN_JOURNEY -> REVIEW_JOURNEY
            REVIEW_JOURNEY -> PASSENGER_DETAILS
            PASSENGER_DETAILS -> PAYMENT_PAGE
            PAYMENT_PAGE -> COMPLETED
            COMPLETED, FAILED -> this
        }
    }
}
