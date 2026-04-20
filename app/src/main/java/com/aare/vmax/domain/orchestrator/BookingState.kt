package com.aare.vmax.domain.orchestrator

// यह आपके बॉट को बताएगा कि बुकिंग किस स्टेज पर है
enum class BookingState {
    IDLE,
    INITIALIZING,
    LOGIN,
    TRAIN_SEARCH,
    PASSENGER_ENTRY,
    PAYMENT,
    COMPLETED,
    FAILED
}
