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
    
    val payment: PaymentDetails = PaymentDetails(),
    
    val autoUpgradation: Boolean = false,
    val confirmBerthsOnly: Boolean = false,
    val insurance: Boolean = true,
    
    val bookingOption: String = "None",
    val coachPreferred: Boolean = false,
    val coachId: String = "",
    val mobileNo: String = ""
) : Parcelable {
    
    fun isReadyForBooking(): Boolean {
        return trainNumber.isNotBlank() &&
               travelClass.isNotBlank() &&
               quota.isNotBlank() &&
               passengers.any { it.isFilled() } &&
               payment.isValid()
    }

    fun getPaymentSummary(): String {
        return if (payment.manualPayment) {
            "💳 Manual Payment - ${payment.getDisplayText()}"
        } else {
            "💳 Auto - ${payment.getDisplayText()} | OTP: ${if(payment.autofillOTP) "✅" else "❌"}"
        }
    }
}
