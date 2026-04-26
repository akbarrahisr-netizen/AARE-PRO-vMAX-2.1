package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

// ==================== 1. PASSENGER DATA (Points 13-22) ====================
@Parcelize
data class PassengerData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var age: String = "",
    var gender: String = "Male",           // Male / Female / Transgender
    var berthPreference: String = "No Preference",
    var meal: String = "No Food",
    var optBerth: Boolean = false,
    var bedRoll: Boolean = false,
    var availConcession: Boolean = false,
    var nationality: String = "India"
) : Parcelable {
    fun isFilled() = name.isNotBlank() && age.isNotBlank()
}

// ==================== 2. CHILD DATA (Points 28-40) ====================
@Parcelize
data class ChildData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var ageRange: String = "Below 1 yr",   // Below 1 yr / 1 yr / 2 yr / 3 yr / 4 yr
    var gender: String = "Male"
) : Parcelable

// ==================== 3. ENUMS FOR PAYMENT ====================
enum class PaymentCategory(val display: String) {
    CARDS_NETBANKING("Cards & Netbanking"),
    BHIM_UPI("BHIM / UPI"),
    E_WALLETS("e-Wallets"),
    UPI_ID("UPI ID"),
    UPI_APPS("UPI Apps")
}

enum class WalletType(val display: String) {
    IRCTC("IRCTC eWallet"),
    MOBIKWIK("Mobikwik™")
}

enum class UpiApp(val display: String) {
    PHONEPE("PhonePe"),
    PAYTM("Paytm"),
    CRED("CRED UPI"),
    BHIM_PAYTM("BHIM UPI (Powered by Paytm)")
}

enum class BookingOption(val value: Int, val display: String) {
    NONE(0, "None"),
    SAME_COACH(1, "Book, only if all berths are allotted in same coach"),
    ONE_LOWER_BERTH(2, "Book, only if at least 1 lower berth is allotted"),
    TWO_LOWER_BERTHS(3, "Book, only if 2 lower berths are allotted")
}

// ==================== 4. PAYMENT DETAILS ====================
@Parcelize
data class PaymentDetails(
    var category: PaymentCategory = PaymentCategory.BHIM_UPI,
    var upiId: String = "",
    var walletType: WalletType = WalletType.IRCTC,      // ✅ Enum instead of String
    var upiApp: UpiApp = UpiApp.PHONEPE,               // ✅ Enum instead of String
    var manualPayment: Boolean = false,
    var autofillOTP: Boolean = true
) : Parcelable {
    
    fun isValid(): Boolean {
        if (category == PaymentCategory.UPI_ID && upiId.isBlank()) return false
        return true
    }

    fun getDisplayText(): String = category.display
}

// ==================== 5. MASTER SNIPER TASK ====================
@Parcelize
data class SniperTask(
    val taskId: String = UUID.randomUUID().toString(),
    
    // ⏰ Time Sniper Variables (VERY IMPORTANT)
    val triggerTime: String = "10:00:00",
    val msAdvance: Int = 200,
    
    // 🚂 Journey Details
    val trainNumber: String = "",
    val travelClass: String = "",
    val quota: String = "",
    
    // 👨‍👩‍👧‍👦 Passengers
    val passengers: List<PassengerData> = listOf(PassengerData()),
    val children: List<ChildData> = emptyList(),
    
    // 💳 Payment
    val payment: PaymentDetails = PaymentDetails(),
    
    // ⚙️ Advanced Options
    val captchaAutofill: Boolean = true,
    val autoUpgradation: Boolean = false,
    val confirmBerthsOnly: Boolean = false,
    val insurance: Boolean = true,
    val bookingOption: BookingOption = BookingOption.NONE,  // ✅ Enum instead of String
    
    // 🚆 Coach & Contact
    val coachPreferred: Boolean = false,
    val coachId: String = "",
    val mobileNo: String = ""
) : Parcelable {
    
    fun isReadyForBooking(): Boolean {
        return trainNumber.isNotBlank() &&
               travelClass.isNotBlank() &&
               quota.isNotBlank() &&
               passengers.isNotEmpty() &&
               passengers.any { it.isFilled() } &&
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
