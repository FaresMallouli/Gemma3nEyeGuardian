package com.example.eyeguardian

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.eyeguardian.databinding.ActivityVideoAnalysisBinding
import com.example.eyeguardian.helpers.Gemma3nHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class VideoAnalysisActivity : BaseActivity(), TextToSpeech.OnInitListener, RecognitionListener {

    internal enum class AlertState { GREEN, YELLOW, RED }

    companion object {
        const val EXTRA_MODEL_URI = "extra_model_uri"
        const val EXTRA_EMERGENCY_CONTACT = "extra_emergency_contact"
        const val EXTRA_USER_INSTRUCTIONS = "extra_user_instructions"
        const val EXTRA_LANGUAGE_TAG = "extra_language_tag"
        private const val TAG = "VideoAnalysisActivity"
        private const val ACTION_SMS_SENT = "com.example.eyeguardian.SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.example.eyeguardian.SMS_DELIVERED"
    }

    private lateinit var binding: ActivityVideoAnalysisBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var gemma3nHelper: Gemma3nHelper

    private var modelUri: Uri? = null
    private var emergencyContactPhone: String? = null
    private var userInstructions: String? = null
    private var languageTag: String = "en-US"

    private var frameCounter = 0
    private val isProcessing = AtomicBoolean(false)
    private var currentAlertState: AlertState = AlertState.GREEN
    private var currentAlertReason: String = ""

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val isCheckInProgress = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val conversationLog = StringBuilder()

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val resultCode = resultCode
            when (resultCode) {
                Activity.RESULT_OK -> logToScreen("SMS: Sent successfully.")
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> logToScreen("SMS ERROR: Generic failure.")
                SmsManager.RESULT_ERROR_NO_SERVICE -> logToScreen("SMS ERROR: No service.")
                SmsManager.RESULT_ERROR_NULL_PDU -> logToScreen("SMS ERROR: Null PDU.")
                SmsManager.RESULT_ERROR_RADIO_OFF -> logToScreen("SMS ERROR: Radio off.")
            }
        }
    }

    private val smsDeliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (resultCode) {
                Activity.RESULT_OK -> logToScreen("SMS: Delivered successfully to contact.")
                Activity.RESULT_CANCELED -> logToScreen("SMS ERROR: Delivery failed or was canceled.")
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        logToScreen("Activity: onCreate")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsSentReceiver, IntentFilter(ACTION_SMS_SENT), RECEIVER_NOT_EXPORTED)
            registerReceiver(smsDeliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsSentReceiver, IntentFilter(ACTION_SMS_SENT))
            registerReceiver(smsDeliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED))
        }

        modelUri = intent.getStringExtra(EXTRA_MODEL_URI)?.let { Uri.parse(it) }
        emergencyContactPhone = intent.getStringExtra(EXTRA_EMERGENCY_CONTACT)
        userInstructions = intent.getStringExtra(EXTRA_USER_INSTRUCTIONS)
        languageTag = intent.getStringExtra(EXTRA_LANGUAGE_TAG) ?: "en-US"

        if (modelUri == null || emergencyContactPhone.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.toast_critical_data_missing), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(this)

        cameraExecutor = Executors.newSingleThreadExecutor()
        initializeModelAndStartMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsSentReceiver)
        unregisterReceiver(smsDeliveredReceiver)

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        logToScreen("Activity: onDestroy. Shutting down resources.")
        mainHandler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        cameraExecutor.shutdown()
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        if (::gemma3nHelper.isInitialized) {
            gemma3nHelper.cleanup()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale.forLanguageTag(languageTag)
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                logToScreen(getString(R.string.error_tts_lang_not_supported, languageTag))
                tts?.language = Locale.US
            } else {
                logToScreen("TTS: Engine initialized successfully for language '$languageTag'.")
            }
        } else {
            logToScreen("TTS ERROR: Engine failed to initialize.")
            Toast.makeText(this, getString(R.string.error_tts_init_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeModelAndStartMonitoring() {
        logToScreen("Init: Starting model initialization.")
        binding.statusTextView.text = getString(R.string.status_initializing_model)
        binding.progressBar.visibility = View.VISIBLE

        val basePrompt = getString(R.string.llm_vision_prompt_base)
        val fullPrompt = if (!userInstructions.isNullOrBlank()) {
            basePrompt + getString(R.string.llm_vision_prompt_context_suffix, userInstructions)
        } else {
            basePrompt
        }
        logToScreen("Using refined prompt...")

        gemma3nHelper = Gemma3nHelper(applicationContext, fullPrompt, this::logToScreen)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val modelFile = gemma3nHelper.copyModelToInternalStorage(modelUri!!)
                    gemma3nHelper.loadModel(modelFile)
                }
                logToScreen("Init: Model loaded successfully.")
                binding.statusTextView.text = getString(R.string.status_model_loaded)
                startCamera()
            } catch (e: Exception) {
                handleError(getString(R.string.error_initialization_failed, e.message))
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        logToScreen("Camera: Starting camera provider.")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
                logToScreen("Camera: Bound to lifecycle successfully.")
                updateUiForAlertState()
            } catch (exc: Exception) {
                handleError(getString(R.string.error_camera_binding_failed, exc.message))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessing.get() || isCheckInProgress.get()) {
            imageProxy.close()
            return
        }

        if (frameCounter % 15 == 0) {
            runFullAnalysis(imageProxy)
        } else {
            imageProxy.close()
        }
        frameCounter++
    }

    private fun runFullAnalysis(imageProxy: ImageProxy) {
        if (isProcessing.getAndSet(true)) {
            imageProxy.close()
            return
        }
        logToScreen("Analysis: Starting full frame analysis.")
        lifecycleScope.launch {
            try {
                val resultString = gemma3nHelper.analyzeFrame(imageProxy)
                logToScreen("Analysis: Received result: $resultString")
                parseResultAndUpdateState(resultString)
            } catch (e: Exception) {
                logToScreen("Analysis ERROR: ${e.message}")
                Log.e(TAG, "Error during analysis", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private fun parseResultAndUpdateState(result: String) {
        val parts = result.split("|").map { it.trim() }
        val code = parts.getOrNull(0)
        val issueLabel = parts.getOrNull(1) ?: "Unspecified issue detected."

        val newState = when (code) {
            "CODE_RED" -> AlertState.RED
            "CODE_YELLOW" -> AlertState.YELLOW
            "CODE_GREEN" -> AlertState.GREEN
            else -> {
                logToScreen("Parser: Unknown code in result: $result")
                null
            }
        }

        if (newState == null || newState == currentAlertState) return

        logToScreen("State change: $currentAlertState -> $newState. Issue Label: $issueLabel")

        when (newState) {
            AlertState.GREEN -> {
                currentAlertState = AlertState.GREEN
                currentAlertReason = ""
                isCheckInProgress.set(false)
                mainHandler.removeCallbacksAndMessages(null)
                updateUiForAlertState()
                addToConversationLog("SYSTEM", "Situation resolved. Resuming normal monitoring.")
            }
            AlertState.RED -> {
                currentAlertReason = issueLabel
                escalateToRed(issueLabel)
            }
            AlertState.YELLOW -> {
                if (currentAlertState != AlertState.RED) {
                    currentAlertReason = issueLabel
                    triggerInteractiveCheckIn(issueLabel)
                }
            }
        }
    }

    private fun triggerInteractiveCheckIn(description: String) {
        if (isCheckInProgress.compareAndSet(false, true)) {
            logToScreen("Check-in: Triggered. Reason: $description")
            currentAlertState = AlertState.YELLOW
            updateUiForAlertState()
            speak(getString(R.string.tts_check_in_prompt, description))
        } else {
            logToScreen("Check-in: Trigger requested but one is already in progress.")
        }
    }

    private fun speak(text: String) {
        addToConversationLog("APP", text)
        logToScreen("TTS: Speaking '$text'. Scheduling listener.")
        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceCompletedListener { completedId ->
            if (completedId == utteranceId) {
                mainHandler.post {
                    if (isCheckInProgress.get()) {
                        listen()
                    } else {
                        logToScreen("TTS: Listener timer fired, but check-in was cancelled.")
                    }
                }
            }
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun listen() {
        if (!isFinishing && SpeechRecognizer.isRecognitionAvailable(this)) {
            logToScreen("STT: Starting listener with language '$languageTag'.")
            val sttIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
            }
            speechRecognizer?.startListening(sttIntent)
        } else {
            logToScreen("STT ERROR: Service not available. Escalating.")
            escalateToRed(getString(R.string.escalation_reason_stt_unavailable))
        }
    }

    private fun processSttResult(fullPhrase: String) {
        if (!isCheckInProgress.get()) return
        addToConversationLog("USER", fullPhrase)
        logToScreen("LLM: Analyzing user text: '$fullPhrase'")
        lifecycleScope.launch {
            try {
                val llmClassification = gemma3nHelper.analyzeTextResponse(fullPhrase)
                logToScreen("LLM: Text analysis result: $llmClassification")
                if (llmClassification.contains("POSITIVE")) {
                    currentAlertState = AlertState.GREEN
                    currentAlertReason = ""
                    updateUiForAlertState()
                } else {
                    escalateToRed(getString(R.string.escalation_reason_distress_signal, fullPhrase))
                }
            } catch (e: Exception) {
                escalateToRed(getString(R.string.escalation_reason_analysis_error))
            } finally {
                isCheckInProgress.set(false)
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) { logToScreen("STT: Ready for speech.") }
    override fun onBeginningOfSpeech() { logToScreen("STT: Speech started.") }
    override fun onEndOfSpeech() { logToScreen("STT: End of speech detected.") }

    override fun onError(error: Int) {
        if (!isCheckInProgress.get()) return
        val reason = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> getString(R.string.stt_error_no_match)
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.stt_error_timeout)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.stt_error_permission)
            else -> getString(R.string.stt_error_unknown)
        }
        logToScreen("STT ERROR: $reason. Escalating.")
        escalateToRed(getString(R.string.escalation_reason_stt_failed, reason))
        isCheckInProgress.set(false)
    }

    override fun onResults(results: Bundle?) {
        if (!isCheckInProgress.get()) return
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            logToScreen("STT: Received text: '${matches[0]}'")
            processSttResult(matches[0])
        } else {
            logToScreen("STT: No matches found. Escalating.")
            escalateToRed(getString(R.string.escalation_reason_no_clear_response))
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    private fun escalateToRed(finalTriggerReason: String) {
        if (currentAlertState == AlertState.RED) {
            logToScreen("Escalation: Already in RED state. Ignoring new trigger.")
            return
        }

        logToScreen("Escalation: Triggered RED state. Final Trigger: $finalTriggerReason")
        isCheckInProgress.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        currentAlertState = AlertState.RED
        updateUiForAlertState()

        val alertMessage = if (currentAlertReason.isNotBlank() && currentAlertReason != finalTriggerReason) {
            getString(R.string.sms_alert_escalated, currentAlertReason, finalTriggerReason)
        } else {
            getString(R.string.sms_alert_direct, finalTriggerReason)
        }

        addToConversationLog("SYSTEM", "Critical Alert Triggered. Sending SMS to emergency contact.")
        sendSmsAlert(alertMessage)
    }

    private fun updateUiForAlertState() {
        runOnUiThread {
            val panel = binding.alertPanel
            when (currentAlertState) {
                AlertState.RED -> {
                    panel.visibility = View.VISIBLE
                    panel.setCardBackgroundColor(ContextCompat.getColor(this, R.color.alert_red_bg))
                    binding.alertIcon.setImageResource(R.drawable.ic_error_red)
                    binding.alertTitle.text = getString(R.string.alert_title_critical)
                    binding.alertDescription.text = getString(R.string.alert_description_critical_event)
                    binding.statusTextView.text = getString(R.string.status_critical_help_requested)
                    binding.conversationScrollView.visibility = View.VISIBLE
                }
                AlertState.YELLOW -> {
                    panel.visibility = View.VISIBLE
                    panel.setCardBackgroundColor(ContextCompat.getColor(this, R.color.alert_yellow_bg))
                    binding.alertIcon.setImageResource(R.drawable.ic_warning_amber)
                    binding.alertTitle.text = getString(R.string.alert_title_unusual_situation)
                    binding.alertDescription.text = getString(R.string.alert_description_checking_in)
                    binding.statusTextView.text = getString(R.string.status_checking_in)
                    binding.conversationScrollView.visibility = View.VISIBLE
                }
                AlertState.GREEN -> {
                    panel.visibility = View.GONE
                    binding.statusTextView.text = getString(R.string.status_monitoring_all_clear)
                }
            }
        }
    }

    private fun addToConversationLog(speaker: String, message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $speaker: $message\n"
            conversationLog.append(logEntry)
            binding.conversationLogTextView.text = conversationLog.toString()
            binding.conversationScrollView.post {
                binding.conversationScrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun sendSmsAlert(message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            handleError(getString(R.string.error_sms_permission_not_granted))
            return
        }

        try {
            val smsManager = SmsManager.getDefault()

            val intentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentPI: PendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_SMS_SENT), intentFlags)
            val deliveredPI: PendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_SMS_DELIVERED), intentFlags)

            val parts = smsManager.divideMessage(message)
            val sentIntents = ArrayList<PendingIntent>()
            val deliveredIntents = ArrayList<PendingIntent>()

            for (i in parts.indices) {
                sentIntents.add(sentPI)
                deliveredIntents.add(deliveredPI)
            }

            smsManager.sendMultipartTextMessage(emergencyContactPhone, null, parts, sentIntents, deliveredIntents)

            logToScreen("SMS: Dispatch request sent to system for $emergencyContactPhone.")

        } catch (e: Exception) {
            Log.e(TAG, "SMS sending failed for phone number: $emergencyContactPhone", e)
            handleError(getString(R.string.error_sms_sending_failed, e.message))
        }
    }

    private fun handleError(message: String) {
        logToScreen("HANDLE_ERROR: $message")
        Log.e(TAG, message)
        binding.statusTextView.text = "ERROR: $message"
        Toast.makeText(this, "ERROR: $message", Toast.LENGTH_LONG).show()
    }

    private fun logToScreen(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message\n"
            binding.diagnosticLogTextView.append(logEntry)
            binding.diagnosticLogScrollView.post {
                binding.diagnosticLogScrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
}