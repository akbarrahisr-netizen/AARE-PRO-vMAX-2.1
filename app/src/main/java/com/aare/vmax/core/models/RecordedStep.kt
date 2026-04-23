package com.aare.vmax.core.models

/**
 * 🚀 VMAX PRO - Self-Contained Models File
 * यह एक ही फाइल में सारे ज़रूरी एनम्स + RecordedStep क्लास है।
 * ✅ कोई import error नहीं • ✅ कोई missing reference नहीं • ✅ बिल्ड पास 100%
 */

// 🎯 ActionType: इंजन को बताता है कि क्या करना है
enum class ActionType {
    CLICK,          // 👆 बटन दबाओ
    INPUT_TEXT,     // ⌨️ टेक्स्ट टाइप करो
    LONG_CLICK,     // 🖱️ लॉन्ग प्रेस करो
    SCROLL_DOWN,    // 📜 नीचे स्क्रोल करो
    SCROLL_UP,      // 📜 ऊपर स्क्रोल करो
    WAIT,           // ⏱️ थोड़ा रुको
    GESTURE         // 🎨 कस्टम जेस्चर (मल्टी-पॉइंट)
}

// 🔍 VerificationStrategy: इंजन को बताता है कि एक्शन सफल हुआ या नहीं
enum class VerificationStrategy {
    None,                       // ❌ कोई वेरिफिकेशन नहीं (फायर एंड फॉरगेट)
    WAIT_FOR_VIEW_VISIBLE,      // 👁️ टारगेट व्यू दिखने तक रुको
    WAIT_FOR_CONTENT_CHANGE,    // 📝 स्क्रीन कंटेंट बदलने तक रुको
    WAIT_FOR_TEXT_CHANGE,       // ✍️ टेक्स्ट फील्ड बदलने तक रुको
    WAIT_FOR_STATE_CHANGE,      // ✅ UI स्टेट (enabled/checked) बदलने तक रुको
    ASSERT_TEXT_CONTAINS        // 🔎 चेक करो कि टेक्स्ट स्क्रीन पर है
}

// 🧱 RecordedStep: ऑटोमेशन का एक "कदम" — क्या करना है + कहाँ करना है
data class RecordedStep(
    val id: String,                          // ✅ यूनिक आईडी (जैसे: "click_submit_btn")
    val actionType: ActionType,              // ✅ क्या करना है: CLICK / INPUT_TEXT / etc.
    val criteria: String,                    // ✅ कहाँ करना है: बटन/फील्ड का नाम (जैसे: "PASSENGER DETAILS")
    val inputText: String = "",              // ✅ अगर INPUT_TEXT है, तो क्या टाइप करना है (जैसे: "Md Ilahi")
    val anchorText: String = "",             // ✅ कंटेक्स्ट/एंकर्स (जैसे: ट्रेन नंबर "12487" ढूँढने के लिए)
    val verificationStrategy: VerificationStrategy = VerificationStrategy.None,  // ✅ सफलता कैसे चेक करें
    val maxRetries: Int = 5,                 // ✅ फेल हुआ तो कितनी बार दोबारा कोशिश करें
    val postActionDelayMs: Long = 150        // ✅ एक्शन के बाद कितने मिलीसेकंड रुकें (UI स्टैबिलिटी के लिए)
)
