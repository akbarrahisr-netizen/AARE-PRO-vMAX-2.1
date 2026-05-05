
package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PassengerData(
    val name: String = "",
    val age: Int = 0,
    val gender: String = "Male",           // "Male" / "Female" / "Transgender"
    val berthPreference: String = "LOWER", // "LOWER", "MIDDLE", "UPPER", "SIDE LOWER"
    val meal: String = ""
) : Parcelable {

    fun isValid(): Boolean = name.isNotBlank() && age in 1..120
}

// Helper – agar IRCTC ko M/F/T format chahiye
fun mapGenderToIRCTC(gender: String): String = when (gender.lowercase()) {
    "male" -> "M"
    "female" -> "F"
    "transgender" -> "T"
    else -> "M"
}
