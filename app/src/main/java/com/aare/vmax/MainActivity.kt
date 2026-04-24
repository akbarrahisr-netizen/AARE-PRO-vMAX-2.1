package com.aare.vmax // ✅ पैकेज नाम छोटा

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aare.vmax.ui.screens.MainScreen
import com.aare.vmax.ui.theme.VMaxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VMaxTheme { // आपका पर्पल थीम यहाँ काम करेगा
                MainScreen()
            }
        }
    }
}
