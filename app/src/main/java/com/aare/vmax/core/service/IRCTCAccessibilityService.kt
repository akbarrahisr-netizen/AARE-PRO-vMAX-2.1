override fun onServiceConnected() {
    super.onServiceConnected()

    // =========================
    // 1. DATA LAYER INIT
    // =========================
    passengerRepo = PassengerRepository(applicationContext)
    configStore = ConfigStore(applicationContext)

    // =========================
    // 2. ORCHESTRATOR INIT
    // =========================
    orchestrator = AutomationOrchestrator(
        spatial = spatial,
        human = human,
        chrono = chrono,
        safety = safety,
        passengerRepo = passengerRepo
    )

    Log.d(
        "VMAX_LOG",
        "🚀 Full Pipeline Connected: UI -> VM -> Orchestrator -> Service"
    )

    // =========================
    // 3. COMMAND CENTER LISTENER
    // =========================
    job = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
        AutomationCommandCenter.isSystemActive.collect { active ->

            if (!active) {
                orchestrator.stopSystem()
                return@collect
            }

            val root = rootInActiveWindow
            if (root != null) {
                orchestrator.startBookingFlow(root)
            }
        }
    }
}
