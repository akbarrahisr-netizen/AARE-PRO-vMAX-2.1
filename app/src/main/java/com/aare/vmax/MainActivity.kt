package com.aare.vmax

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.aare.vmax.ui.MainScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 🚀 उस्ताद, यहाँ से आपका 'बैंगनी स्नाइपर' UI लोड होगा
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }

    // 🔓 चेक करें कि एक्सेसिबिलिटी चालू है या नहीं
    fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().contains(packageName, ignoreCase = true)) return true
        }
        return false
    }

    // 🚄 IRCTC ऐप खोलने का 'रॉकेट' फंक्शन
    fun launchIrctcApp() {
        val irctcPackage = "空中.org.in.prs.ima" // IRCTC का पैकेज नाम
        try {
            val intent = packageManager.getLaunchIntentForPackage(irctcPackage)
            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
        } catch (e: Exception) {
            // अगर ऐप नहीं है तो स्टोर पर भेजें
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$irctcPackage")))
        }
    }
}
