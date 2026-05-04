package com.vmax.sniper.core.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

object CaptchaSolver {
    private const val TAG = "VMAX_Captcha"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var isSolving = false
    private var lastCaptchaText = ""
    
    // ✅ Main suspend function for Tatkal optimization
    suspend fun executeBypass(
        engine: WorkflowEngine,
        captchaImageNode: AccessibilityNodeInfo,
        captchaInputNode: AccessibilityNodeInfo
    ): Boolean {
        if (isSolving) {
            Log.d(TAG, "Already solving captcha, skipping...")
            return false
        }
        
        isSolving = true
        Log.d(TAG, "🔍 Captcha detected, solving with ML Kit...")
        
        return try {
            // Small delay to ensure image is fully loaded
            delay(150)
            
            val bounds = Rect()
            captchaImageNode.getBoundsInScreen(bounds)
            
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                Log.e(TAG, "Invalid captcha bounds")
                return false
            }
            
            val solvedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Method 1: Android 11+ Screenshot API (Fastest)
                solveWithScreenshotAPI(engine, bounds)
            } else {
                // Method 2: Fallback - Try to get from content description
                getTextFromContentDescription(captchaImageNode)
            }
            
            if (solvedText.isNotBlank() && solvedText.length in 3..8) {
                // Fill the captcha text
                setTextToNode(captchaInputNode, solvedText)
                delay(80)
                
                // Auto click verify button
                clickVerifyButton(engine)
                
                Log.d(TAG, "✅ Captcha solved successfully: $solvedText")
                lastCaptchaText = solvedText
                isSolving = false
                true
            } else {
                Log.w(TAG, "⚠️ Could not solve captcha, waiting for manual input")
                waitForManualCaptcha(engine, captchaInputNode)
                isSolving = false
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Captcha solving error: ${e.message}")
            isSolving = false
            false
        }
    }
    
    // ✅ Method 1: Android 11+ Screenshot API (Fastest)
    private suspend fun solveWithScreenshotAPI(
        engine: WorkflowEngine,
        bounds: Rect
    ): String = suspendCancellableCoroutine { continuation ->
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            engine.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                engine.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                            
                            if (bitmap != null && bounds.width() > 0 && bounds.height() > 0) {
                                try {
                                    // Crop to captcha area only
                                    val croppedBitmap = if (bounds.left >= 0 && bounds.top >= 0 &&
                                        bounds.right <= bitmap.width && bounds.bottom <= bitmap.height) {
                                        Bitmap.createBitmap(
                                            bitmap,
                                            bounds.left,
                                            bounds.top,
                                            bounds.width(),
                                            bounds.height()
                                        )
                                    } else {
                                        // If bounds are invalid, use full bitmap
                                        bitmap
                                    }
                                    
                                    // Process OCR
                                    processOCR(croppedBitmap, continuation)
                                    
                                    // Clean up
                                    if (croppedBitmap != bitmap) croppedBitmap.recycle()
                                    bitmap.recycle()
                                    
                                } catch (e: Exception) {
                                    Log.e(TAG, "Crop error: ${e.message}")
                                    continuation.resume("")
                                }
                            } else {
                                continuation.resume("")
                            }
                            hardwareBuffer.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Screenshot processing error: ${e.message}")
                            continuation.resume("")
                        }
                    }
                    
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with code: $errorCode")
                        continuation.resume("")
                    }
                }
            )
        } else {
            continuation.resume("")
        }
    }
    
    // ✅ Process OCR with ML Kit
    private fun processOCR(bitmap: Bitmap, continuation: kotlin.coroutines.Continuation<String>) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                var result = visionText.text
                    .replace(Regex("[^A-Za-z0-9]"), "")
                    .trim()
                    .uppercase()
                
                // Remove common confusing characters
                result = result.replace("O", "0")
                    .replace("I", "1")
                    .replace("Z", "2")
                    .take(8)
                
                Log.d(TAG, "📖 OCR Result: $result")
                continuation.resume(result)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failed: ${e.message}")
                continuation.resume("")
            }
    }
    
    // ✅ Method 2: Get text from content description (Fallback)
    private fun getTextFromContentDescription(node: AccessibilityNodeInfo): String {
        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrBlank()) {
            val cleaned = contentDesc.replace(Regex("[^A-Za-z0-9]"), "").uppercase()
            if (cleaned.length in 3..8) {
                Log.d(TAG, "📖 Captcha from content description: $cleaned")
                return cleaned
            }
        }
        return ""
    }
    
    // ✅ Method 3: Manual captcha wait (Last resort)
    private suspend fun waitForManualCaptcha(engine: WorkflowEngine, inputNode: AccessibilityNodeInfo) {
        Log.d(TAG, "⏳ Waiting for manual captcha input...")
        var attempts = 0
        var lastText = ""
        
        while (attempts < 40) { // Wait up to 4 seconds
            delay(100)
            val currentText = inputNode.text?.toString() ?: ""
            
            if (currentText.isNotBlank() && currentText != lastText && currentText.length in 3..8) {
                Log.d(TAG, "✅ Manual captcha detected: $currentText")
                delay(100)
                clickVerifyButton(engine)
                return
            }
            lastText = currentText
            attempts++
        }
        Log.w(TAG, "⚠️ No manual captcha entered")
    }
    
    // ✅ Click verify button after captcha
    private suspend fun clickVerifyButton(engine: WorkflowEngine) {
        delay(80)
        repeat(3) { // Try 3 times
            val root = engine.rootInActiveWindow ?: return
            val verifyBtn = engine.findNodeFast(
                root,
                listOf("Verify", "Submit", "Check", "जांचें", "सबमिट", "OK", "Continue"),
                ""
            )
            if (verifyBtn?.isClickable == true) {
                engine.humanClickFast(verifyBtn)
                Log.d(TAG, "✅ Verify button clicked")
                verifyBtn.recycle()
                root.recycle()
                return
            }
            root.recycle()
            delay(80)
        }
    }
    
    // ✅ Set text to input node
    private fun setTextToNode(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "✏️ Text set to input field: $text")
    }
}
