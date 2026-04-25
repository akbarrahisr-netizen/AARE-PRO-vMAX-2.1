package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PaymentDetails(
    var paymentCategory: String = "UPI apps",
    var paymentMethod: String = "PhonePe",
    var upiId: String = "",
    var manualPayment: Boolean = false,
    var autofillOTP: Boolean = true
) : Parcelable {
    
    fun isValid(): Boolean {
        return when (paymentCategory) {
            "UPI apps" -> paymentMethod in listOf("PhonePe", "Paytm", "CRED UPI")
            "e-Wallets" -> paymentMethod in listOf("IRCTC", "Mobikwik™")
            // ✅ Fix: Added '+' after brackets to allow multiple characters in UPI ID
            "UPI ID" -> upiId.isNotBlank() && upiId.matches(Regex("^[a-zA-Z0-9.-]+@[a-zA-Z]+$"))
            "Netbanking, Credit/Debit/Cash Cards, Others" -> true
            else -> false
        }
    }
    
    fun getDisplayText(): String {
        return when (paymentCategory) {
            "UPI apps" -> "UPI - $paymentMethod"
            "e-Wallets" -> "Wallet - $paymentMethod"
            "UPI ID" -> "UPI ID: $upiId"
            else -> paymentCategory
        }
    }
}
