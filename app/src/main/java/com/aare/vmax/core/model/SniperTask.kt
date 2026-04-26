package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// PaymentCategory यहाँ सुरक्षित है
enum class PaymentCategory(val display: String) {
    CARDS_NETBANKING("Cards & Netbanking"),
    BHIM_UPI("BHIM / UPI"),
    E_WALLETS("e-Wallets"),
    UPI_ID("UPI ID"),
    UPI_APPS("UPI Apps")
}

// 🎯 सिर्फ Master Task यहाँ रहेगा (बाकी सब अपनी-अपनी फाइलों में हैं)
@Parcelize
data class SniperTask(
    val taskId: String = java.util.UUID.randomUUID().toString(),
    
    val triggerTime: String = "10:00:00",
    val msAdvance: Int = 200,
    
    val trainNumber: String = "",
    val travelClass: String = "",
    val quota: String = "",
    
    val passengers: List<PassengerData> = emptyList(),
    val children: List<ChildData> = emptyList(),
    
    val payment: PaymentDetails = PaymentDetails(),
    
    val captchaAutofill: Boolean = true,
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
               passengers.isNotEmpty() &&
               payment.isValid()
    }

    fun getPaymentSummary(): String {
        return if (payment.manualPayment) {
            "💳 Manual - ${payment.getDisplayText()}"
        } else {
            "💳 Auto - ${payment.getDisplayText()} | OTP: ${if(payment.autofillOTP) "✅" else "❌"}"
        }
    }
}
