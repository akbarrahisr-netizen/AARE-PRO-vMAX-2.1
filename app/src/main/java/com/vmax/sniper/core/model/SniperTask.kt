package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

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

enum class TravelClass { 
    AC_FIRST, AC_2TIER, AC_3TIER, SLEEPER 
}

enum class Quota { 
    GENERAL, TATKAL, PREMIUM_TATKAL, LADIES, LOWER_BERTH 
}

enum class BookingOption { 
    NONE, SAME_COACH, ONE_LOWER_BERTH, TWO_LOWER_BERTHS 
}

@Parcelize
data class PaymentDetails(
    val category: PaymentCategory = PaymentCategory.BHIM_UPI,
    val upiId: String = "",
    val manualPayment: Boolean = false,
    val autofillOTP: Boolean = true
) : Parcelable

enum class PaymentCategory { 
    BHIM_UPI, UPI_ID, CARDS 
}
