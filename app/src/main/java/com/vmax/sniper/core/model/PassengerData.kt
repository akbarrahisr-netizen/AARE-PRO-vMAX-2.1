package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Locale

/**
 * VMAX Professional Domain Model Layer
 * Clean + Type-Safe + Scalable
 */

enum class Gender {
    MALE, FEMALE, TRANSGENDER
}

enum class BerthPreference {
    LOWER, MIDDLE, UPPER, SIDE_LOWER
}

@Parcelize
data class PassengerData(
    val name: String = "",
    val age: Int = 0,
    val gender: Gender = Gender.MALE,
    val berthPreference: BerthPreference = BerthPreference.LOWER,
    val meal: String = ""
) : Parcelable {

    fun isValid(): Boolean {
        return name.isNotBlank() && age in 1..120
    }
}

@Parcelize
data class ChildData(
    val name: String = "",
    val age: Int = 0,
    val gender: Gender = Gender.MALE
) : Parcelable {

    fun isValid(): Boolean {
        return name.isNotBlank() && age in 0..12
    }
}

/**
 * Mapper Layer (UI → IRCTC format)
 */
object IRCTCMapper {

    fun genderToCode(gender: Gender): String {
        return when (gender) {
            Gender.MALE -> "M"
            Gender.FEMALE -> "F"
            Gender.TRANSGENDER -> "T"
        }
    }

    fun berthToCode(berth: BerthPreference): String {
        return when (berth) {
            BerthPreference.LOWER -> "LB"
            BerthPreference.MIDDLE -> "MB"
            BerthPreference.UPPER -> "UB"
            BerthPreference.SIDE_LOWER -> "SL"
        }
    }
}
