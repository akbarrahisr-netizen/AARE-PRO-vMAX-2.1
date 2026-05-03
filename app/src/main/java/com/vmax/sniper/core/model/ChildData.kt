package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class ChildData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var ageRange: String = "Below 1 yr",
    var gender: String = "Male"
) : Parcelable
