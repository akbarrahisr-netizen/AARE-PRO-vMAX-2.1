private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
private var job: Job? = null

override fun onServiceConnected() {
    super.onServiceConnected()

    job = serviceScope.launch {
        AutomationCommandCenter.isSystemActive
            .distinctUntilChanged()
            .collect { active ->

                if (!active) {
                    orchestrator.stopSystem()
                    return@collect
                }

                val root = rootInActiveWindow ?: return@collect
                orchestrator.startBookingFlow(root)
            }
    }
}

override fun onDestroy() {
    super.onDestroy()
    job?.cancel()
    serviceScope.cancel()
}
