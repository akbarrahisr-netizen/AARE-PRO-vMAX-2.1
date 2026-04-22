package com.aare.vmax.core.models

enum class ActionType {
    CLICK, SCROLL, SWIPE, INPUT_TEXT, WAIT, FOCUS, RECOVER
}

enum class SelectorType {
    RESOURCE_ID,
    CONTENT_DESC,
    CLASS_NAME,
    TEXT
}

sealed class VerificationStrategy {

    object None : VerificationStrategy()

    data class ScreenChanged(
        val minHashDiff: Long = 50L
    ) : VerificationStrategy()

    data class NodeExists(
        val selector: String,
        val selectorType: SelectorType = SelectorType.TEXT
    ) : VerificationStrategy()

    data class TextAppears(val text: String) : VerificationStrategy()

    data class ElementDisappears(val selector: String) : VerificationStrategy()
}

data class RecordedStep(
    val id: String,
    val criteria: String,
    val actionType: ActionType = ActionType.CLICK,

    // 🎯 precision targeting
    val targetClass: String? = null,
    val targetDesc: String? = null,
    val targetId: String? = null,

    // 🔄 improved fallback system (IMPORTANT FIX)
    val fallbackCriteria: List<Pair<String, SelectorType>> = emptyList(),

    // ⚙️ execution control
    val maxRetries: Int = 15,
    val postActionDelayMs: Long = 200L,

    // 🧠 behavior control
    val isCritical: Boolean = false,
    val adaptive: Boolean = true,

    // ⚠️ NOTE: engine already uses fingerprint logic, so this is fallback only
    val verificationStrategy: VerificationStrategy =
        VerificationStrategy.ScreenChanged(100L)
) {

    fun getPrimarySelector(): Pair<String, SelectorType> {
        return when {
            !targetId.isNullOrEmpty() ->
                targetId to SelectorType.RESOURCE_ID

            !targetDesc.isNullOrEmpty() ->
                targetDesc to SelectorType.CONTENT_DESC

            !targetClass.isNullOrEmpty() ->
                targetClass to SelectorType.CLASS_NAME

            else ->
                criteria to SelectorType.TEXT
        }
    }

    fun getAllSelectors(): List<Pair<String, SelectorType>> {
        val primary = getPrimarySelector()
        return listOf(primary) + fallbackCriteria
    }

    fun requiresUiChangeVerification(): Boolean {
        return when (actionType) {
            ActionType.SCROLL,
            ActionType.SWIPE -> true
            else -> verificationStrategy is VerificationStrategy.ScreenChanged
        }
    }
}
