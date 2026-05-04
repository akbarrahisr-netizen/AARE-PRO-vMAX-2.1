package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ✅ FIX 1: PaymentCategory with Parcelable
@Parcelize
enum class PaymentCategory(val display: String) : Parcelable {
    CARDS_NETBANKING("Cards & Netbanking"),
    BHIM_UPI("BHIM / UPI"),
    E_WALLETS("e-Wallets"),
    UPI_ID("UPI ID"),
    UPI_APPS("UPI Apps")
}

// ✅ FIX 2: WalletType with Parcelable
@Parcelize
enum class WalletType(val display: String) : Parcelable {
    IRCTC("IRCTC eWallet"),
    MOBIKWIK("Mobikwik™")
}

// ✅ FIX 3: UpiApp with Parcelable
@Parcelize
enum class UpiApp(val display: String) : Parcelable {
    PHONEPE("PhonePe"),
    PAYTM("Paytm"),
    CRED("CRED UPI"),
    BHIM_PAYTM("BHIM UPI (Powered by Paytm)")
}

// ✅ FIX 4: BookingOption with Parcelable
@Parcelize
enum class BookingOption(val value: Int, val display: String) : Parcelable {
    NONE(0, "None"),
    SAME_COACH(1, "Book, only if all berths are allotted in same coach"),
    ONE_LOWER_BERTH(2, "Book, only if at least 1 lower berth is allotted"),
    TWO_LOWER_BERTHS(3, "Book, only if 2 lower berths are allotted")
}

// ✅ FIX 5: TravelClass with Parcelable
@Parcelize
enum class TravelClass(val code: String, val display: String) : Parcelable {
    AC_FIRST("1A", "AC First Class (1A)"),
    AC_2TIER("2A", "AC 2 Tier (2A)"),
    AC_3TIER("3A", "AC 3 Tier (3A)"),
    SLEEPER("SL", "Sleeper (SL)")
}

// ✅ FIX 6: Quota with Parcelable
@Parcelize
enum class Quota(val code: String, val display: String) : Parcelable {
    GENERAL("GN", "General"),
    TATKAL("TQ", "Tatkal")
}

// ✅ This is already correct
@Parcelize
data class PaymentDetails(
    var category: PaymentCategory = PaymentCategory.BHIM_UPI,
    var upiId: String = "",
    var walletType: WalletType = WalletType.IRCTC,
    var upiApp: UpiApp = UpiApp.PHONEPE,
    var manualPayment: Boolean = false,
    var autofillOTP: Boolean = true
) : Parcelable {
    fun isValid(): Boolean {
        if (category == PaymentCategory.UPI_ID && upiId.isBlank()) return false
        return true
    }
    fun getDisplayText(): String = category.display
}
