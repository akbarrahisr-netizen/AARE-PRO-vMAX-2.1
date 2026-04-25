package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class PassengerData(
    val id: String = UUID.randomUUID().toString(),
    
    // 👤 Basic Details
    var name: String = "",
    var age: String = "",
    var gender: String = "Male",
    
    // 🎯 Berth & Meal
    var berthPreference: String = "No Preference",
    var meal: String = "No Food",                  // ✅ FIX: UI के हिसाब से इसे 'meal' ही रखा है
    var optBerth: Boolean = false,
    var bedRoll: Boolean = false,
    
    // 🌍 Concession & Country
    var availConcession: Boolean = false,
    var country: String = "India",
    
    // 👶 Child Details (if applicable)
    var isChild: Boolean = false,
    var childDetails: String = "",
    
    var isMandatory: Boolean = true
) : Parcelable {
    
    // ✅ एडवांस्ड वैलिडेशन (दोनों का बेस्ट मिक्स)
    fun isValid(): Boolean {
        val ageInt = age.toIntOrNull() ?: 0
        return name.trim().length in 4..50 && 
               ageInt in 1..120 &&
               gender in listOf("Male", "Female", "Transgender")
    }
    
    fun isFilled(): Boolean = name.isNotBlank() && age.isNotBlank()
    
    companion object {
        fun createDefault() = PassengerData()
    }
}

@Parcelize
data class BookingOptions(
    // ⚙️ Advanced Booking Options
    var noFood: Boolean = false,
    var considerAutoUpgradation: Boolean = false,
    var bookOnlyIfConfirm: Boolean = false,
    var travelInsurance: Boolean = false,
    var bookingOption: String = "None",  // None, Same Coach, 1 Lower, 2 Lower
    var coachPreferred: Boolean = false,
    var coachId: String = "",
    var mobileNo: String = "",
    
    // 💳 Payment Options
    var paymentMethod: String = "UPI",   // UPI, e-Wallet, Netbanking, Card
    var upiId: String = "",
    var autofillOTP: Boolean = true,
    var manualPayment: Boolean = false
) : Parcelable
