package com.example.eyeguardian.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.eyeguardian.R
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class Gemma3nHelper(
    private val context: Context,
    private val visionAnalysisPrompt: String,
    private val logger: (String) -> Unit
) {
    private var llmInference: LlmInference? = null
    private var llmInferenceSession: LlmInferenceSession? = null
    private var sessionOptions: LlmInferenceSession.LlmInferenceSessionOptions? = null

    companion object {
        private const val TAG = "Gemma3nHelper"
    }

    @SuppressLint("UnsafeOptInUsageError")
    suspend fun analyzeFrame(imageProxy: ImageProxy): String {
        if (llmInference == null || sessionOptions == null) {
            val errorMsg = "ERROR: LLM Inference or session options not initialized."
            logger(errorMsg)
            Log.w(TAG, errorMsg)
            imageProxy.close()
            return "ERROR: Session not created"
        }

        val bitmap: Bitmap = try {
            withContext(Dispatchers.Default) { imageProxy.toBitmap() }
        } catch (e: Exception) {
            logger("ERROR: Failed to convert ImageProxy to Bitmap.")
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
            return "ERROR: Could not convert ImageProxy to Bitmap."
        } finally {
            imageProxy.close()
        }

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            try {
                llmInferenceSession?.close()
                llmInferenceSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions!!)
                logger("Gemma: New session created for vision analysis.")

                val mpImage = BitmapImageBuilder(bitmap).build()

                llmInferenceSession?.addQueryChunk(visionAnalysisPrompt)
                llmInferenceSession?.addImage(mpImage)
                val result = llmInferenceSession?.generateResponse() ?: "ERROR: No response from model"

                val inferenceTime = System.currentTimeMillis() - startTime
                logger("Gemma: Vision inference success (${inferenceTime}ms). Result: '$result'")
                Log.d(TAG, "Inference successful. Time: ${inferenceTime}ms. Prompt: '$visionAnalysisPrompt' Result: '$result'")

                result
            } catch (e: Exception) {
                logger("Gemma ERROR: Vision inference failed: ${e.message}")
                Log.e(TAG, "Error during vision inference", e)
                "ERROR: Inference failed: ${e.message}"
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun analyzeTextResponse(userResponse: String): String {
        if (llmInference == null || sessionOptions == null) {
            val errorMsg = "ERROR: LLM Inference or session options not initialized."
            logger(errorMsg)
            Log.w(TAG, "LLM Inference Session not initialized. Aborting text analysis.")
            return "ERROR: Session not created"
        }

        return withContext(Dispatchers.IO) {
            try {
                llmInferenceSession?.close()
                llmInferenceSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions!!)
                logger("Gemma: New session created for text analysis.")

                val textAnalysisPrompt = context.getString(R.string.llm_text_analysis_prompt)
                val fullPrompt = textAnalysisPrompt + userResponse

                llmInferenceSession?.addQueryChunk(fullPrompt)
                val result = llmInferenceSession?.generateResponse() ?: "NEGATIVE"

                logger("Gemma: Text analysis success. Result: '$result'")
                Log.d(TAG, "Text analysis successful. Prompt: '$fullPrompt'. Result: '$result'")
                result

            } catch (e: Exception) {
                logger("Gemma ERROR: Text analysis failed: ${e.message}")
                Log.e(TAG, "Error during text analysis", e)
                "NEGATIVE"
            }
        }
    }

    fun loadModel(modelFile: File) {
        try {
            logger("Gemma: Loading model from ${modelFile.name}")
            val optionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setMaxNumImages(1)
                .setPreferredBackend(LlmInference.Backend.GPU)

            val llmInferenceOptions = optionsBuilder.build()
            llmInference = LlmInference.createFromOptions(context, llmInferenceOptions)
            logger("Gemma: Model loaded. Creating session options.")

            val graphOptions = GraphOptions.builder().setEnableVisionModality(true).build()
            this.sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setGraphOptions(graphOptions)
                .build()

            this.llmInferenceSession = LlmInferenceSession.createFromOptions(llmInference!!, this.sessionOptions!!)
            logger("Gemma: Initial LlmInferenceSession created.")
            Log.d(TAG, "LlmInferenceSession created successfully for vision tasks.")

        } catch (e: Exception) {
            logger("Gemma FATAL: Error loading model: ${e.message}")
            Log.e(TAG, "Error loading Gemma model or creating session", e)
            throw e
        }
    }

    fun cleanup() {
        logger("Gemma: Cleaning up resources.")
        llmInferenceSession?.close()
        llmInferenceSession = null
        llmInference?.close()
        llmInference = null
        Log.d(TAG, "Gemma resources cleaned up successfully.")
    }

    @Throws(IOException::class)
    fun copyModelToInternalStorage(uri: Uri): File {
        val modelFile = File(context.cacheDir, "gemma_model.task")
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            modelFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IOException("Unable to open model file from URI")
        return modelFile
    }
}