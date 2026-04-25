// ==================== 🎯 IMPROVED: CHILD DETAILS (Points 28-40) ====================
private fun fillChildDetails(root: AccessibilityNodeInfo) {
    val task = activeTask ?: return
    val childNameFields = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_NAME)
    val childAgeSpinners = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_AGE)
    val childGenderSpinners = root.findAccessibilityNodeInfosByViewId(IRCTC.CHILD_GENDER)
    
    for (i in 0 until min(childNameFields.size, task.children.size)) {
        val child = task.children[i]
        
        // Point 29-32: Child Name
        if (childNameFields[i].text.isNullOrBlank()) {
            setTextToNode(childNameFields[i], child.name)
            Thread.sleep(30)
        }
        
        // Point 33-37: Child Age Range
        if (i < childAgeSpinners.size && child.ageRange.isNotBlank()) {
            selectSpinnerValue(childAgeSpinners[i], child.ageRange, root)
            Thread.sleep(30)
        }
        
        // Point 38-40: Child Gender
        if (i < childGenderSpinners.size && child.gender.isNotBlank()) {
            selectSpinnerValue(childGenderSpinners[i], child.gender, root)
            Thread.sleep(30)
        }
    }
}

// ==================== 🎯 IMPROVED: BOOKING OPTIONS (Points 42-49) ====================
private fun setBookingOptions(root: AccessibilityNodeInfo) {
    val task = activeTask ?: return
    
    // Point 42: Auto Upgradation
    val autoUpgrade = root.findAccessibilityNodeInfosByViewId(IRCTC.AUTO_UPGRADE_CHECK)
    if (autoUpgrade.isNotEmpty() && task.autoUpgradation) {
        if (!autoUpgrade[0].isChecked) clickNode(autoUpgrade[0])
        Thread.sleep(30)
    }
    
    // Point 43: Confirm Berths Only
    val confirmBerth = root.findAccessibilityNodeInfosByViewId(IRCTC.CONFIRM_BERTH_CHECK)
    if (confirmBerth.isNotEmpty() && task.confirmBerthsOnly) {
        if (!confirmBerth[0].isChecked) clickNode(confirmBerth[0])
        Thread.sleep(30)
    }
    
    // Point 44-45: Travel Insurance
    if (task.insurance) {
        val insuranceYes = root.findAccessibilityNodeInfosByViewId(IRCTC.INSURANCE_YES)
        if (insuranceYes.isNotEmpty() && !insuranceYes[0].isChecked) clickNode(insuranceYes[0])
    } else {
        val insuranceNo = root.findAccessibilityNodeInfosByViewId(IRCTC.INSURANCE_NO)
        if (insuranceNo.isNotEmpty() && !insuranceNo[0].isChecked) clickNode(insuranceNo[0])
    }
    Thread.sleep(50)
    
    // Point 46-49: Booking Option Spinner
    val bookingOptSpinner = root.findAccessibilityNodeInfosByViewId(IRCTC.BOOKING_OPT_SPINNER)
    if (bookingOptSpinner.isNotEmpty() && task.bookingOption.value > 0) {
        selectSpinnerValue(bookingOptSpinner[0], task.bookingOption.display, root)
        Thread.sleep(50)
    }
}

// ==================== 🎯 IMPROVED: COACH & MOBILE (Points 50-52) ====================
private fun setCoachAndMobile(root: AccessibilityNodeInfo) {
    val task = activeTask ?: return
    
    // Point 50-51: Coach Preference
    if (task.coachPreferred && task.coachId.isNotBlank()) {
        val coachInput = root.findAccessibilityNodeInfosByViewId(IRCTC.COACH_PREF_INPUT)
        if (coachInput.isNotEmpty() && coachInput[0].text.isNullOrBlank()) {
            setTextToNode(coachInput[0], task.coachId.uppercase())
            Thread.sleep(30)
        }
    }
    
    // Point 52: Mobile Number
    if (task.mobileNo.isNotBlank()) {
        val mobileInput = root.findAccessibilityNodeInfosByViewId(IRCTC.MOBILE_INPUT)
        if (mobileInput.isNotEmpty() && mobileInput[0].text.isNullOrBlank()) {
            setTextToNode(mobileInput[0], task.mobileNo)
            Thread.sleep(30)
        }
    }
}

// ==================== 🎯 IMPROVED: PAYMENT SELECTION (Points 53-76) ====================
private fun selectPaymentMethod(root: AccessibilityNodeInfo) {
    val task = activeTask ?: return
    
    when (task.paymentCategory) {
        PaymentCategory.CARDS_NETBANKING -> {
            val cardsRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_CARDS)
            if (cardsRadio.isNotEmpty()) clickNode(cardsRadio[0])
        }
        PaymentCategory.BHIM_UPI -> {
            val upiRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_BHIM_UPI)
            if (upiRadio.isNotEmpty()) clickNode(upiRadio[0])
        }
        PaymentCategory.E_WALLETS -> {
            val walletRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_EWALLET)
            if (walletRadio.isNotEmpty()) clickNode(walletRadio[0])
            Thread.sleep(100)
            // Wallet selection spinner
            val walletSpinner = root.findAccessibilityNodeInfosByText(task.walletType.display)
            if (walletSpinner.isNotEmpty()) clickNode(walletSpinner[0])
        }
        PaymentCategory.UPI_ID -> {
            val upiIdRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_UPI_ID)
            if (upiIdRadio.isNotEmpty()) clickNode(upiIdRadio[0])
            Thread.sleep(100)
            if (task.upiId.isNotBlank()) {
                val upiIdInput = root.findAccessibilityNodeInfosByViewId(IRCTC.UPI_ID_INPUT)
                if (upiIdInput.isNotEmpty()) {
                    setTextToNode(upiIdInput[0], task.upiId)
                }
            }
        }
        PaymentCategory.UPI_APPS -> {
            val upiAppsRadio = root.findAccessibilityNodeInfosByViewId(IRCTC.PAYMENT_UPI_APPS)
            if (upiAppsRadio.isNotEmpty()) clickNode(upiAppsRadio[0])
            Thread.sleep(100)
            val upiAppSpinner = root.findAccessibilityNodeInfosByText(task.upiApp.display)
            if (upiAppSpinner.isNotEmpty()) clickNode(upiAppSpinner[0])
        }
    }
    Thread.sleep(100)
    
    // Point 61-62: Manual Payment & OTP Autofill Preferences
    if (task.manualPayment) {
        val manualCheckbox = root.findAccessibilityNodeInfosByText("I will fill payment information manually")
        if (manualCheckbox.isNotEmpty() && !manualCheckbox[0].isChecked) {
            clickNode(manualCheckbox[0])
        }
    }
    
    updateNotification("💳 Payment: ${task.paymentCategory.display}")
    
    // Click Continue/Proceed Button
    val proceedBtn = root.findAccessibilityNodeInfosByViewId(IRCTC.PROCEED_BTN)
        .ifEmpty { root.findAccessibilityNodeInfosByViewId(IRCTC.CONTINUE_BTN) }
    if (proceedBtn.isNotEmpty()) {
        clickNode(proceedBtn[0])
    }
}

// ==================== 🎯 IMPROVED: SPINNER SELECTION (Better reliability) ====================
private fun selectSpinnerValue(spinner: AccessibilityNodeInfo, value: String, root: AccessibilityNodeInfo) {
    clickNode(spinner)
    Thread.sleep(150) // Wait for dropdown to open
    
    // Try by exact text match first
    var options = root.findAccessibilityNodeInfosByText(value)
    
    // If not found, try partial match
    if (options.isEmpty()) {
        options = root.findAccessibilityNodeInfosByText(value.take(10))
    }
    
    if (options.isNotEmpty()) {
        clickNode(options[0])
    } else {
        Log.w(TAG, "⚠️ Spinner option not found: $value")
    }
    Thread.sleep(50)
}
