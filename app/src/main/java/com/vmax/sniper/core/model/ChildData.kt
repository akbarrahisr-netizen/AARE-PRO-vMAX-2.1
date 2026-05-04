package com.vmax.sniper.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize  // ✅ यह होना चाहिए
data class ChildData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var ageRange: String = "",
    var gender: String = ""
) : Parcelable  // ✅ Parcelable implement होना चाहिए
