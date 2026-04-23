package com.aare.vmax.core.models

/**
 * 🚀 VMAX PRO - RecordedStep
 * यह फाइल इंजन को बताती है कि क्या करना है (Click/Type) और कहाँ करना है।
 */
data class RecordedStep(
    val id: String,
    val actionType: ActionType,
    val criteria: String,
    val inputText: String = "",      // 👈 यह नाम भरने के लिए है
    val anchorText: String = "",     // 👈 यह सही ट्रेन ढूँढने के लिए है
    val verificationStrategy: VerificationStrategy = VerificationStrategy.None,
    val maxRetries: Int = 5,
    val postActionDelayMs: Long = 150
)
