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
import kotlin.coroutines.resume

object CaptchaSolver {
    private const val TAG = "VMAX_Captcha"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var isSolving = false
    
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
            delay(50) // ✅ Reduced to 50ms for speed
            
            val bounds = Rect()
            captchaImageNode.getBoundsInScreen(bounds)
            
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                Log.e(TAG, "Invalid captcha bounds")
                isSolving = false
                return false
            }
            
            val solvedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                solveWithScreenshotAPI(engine, bounds)
            } else {
                getTextFromContentDescription(captchaImageNode)
            }
            
            if (solvedText.isNotBlank() && solvedText.length in 3..8) {
                engine.setTextFast(captchaInputNode, solvedText)
                delay(30)
                clickVerifyButton(engine)
                
                Log.d(TAG, "✅ Captcha solved successfully: $solvedText")
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
    
    // ✅ Method 1: Android 11+ Screenshot API (NO GlobalScope - FIXED)
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
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        var bitmap: Bitmap? = null
                        var croppedBitmap: Bitmap? = null
                        
                        try {
                            bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                            
                            if (bitmap != null && bounds.width() > 0 && bounds.height() > 0) {
                                val left = bounds.left.coerceIn(0, bitmap.width - 1)
                                val top = bounds.top.coerceIn(0, bitmap.height - 1)
                                val right = bounds.right.coerceIn(left + 1, bitmap.width)
                                val bottom = bounds.bottom.coerceIn(top + 1, bitmap.height)
                                val width = right - left
                                val height = bottom - top
                                
                                if (width > 0 && height > 0) {
                                    croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
                                    
                                    // ✅ FIX: Direct OCR call - NO GlobalScope
                                    val image = InputImage.fromBitmap(croppedBitmap!!, 0)
                                    
                                    recognizer.process(image)
                                        .addOnSuccessListener { visionText ->
                                            val result = cleanOCRText(visionText.text)
                                            Log.d(TAG, "📖 OCR Result: $result")
                                            
                                            // ✅ Cleanup after success
                                            croppedBitmap?.recycle()
                                            bitmap?.recycle()
                                            hardwareBuffer.close()
                                            
                                            continuation.resume(result)
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e(TAG, "OCR failed: ${e.message}")
                                            
                                            // ✅ Cleanup after failure
                                            croppedBitmap?.recycle()
                                            bitmap?.recycle()
                                            hardwareBuffer.close()
                                            
                                            continuation.resume("")
                                        }
                                    return // Don't go to finally
                                }
                            }
                            
                            // No valid captcha found
                            bitmap?.recycle()
                            hardwareBuffer.close()
                            continuation.resume("")
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Screenshot processing error: ${e.message}")
                            croppedBitmap?.recycle()
                            bitmap?.recycle()
                            hardwareBuffer.close()
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
    
    // ✅ Clean OCR Text with IRCTC Special Mapping
    private fun cleanOCRText(rawText: String): String {
        var result = rawText
            .replace(Regex("[^A-Za-z0-9]"), "")
            .trim()
            .uppercase()
        
        // ✅ ENHANCED: IRCTC Special Character Mapping (Beast Mode)
        result = result
            .replace("O", "0")
            .replace("Q", "0")
            .replace("D", "0")      // D looks like 0
            .replace("I", "1")
            .replace("l", "1")      // small L
            .replace("|", "1")      // pipe symbol
            .replace("Z", "2")
            .replace("S", "5")
            .replace("G", "6")
            .replace("B", "8")
            .replace("T", "7")
            .replace("E", "3")
            .replace("U", "V")      // U looks like V
            .take(6)                // ✅ IRCTC captcha is always 6 characters
        
        return result
    }
    
    // ✅ Method 2: Get text from content description (Enhanced)
    private fun getTextFromContentDescription(node: AccessibilityNodeInfo): String {
        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrBlank()) {
            var cleaned = contentDesc.replace(Regex("[^A-Za-z0-9]"), "").uppercase()
            cleaned = cleaned
                .replace("O", "0")
                .replace("Q", "0")
                .replace("D", "0")
                .replace("I", "1")
                .replace("l", "1")
                .replace("Z", "2")
                .replace("S", "5")
                .take(6)
            
            if (cleaned.length in 3..6) {
                Log.d(TAG, "📖 Captcha from content description: $cleaned")
                return cleaned
            }
        }
        return ""
    }
    
    // ✅ Method 3: Manual captcha wait (Last resort)
    private suspend fun waitForManualCaptcha(engine: WorkflowEngine, inputNode: AccessibilityNodeInfo) {
        Log.d(TAG, "⏳ Waiting for manual captcha input...")
        repeat(25) { // Reduced to 2.5 seconds
            delay(100)
            val currentText = inputNode.text?.toString() ?: ""
            if (currentText.isNotBlank() && currentText.length in 3..6) {
                Log.d(TAG, "✅ Manual captcha detected: $currentText")
                delay(50)
                clickVerifyButton(engine)
                return
            }
        }
        Log.w(TAG, "⚠️ No manual captcha entered")
    }
    
    // ✅ Click verify button after captcha (Super fast)
    private suspend fun clickVerifyButton(engine: WorkflowEngine) {
        delay(30) // ✅ Reduced to 30ms
        repeat(3) {
            val root = engine.rootInActiveWindow ?: return
            try {
                val verifyBtn = engine.findNodeFast(
                    root,
                    listOf("Verify", "Submit", "Check", "जांचें", "सबमिट", "OK", "Continue", "Proceed"),
                    ""
                )
                verifyBtn?.let {
                    if (it.isClickable) {
                        engine.humanClickFast(it)
                        Log.d(TAG, "✅ Verify button clicked")
                        it.recycle()
                        return
                    }
                    it.recycle()
                }
            } finally {
                root.recycle()
            }
            delay(30)
        }
    }
}
