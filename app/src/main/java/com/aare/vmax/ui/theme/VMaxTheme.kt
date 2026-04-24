package com.aare.vmax.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

// 🎨 उस्ताद की सिग्नेचर डार्क पर्पल थीम
data class VMaxColors(
    val background: Color = Color(0xFF101018),
    val cardBg: Color = Color(0xFF1E1E2A),
    val fieldBg: Color = Color(0xFF252538),
    val accent: Color = Color(0xFF6B21A8),
    val onField: Color = Color.White,
    val hint: Color = Color.Gray,
    val error: Color = Color.Red,
    val warning: Color = Color(0xFFFFA500)
) {
    companion object {
        val Dark = VMaxColors()
        val current: VMaxColors @Composable get() = LocalVMaxColors.current
    }
}

val LocalVMaxColors = staticCompositionLocalOf { VMaxColors() }

@Composable
fun VMaxTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVMaxColors provides VMaxColors.Dark) {
        MaterialTheme(content = content)
    }
}
