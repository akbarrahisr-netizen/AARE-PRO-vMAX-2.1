package com.aare.vmax

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.aare.vmax.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔥 Check & Request Permissions
        checkAndRequestPermissions()

        // 🔥 UI: Guide user to enable service
        binding.btnEnableService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
        }
    }

    private fun checkAndRequestPermissions() {
        // 1. Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            binding.overlayStatus.text = "❌ Overlay: Not Granted"
        } else {
            binding.overlayStatus.text = "✅ Overlay: Granted"
        }

        // 2. Accessibility Permission (User must enable manually)
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        binding.accessibilityStatus.text = 
            if (accessibilityEnabled) "✅ Accessibility: Enabled" 
            else "❌ Accessibility: Not Enabled"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/.service.VMaxAccessibilityService"
        val enabled = Settings.Secure.getString(
            contentResolver, 
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.contains(serviceName)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) {
            checkAndRequestPermissions()
        }
    }

    companion object {
        private const val REQUEST_OVERLAY = 1234
    }
}
