package com.aare.vmax

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aare.vmax.ui.MainScreen // ✅ '.screens' हटा दिया गया है
import com.aare.vmax.ui.theme.VMaxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VMaxTheme { 
                MainScreen()
            }
        }
    }
}
