package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class SniperTask(
    val taskId: String = UUID.randomUUID().toString(),
    
    // Time Sniper
    val triggerTime: String = "10:00:00",
    val msAdvance: Int = 200,
    
    // Journey
    val trainNumber: String = "",
    val travelClass: TravelClass = TravelClass.AC_3TIER,
    val quota: Quota = Quota.TATKAL,
    val journeyDate: String = "",
    
    // Passengers
    val passengers: List<PassengerData> = listOf(PassengerData()),
    val children: List<ChildData> = emptyList(),
    
    // Booking Options
    val bookingOption: BookingOption = BookingOption.NONE,
    val autoUpgradation: Boolean = false,
    val confirmBerthsOnly: Boolean = false,
    val insurance: Boolean = true,
    
    // Coach & Contact
    val coachPreferred: Boolean = false,
    val coachId: String = "",
    val mobileNo: String = "",
    
    // Payment
    val payment: PaymentDetails = PaymentDetails(),
    
    // Advanced
    val captchaAutofill: Boolean = true,
    val noFoodForAll: Boolean = false
) : Parcelable {
    fun isReady(): Boolean {
        return trainNumber.isNotBlank() &&
               journeyDate.isNotBlank() &&
               passengers.any { it.isFilled() }
    }
}
