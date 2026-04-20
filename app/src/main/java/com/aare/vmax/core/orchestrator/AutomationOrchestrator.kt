    fun start(config: StrikeConfig, getRoot: () -> AccessibilityNodeInfo?) { // ✅ 'f' छोटा कर दिया

        val liveSteps = listOf(
            RecordedStep(
                id = "step_select_class",
                action = ActionType.CLICK,
                criteria = config.bookingClass, // जैसे SL या 3A
                maxRetries = 2
            ),
            RecordedStep(
                id = "step_book_now",
                action = ActionType.CLICK,
                criteria = "Passenger", // 'Passenger Details' बटन के लिए
                maxRetries = 2
            )
        )

        workflowEngine.loadRecording(liveSteps)

        // ✅ Observer loop (controlled)
        scope.launch(Dispatchers.Default) {
            while (isActive) {

                val root = getRoot()
                if (root != null) {
                    try {
                        val screen = detectScreenType(root)

                        // 🎯 सिर्फ सही स्क्रीन पर trigger होगा
                        if (screen == ScreenType.SEARCH_RESULTS) {
                            workflowEngine.onScreenChanged(root)
                        }

                    } catch (e: Exception) {
                        // silent
                    } finally {
                        root.recycle()
                    }
                }

                delay(200L) // 0.2 सेकंड की तेज़ स्पीड
            }
        }
    }
    
