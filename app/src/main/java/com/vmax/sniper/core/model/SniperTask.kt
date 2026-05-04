package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class SniperTask(
    val taskId: String = UUID.randomUUID().toString(),
    val triggerTime: String = "10:00:00",      // ✅ Default, MainActivity override करेगी
    val msAdvance: Int = 150,                  // ✅ Default 150ms
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
                              passengers.any { it.isFilled() }
}
