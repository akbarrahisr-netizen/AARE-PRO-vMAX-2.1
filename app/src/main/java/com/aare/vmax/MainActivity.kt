package com.aare.vmax

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aare.vmax.ui.screens.MainScreen
import com.aare.vmax.ui.theme.VMaxTheme // ✅ थीम इम्पोर्ट

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VMaxTheme { // ✅ थीम अप्लाई की
                MainScreen()
            }
        }
    }
}
