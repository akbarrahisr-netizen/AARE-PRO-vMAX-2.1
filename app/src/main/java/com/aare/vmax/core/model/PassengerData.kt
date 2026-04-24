package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class PassengerData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var age: String = "",
    var gender: String = "Male",
    var meal: String = "No Food",            // ✅ नाम फिक्स किया
    var berthPreference: String = "No Preference", // ✅ नाम फिक्स किया
    var bedRoll: Boolean = false,
    var isMandatory: Boolean = true
) : Parcelable {
    fun isValid() = name.trim().length >= 4 && age.toIntOrNull() in 1..120
    fun isFilled() = name.isNotBlank() && age.isNotBlank()
}
