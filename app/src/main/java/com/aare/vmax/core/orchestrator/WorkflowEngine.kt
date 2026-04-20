class WorkflowEngine(
    private val logger: Logger
) {

    private val stateLog = CopyOnWriteArrayList<StateTransition>()

    data class StateTransition(
        val from: Int?,
        val to: Int,
        val result: StepResult,
        val timestamp: Long
    )

    // =========================================================
    // ✅ SAFE LOOP DETECTION
    // =========================================================
    fun detectLoop(stepId: Int): Boolean {

        val recent = stateLog.takeLast(10).map { it.to }

        val frequency = recent.count { it == stepId }

        if (frequency >= 3) {
            logger.error(
                tag = "Workflow",
                message = "Loop detected at state $stepId",
                metadata = mapOf("recentStates" to recent)
            )
            return true
        }

        return false
    }

    // =========================================================
    // ✅ SAFE STATE EXECUTION TRACKING
    // =========================================================
    fun recordTransition(
        from: Int?,
        to: Int,
        result: StepResult
    ) {
        stateLog.add(
            StateTransition(
                from = from,
                to = to,
                result = result,
                timestamp = System.currentTimeMillis()
            )
        )

        cleanup()
    }

    // =========================================================
    // ✅ MEMORY SAFE CLEANUP
    // =========================================================
    private fun cleanup(maxSize: Int = 100) {
        if (stateLog.size > maxSize) {
            val removeCount = stateLog.size - maxSize
            repeat(removeCount) {
                stateLog.removeAt(0)
            }
        }
    }
}
