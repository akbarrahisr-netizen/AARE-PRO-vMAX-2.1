package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class PassengerData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var age: String = "",
    var gender: String = "Male",
    var berthPreference: String = "No Preference",
    var meal: String = "No Food",
    var optBerth: Boolean = false,
    var bedRoll: Boolean = false,
    var availConcession: Boolean = false,
    var nationality: String = "India"
) : Parcelable {
    fun isFilled() = name.isNotBlank() && age.isNotBlank()
}
