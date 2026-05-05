package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class PassengerData(
    val name: String = "",
    val age: String = "",              // ✅ String to match UI text field
    val gender: String = "Male",       // "Male"/"Female"/"Transgender"
    val berthPreference: String = "LOWER",
    val meal: String = ""
) : Parcelable {

    fun isValid(): Boolean = name.isNotBlank() && age.toIntOrNull() in 1..120
    fun isFilled(): Boolean = isValid()
}

// Helper function for IRCTC format
fun mapGenderToIRCTC(gender: String): String {
    return when (gender.lowercase()) {
        "male" -> "M"
        "female" -> "F"
        "transgender" -> "T"
        else -> "M"
    }
}
