package com.aare.vmax.core.model

import kotlinx.serialization.Serializable

/**
 * उस्ताद, यह सांचा आपके ६ पैसेंजर ऑप्शंस को स्टोर करेगा।
 */
@Serializable
data class PassengerData(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "",           // १. नाम
    var age: String = "",            // २. उम्र
    var gender: String = "Male",     // ३. जेंडर
    var berthPref: String = "No Preference", // ४. सीट चॉइस
    var mealPref: String = "No Food",  // ५. खाना
    var bedRoll: Boolean = false     // ६. बेड रोल (Yes/No)
)
