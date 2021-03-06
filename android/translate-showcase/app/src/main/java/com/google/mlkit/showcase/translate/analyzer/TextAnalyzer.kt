/*
 * Copyright 2020 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.google.mlkit.showcase.translate.analyzer

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.showcase.translate.util.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import java.lang.Exception


/**
 * Analyzes the frames passed in from the camera and returns any detected text within the requested
 * crop region.
 */
class TextAnalyzer(
    private val context: Context,
    private val result: MutableLiveData<String>,
    val widthCropPercent: Int,
    val heightCropPercent: Int
) : ImageAnalysis.Analyzer {
    private val detector = TextRecognition.getClient()

    // Flag to skip analyzing new available frames until previous analysis has finished.
    private var isBusy = false

    override fun analyze(imageProxy: ImageProxy, imageRotationDegrees: Int) {
        val mediaImage = imageProxy.image ?: return
        if (isBusy) return

        isBusy = true
        val convertImageToBitmap = ImageUtils.convertYuv420888ImageToBitmap(mediaImage)
        val cropRect = Rect(0, 0, mediaImage.width, mediaImage.height)

        // If the image is rotated by 90 (or 270) degrees, swap height and width when calculating
        // the crop.
        val (widthCrop, heightCrop) = when(imageRotationDegrees) {
            90, 270 -> Pair(heightCropPercent / 100f, widthCropPercent / 100f)
            else -> Pair(widthCropPercent / 100f, heightCropPercent / 100f)
        }
        cropRect.inset(
            (mediaImage.width * widthCrop / 2).toInt(),
            (mediaImage.height * heightCrop / 2).toInt()
        )
        val croppedBitmap =
            ImageUtils.rotateAndCrop(convertImageToBitmap, imageRotationDegrees, cropRect);
        recognizeTextOnDevice(InputImage.fromBitmap(croppedBitmap, 0)).addOnCompleteListener {
            isBusy = false
        }
    }

    private fun recognizeTextOnDevice(
        image: InputImage
    ): Task<Text> {
        // Pass image to an ML Kit Vision API
        return detector.process(image)
            .addOnSuccessListener { visionText ->
                // Task completed successfully
                result.value = visionText.text
            }
            .addOnFailureListener { exception ->
                // Task failed with an exception
                Log.e(TAG, "Text recognition error", exception)
                val message = getErrorMessage(exception)
                message?.let {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun getErrorMessage(exception: Exception): String? {
        val mlKitException = exception as? MlKitException ?: return exception.message
        return if (mlKitException.errorCode == MlKitException.UNAVAILABLE) {
            "Waiting for text recognition model to be downloaded"
        } else exception.message
    }

    companion object {
        private const val TAG = "TextAnalyzer"
    }
}