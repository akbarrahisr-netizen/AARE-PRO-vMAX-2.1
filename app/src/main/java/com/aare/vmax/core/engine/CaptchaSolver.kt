package com.aare.vmax.core.engine

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

object CaptchaSolver {
    private const val TAG = "VMAX_Captcha"
    
    // ML Kit क्लाइंट (ऑफ़लाइन और सुपर फ़ास्ट)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    // ताकि यह बार-बार ट्रिगर न हो
    private var isSolving = false

    fun executeBypass(
        service: AccessibilityService,
        captchaImageNode: AccessibilityNodeInfo,
        captchaInputNode: AccessibilityNodeInfo
    ) {
        if (isSolving) return
        isSolving = true

        Log.d(TAG, "🔍 Captcha Detected! Initiating ML Kit Bypass...")

        // 1. कैप्चा इमेज की स्क्रीन पर लोकेशन (Coordinates) निकालें
        val bounds = Rect()
        captchaImageNode.getBoundsInScreen(bounds)

        // 2. Android 11+ (API 30+) में स्क्रीनशॉट लेने का फास्ट तरीका
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        if (bitmap != null) {
                            // 3. पूरी स्क्रीन में से सिर्फ कैप्चा वाला हिस्सा काटें (Crop)
                            try {
                                val croppedBitmap = Bitmap.createBitmap(
                                    bitmap, bounds.left, bounds.top, bounds.width(), bounds.height()
                                )
                                hardwareBuffer.close()

                                // 4. फोटो को ML Kit को भेजें
                                processImageAndFill(croppedBitmap, captchaInputNode)
                            } catch (e: Exception) {
                                Log.e(TAG, "✂️ Crop Error: ${e.message}")
                                isSolving = false
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "📸 Screenshot Failed! Code: $errorCode")
                        isSolving = false
                    }
                }
            )
        } else {
            Log.e(TAG, "⚠️ Screenshot API requires Android 11+")
            isSolving = false
        }
    }

    private fun processImageAndFill(bitmap: Bitmap, inputNode: AccessibilityNodeInfo) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // 5. फालतू स्पेस और स्पेशल कैरेक्टर हटाएं (सिर्फ अक्षर और नंबर रखें)
                val solvedText = visionText.text.replace(Regex("[^a-zA-Z0-9]"), "").trim()
                Log.d(TAG, "🎯 Captcha Solved by AI: $solvedText")
                
                if (solvedText.isNotEmpty()) {
                    fillTextField(inputNode, solvedText)
                } else {
                    Log.w(TAG, "⚠️ AI could not read Captcha.")
                }
                
                // 2 सेकंड का कूलडाउन ताकि बार-बार फायर न हो
                Thread {
                    Thread.sleep(2000)
                    isSolving = false
                }.start()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ ML Kit OCR Failed: ${e.message}")
                isSolving = false
            }
    }

    private fun fillTextField(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
