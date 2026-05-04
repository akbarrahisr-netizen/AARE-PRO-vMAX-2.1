// ==================== ELITE TUNED ENGINE ====================

private suspend fun smartAttackWithLock(isAc: Boolean): Boolean = coroutineScope {
    // 1. Scroll Stabilization (UI Render Fix)
    ensureListIsReady()

    val root = rootInActiveWindow ?: return@coroutineScope false
    
    // 2. LOCAL Node Cache (Safe from Stale References)
    val localNodeCache = buildLocalNodeCache(root)
    
    val snapshot = TrainPriorityManager.getCurrentAvailabilityMap(isAc)
    val targets = TrainPriorityManager.getFullAttackPlan(isAc, snapshot)
    
    if (targets.isEmpty()) return@coroutineScope false

    val winnerDeferred = CompletableDeferred<Boolean>()
    val attackJobs = mutableListOf<Job>()
    
    for (train in targets) {
        if (AttackLock.isLocked()) break

        val classOrder = TrainPriorityManager.getSmartClassOrder(train, isAc, snapshot)
        val bestClass = classOrder.firstOrNull() ?: continue

        val job = launch(Dispatchers.Default) {
            if (!isActive || AttackLock.isLocked()) return@launch
            
            // 3. Fast Context-Aware Cache Lookup
            val trainNode = localNodeCache[train.trainNumber] ?: return@launch
            
            if (executeEliteStrike(trainNode, train.trainNumber, bestClass)) {
                runCatching { winnerDeferred.complete(true) }
            }
        }
        attackJobs.add(job)
    }

    val result = withTimeoutOrNull(250L) { winnerDeferred.await() } ?: false
    attackJobs.forEach { it.cancel() }
    return@coroutineScope result
}

// ==================== FAST CHILD SCAN (Zero BFS) ====================
private fun findClassNodeElite(trainNode: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
    for (i in 0 until trainNode.childCount) {
        val child = trainNode.getChild(i) ?: continue
        val text = child.text?.toString()?.uppercase() ?: ""
        if (text.contains(className)) return child
    }
    return null
}

// ==================== SURGICAL STRIKE LOGIC ====================
private suspend fun executeEliteStrike(trainNode: AccessibilityNodeInfo, trainNo: String, className: String): Boolean {
    // BFS हटाकर Direct Child Scan का इस्तेमाल
    val classNode = findClassNodeElite(trainNode, className) ?: return false
    
    val weight = getSmartWeight(classNode.text?.toString() ?: "", className)
    TrainPriorityManager.updateAvailability(trainNo, className, weight)

    // Threshold tightened to 20 as suggested
    if (weight <= 20) {
        if (AttackLock.tryLock(trainNo, className)) {
            
            // Micro-latency tuning (6ms for premium classes)
            val delayMs = if (className.contains("2A")) 6L else 8L
            delay(delayMs) 
            
            val bookBtn = findClassNodeElite(classNode, "BOOK NOW") ?: findClassNodeElite(classNode, "अभी बुक करें")
            
            if (bookBtn != null && bookBtn.isVisibleToUser) {
                val success = stableClick(bookBtn)
                
                // Already Clicked Guard (Retry allow if failed)
                if (!success) {
                    AttackLock.reset()
                }
                return success
            }
        }
    }
    return false
}

// ==================== UI STABILIZER ====================
private suspend fun ensureListIsReady() {
    performGlobalAction(GLOBAL_ACTION_SCROLL_FORWARD)
    delay(20)
    performGlobalAction(GLOBAL_ACTION_SCROLL_BACKWARD)
    delay(20) // UI को रेंडर होने का समय दें
}
