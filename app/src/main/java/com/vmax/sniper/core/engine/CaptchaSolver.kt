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

object CaptchaSolver {
    private const val TAG = "VMAX_Captcha"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var isSolving = false

    fun executeBypass(
        service: AccessibilityService,
        captchaImageNode: AccessibilityNodeInfo,
        captchaInputNode: AccessibilityNodeInfo
    ) {
        if (isSolving) return
        isSolving = true

        val bounds = Rect()
        captchaImageNode.getBoundsInScreen(bounds)

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
                            try {
                                val croppedBitmap = Bitmap.createBitmap(
                                    bitmap, bounds.left, bounds.top, bounds.width(), bounds.height()
                                )
                                hardwareBuffer.close()
                                processImageAndFill(croppedBitmap, captchaInputNode)
                            } catch (e: Exception) {
                                Log.e(TAG, "Crop Error: ${e.message}")
                                isSolving = false
                            }
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot Failed! Code: $errorCode")
                        isSolving = false
                    }
                }
            )
        } else {
            isSolving = false
        }
    }

    private fun processImageAndFill(bitmap: Bitmap, inputNode: AccessibilityNodeInfo) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val solvedText = visionText.text.replace(Regex("[^a-zA-Z0-9]"), "").trim()
                if (solvedText.isNotEmpty()) {
                    fillTextField(inputNode, solvedText)
                }
                Thread { Thread.sleep(2000); isSolving = false }.start()
            }
            .addOnFailureListener { isSolving = false }
    }

    private fun fillTextField(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
