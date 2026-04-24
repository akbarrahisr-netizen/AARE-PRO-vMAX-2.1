package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// 🏹 डेटा कोIntent में भेजने के लिए Parcelize ज़रूरी है
@Parcelize
data class SniperTask(
    val taskId: String,
    val trainNumber: String,
    val travelClass: String,
    val quota: String,
    val passengers: List<PassengerData>
) : Parcelable

// 📝 पैसेंजर का सांचा (अगर PassengerData.kt अलग से नहीं है, तो इसे यहाँ रहने दें)
@Parcelize
data class PassengerData(
    var name: String = "",
    var age: String = "",
    var gender: String = "Male",
    var berthPreference: String = "No Preference"
) : Parcelable {
    fun isFilled() = name.isNotBlank() && age.isNotBlank()
}

