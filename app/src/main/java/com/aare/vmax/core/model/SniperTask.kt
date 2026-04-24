package com.aare.vmax.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// 🏹 इंजन के लिए असली स्नाइपर टास्क
@Parcelize
data class SniperTask(
    val taskId: String,
    val trainNumber: String,
    val travelClass: String,
    val quota: String,
    val passengers: List<PassengerData>
) : Parcelable

