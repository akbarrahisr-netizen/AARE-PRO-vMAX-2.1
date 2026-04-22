class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bootCheck() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUI()
        bootCheck()
    }

    // =====================================================
    // 🧠 SYSTEM STATE CHECKER (CORE)
    // =====================================================
    private fun bootCheck() {
        val overlay = Settings.canDrawOverlays(this)
        val access = isAccessibilityServiceEnabled()

        updateUI(overlay, access)

        if (overlay && access) {
            enterReadyMode()
        }
    }

    // =====================================================
    // 🎯 UI INIT
    // =====================================================
    private fun initUI() {

        binding.btnEnableService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnGrantOverlay.setOnClickListener {
            requestOverlay()
        }

        // 🚀 ONE BUTTON “SNIPER MODE”
        binding.btnStart.setOnClickListener {
            if (Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()) {
                launchSniperCore()
            } else {
                Toast.makeText(this, "Enable permissions first", Toast.LENGTH_SHORT).show()
                bootCheck()
            }
        }
    }

    // =====================================================
    // ⚡ OVERLAY REQUEST
    // =====================================================
    private fun requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    // =====================================================
    // 🔥 READY MODE (ALL SYSTEMS GREEN)
    // =====================================================
    private fun enterReadyMode() {
        binding.tvInstruction.text = "🚀 SYSTEM READY — SNIPER MODE ENABLED"
        binding.btnStart.isEnabled = true
        binding.btnStart.alpha = 1.0f
    }

    // =====================================================
    // 🧠 UI STATE ENGINE
    // =====================================================
    private fun updateUI(overlay: Boolean, access: Boolean) {

        binding.overlayStatus.text =
            if (overlay) "✅ Overlay READY" else "❌ Overlay MISSING"

        binding.accessibilityStatus.text =
            if (access) "✅ ACCESS READY" else "❌ ACCESS MISSING"

        binding.btnGrantOverlay.isEnabled = !overlay
        binding.btnEnableService.isEnabled = !access

        binding.btnGrantOverlay.alpha = if (overlay) 0.4f else 1.0f
        binding.btnEnableService.alpha = if (access) 0.4f else 1.0f

        binding.btnStart.isEnabled = overlay && access
    }

    // =====================================================
    // 🚀 SNIPER CORE BOOTSTRAP
    // =====================================================
    private fun launchSniperCore() {
        Toast.makeText(this, "🚀 Launching Sniper Core...", Toast.LENGTH_SHORT).show()

        // Example: start floating service / orchestrator entry
        val intent = Intent(this, com.aare.vmax.service.SniperService::class.java)
        startService(intent)
    }

    // =====================================================
    // 🧠 ACCESSIBILITY CHECK
    // =====================================================
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "$packageName/.service.VMaxAccessibilityService"

        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        return enabled.contains(serviceName)
    }
}
