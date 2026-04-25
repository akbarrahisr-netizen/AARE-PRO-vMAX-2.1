package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SniperTask(
    val taskId: String,
    val trainNumber: String,
    val travelClass: String,
    val quota: String,
    val passengers: List<PassengerData>,
    
    // 💳 Payment Suite
    val paymentMethod: String = "UPI apps",
    val upiApp: String = "BHIM UPI",
    
    // ⚙️ Advanced Booking Options (From Screenshots 524981)
    val autoUpgradation: Boolean = false,
    val confirmBerthsOnly: Boolean = false,
    val insurance: Boolean = true,
    val bookingOption: String = "None", // None, Same Coach, 1 Lower, 2 Lower
    val coachPreferred: Boolean = false,
    val coachId: String = "",
    val mobileNo: String = "",
    val manualPayment: Boolean = false,
    val autofillOTP: Boolean = true
) : Parcelable
