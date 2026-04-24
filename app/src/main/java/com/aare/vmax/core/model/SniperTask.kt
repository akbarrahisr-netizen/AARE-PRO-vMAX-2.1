package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class PassengerData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",                   // 1. नाम
    var age: String = "",                    // 2. उम्र
    var gender: String = "Male",             // 3. जेंडर
    var berthPref: String = "No Preference", // 4. सीट चॉइस
    var mealPref: String = "No Food",        // 5. खाना
    var bedRoll: Boolean = false             // 6. बेड रोल (Yes/No)
) : Parcelable {
    
    // 🎯 इंजन को बताने के लिए कि फॉर्म भरा है या नहीं
    fun isFilled() = name.isNotBlank() && age.isNotBlank()
}

