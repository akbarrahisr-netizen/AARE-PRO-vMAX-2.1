private fun resolveTargetNode(
    root: AccessibilityNodeInfo,
    step: RecordedStep
): SafeNode? {

    val selectors = step.getAllSelectors()

    val candidates = mutableListOf<AccessibilityNodeInfo>()

    val order = listOf(
        SelectorType.RESOURCE_ID,
        SelectorType.CONTENT_DESC,
        SelectorType.TEXT,
        SelectorType.CLASS_NAME
    )

    for (type in order) {
        val value = selectors.firstOrNull { it.second == type }?.first ?: continue

        val node = when (type) {
            SelectorType.RESOURCE_ID -> findNodeByResourceId(root, value)
            SelectorType.CONTENT_DESC -> findNodeByContentDesc(root, value)
            SelectorType.CLASS_NAME -> findNodeByClassName(root, value)
            SelectorType.TEXT -> findNodeByText(root, value)
        }

        if (node != null) {
            candidates.add(node)
        }
    }

    // 🎯 pick best candidate (visible + clickable priority)
    val best = candidates
        .filter { it.isVisibleToUser }
        .maxByOrNull { it.isClickable.toInt() }

    candidates.filter { it != best }.forEach {
        try { it.recycle() } catch (_: Exception) {}
    }

    return best?.let { SafeNode.wrap(it, owned = true) }
}
