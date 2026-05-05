package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class ChildData(
    val name: String = "",
    val ageRange: String = "",         // ✅ String to match UI text field
    val gender: String = "Male"
) : Parcelable {

    fun isValid(): Boolean = name.isNotBlank() && ageRange.toIntOrNull() in 0..12
}
