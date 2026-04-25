package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 🎯 VMAX SNIPER TASK — 100% MERGED (ADVANCED PAYMENT EDITION)
 */
@Parcelize
data class SniperTask(
    // 🚄 Basic Booking Details
    val taskId: String,
    val trainNumber: String,
    val travelClass: String,            // ✅ Now includes all 13 classes
    val quota: String,
    
    // 👥 Passenger List
    val passengers: List<PassengerData>,
    
    // 💳 Advanced Payment Options
    val paymentMethod: String = "UPI",  // ✅ UPI, Net Banking, Cards, e-Wallets
    val upiApp: String = "BHIM UPI",    // ✅ Selected UPI app or Wallet name
    val autoFillOTP: Boolean = true,    // ✅ Auto-fill OTP toggle
    val manualPayment: Boolean = false  // ✅ Manual payment toggle
) : Parcelable

// ==========================================
// 💳 Advanced Payment Models (भविष्य के लिए सुरक्षित)
// ==========================================
sealed class PaymentMethod {
    object UPI : PaymentMethod()
    object NetBanking : PaymentMethod()
    object CreditCard : PaymentMethod()
    object DebitCard : PaymentMethod()
    object EWallet : PaymentMethod()
    object CashCard : PaymentMethod()
    
    data class UPIApp(val appName: String) : PaymentMethod()
    data class EWalletApp(val walletName: String) : PaymentMethod()
}
