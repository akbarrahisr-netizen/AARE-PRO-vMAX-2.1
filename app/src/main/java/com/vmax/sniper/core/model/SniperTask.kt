package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

// ==================== SNIPER TASK ====================
@Parcelize
data class SniperTask(
    val taskId: String = UUID.randomUUID().toString(),
    val triggerTime: String = "10:00:00",
    val msAdvance: Int = 150,
    val trainNumber: String = "",
    val travelClass: TravelClass = TravelClass.SLEEPER,
    val quota: Quota = Quota.TATKAL,
    val journeyDate: String = "",
    val passengers: List<PassengerData> = listOf(PassengerData()),
    val children: List<ChildData> = emptyList(),
    val bookingOption: BookingOption = BookingOption.NONE,
    val autoUpgradation: Boolean = false,
    val confirmBerthsOnly: Boolean = false,
    val insurance: Boolean = true,
    val coachPreferred: Boolean = false,
    val coachId: String = "",
    val mobileNo: String = "",
    val payment: PaymentDetails = PaymentDetails(),
    val captchaAutofill: Boolean = true
) : Parcelable {

    fun isReady(): Boolean = trainNumber.isNotBlank() && 
                              journeyDate.isNotBlank() &&
                              passengers.any { it.isValid() }

    val isFilled: Boolean
        get() = trainNumber.isNotBlank() &&
                journeyDate.isNotBlank() &&
                passengers.isNotEmpty() &&
                passengers.all { it.isValid() }
}

// ==================== ENUMS ====================
enum class TravelClass(val code: String, val display: String) {
    AC_FIRST("1A", "AC First Class"),
    AC_2TIER("2A", "AC 2 Tier"),
    AC_3TIER("3A", "AC 3 Tier"),
    SLEEPER("SL", "Sleeper")
}

enum class Quota(val code: String, val display: String) {
    GENERAL("GN", "General"),
    TATKAL("TQ", "Tatkal"),
    PREMIUM_TATKAL("PT", "Premium Tatkal"),
    LADIES("LD", "Ladies"),
    LOWER_BERTH("LB", "Lower Berth")
}

enum class BookingOption(val value: Int, val display: String) {
    NONE(0, "None"),
    SAME_COACH(1, "Book, only if all berths are allotted in same coach"),
    ONE_LOWER_BERTH(2, "Book, only if at least 1 lower berth is allotted"),
    TWO_LOWER_BERTHS(3, "Book, only if 2 lower berths are allotted")
}

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

// ==================== PAYMENT DETAILS ====================
@Parcelize
data class PaymentDetails(
    val category: PaymentCategory = PaymentCategory.BHIM_UPI,
    val upiId: String = "",
    val walletType: WalletType = WalletType.IRCTC,
    val upiApp: UpiApp = UpiApp.PHONEPE,
    val manualPayment: Boolean = false,
    val autofillOTP: Boolean = true
) : Parcelable {

    fun isValid(): Boolean = !(category == PaymentCategory.UPI_ID && upiId.isBlank())
    fun getDisplayText(): String = category.display
}
