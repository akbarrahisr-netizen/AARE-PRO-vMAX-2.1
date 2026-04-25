package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class PassengerData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var age: String = "",
    var gender: String = "Male",           // Male, Female, Transgender
    var berthPreference: String = "LOWER", // LOWER, MIDDLE, UPPER, SIDELOWER, SIDE UPPER, WINDOW SIDE, CABIN, COUPE
    var meal: String = "VEG",              // VEG, NON VEG, JAIN MEAL, VEG (DIABETIC), NON VEG (DIABETIC), No Food
    var optBerth: Boolean = false,
    var bedRoll: Boolean = false,
    var availConcession: Boolean = false,
    var nationality: String = "India"
) : Parcelable {
    fun isFilled() = name.isNotBlank() && age.isNotBlank()
    fun isValid() = name.length >= 3 && (age.toIntOrNull() ?: 0) in 1..125
}
