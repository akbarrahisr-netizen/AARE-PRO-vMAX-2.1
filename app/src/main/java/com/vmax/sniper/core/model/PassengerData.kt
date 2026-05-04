package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PassengerData(
    val name: String = "",
    val age: String = "",
    val gender: String = "",           // "Male", "Female", "Transgender"
    val berthPreference: String = "No Preference",
    val meal: String = "No Food",
    val optBerth: Boolean = false,
    val bedRoll: Boolean = false,
    val availConcession: Boolean = false,
    val nationality: String = "India"
) : Parcelable {
    fun isFilled() = name.isNotBlank() && age.isNotBlank()
}
