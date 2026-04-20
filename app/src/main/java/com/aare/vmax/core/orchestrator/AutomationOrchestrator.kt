class AutomationOrchestrator(
    private val eventBus: AutomationEventBus,
    private val workflowEngine: WorkflowEngine,
    private val nodeFinder: NodeFinder,
    private val actionExecutor: ActionExecutor,
    private val scope: CoroutineScope
) {

    private var lastHash = 0

    fun start(config: StrikeConfig, getRoot: () -> AccessibilityNodeInfo?) {

        // Event handlers (decoupled logic)
        eventBus.subscribe { event ->
            when (event) {

                is AutomationEvent.ScreenChanged -> {
                    workflowEngine.onScreenChange(event.screenType)
                }

                is AutomationEvent.NodeFound -> {
                    actionExecutor.execute(event.nodeId)
                }

                is AutomationEvent.ErrorOccurred -> {
                    if (event.recoverable) workflowEngine.retry()
                    else eventBus.publish(AutomationEvent.UserInterventionRequired)
                }

                else -> {}
            }
        }

        // Lightweight observer
        scope.launch(Dispatchers.Default) {
            observe(getRoot)
        }
    }

    // =========================================================
    // ✅ SAFE OBSERVER (NO NODE LEAKS)
    // =========================================================
    private suspend fun observe(getRoot: () -> AccessibilityNodeInfo?) {

        while (isActive) {

            val root = getRoot() ?: run {
                delay(100)
                continue
            }

            try {
                val hash = root.hashCode() // replace with real structural hash

                if (hash != lastHash) {
                    lastHash = hash

                    eventBus.publish(
                        AutomationEvent.ScreenChanged(
                            detectScreenType(root)
                        )
                    )
                }

            } finally {
                root.recycle()
            }

            delay(80L) // controlled polling
        }
    }
}
