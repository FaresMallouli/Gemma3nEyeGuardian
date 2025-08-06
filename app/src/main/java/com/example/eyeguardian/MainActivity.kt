package com.example.eyeguardian

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.eyeguardian.databinding.ActivityMainBinding
import com.example.eyeguardian.helpers.AppPreferences

// ✅ UPDATED: Now includes logic for the new model selection UI
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private var modelUri: Uri? = null

    // ✅ ADDED: URLs for the models
    private val model2bUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/tree/main"
    private val model4bUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/tree/main"

    private val languageMap = linkedMapOf(
        "English (US)" to "en-US",
        "Japanese" to "ja-JP",
        "German" to "de-DE",
        "Korean" to "ko-KR",
        "Spanish (Spain)" to "es-ES",
        "French (France)" to "fr-FR"
    )
    private val languageDisplayNames = languageMap.keys.toTypedArray()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, getString(R.string.toast_permissions_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.toast_permissions_required), Toast.LENGTH_LONG).show()
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                modelUri = it
                val fileName = it.path?.substringAfterLast('/')
                // ✅ UPDATED: Correctly updates the text of the new button
                binding.btnSelectLocalModel.text = getString(R.string.main_button_select_model_selected, fileName ?: "Selected")
                Toast.makeText(this, getString(R.string.toast_model_selected), Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(this, getString(R.string.toast_failed_to_get_permission), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.SEND_SMS, Manifest.permission.RECORD_AUDIO))
        setupLanguageSpinner()
        loadSavedData()
        setupClickListeners()
    }

    private fun setupLanguageSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageDisplayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
    }

    private fun loadSavedData() {
        binding.etEmergencyContact.setText(AppPreferences.getEmergencyContact(this))
        binding.etUserInstructions.setText(AppPreferences.getUserInstructions(this))

        val savedLangTag = AppPreferences.getSelectedLanguage(this)
        val langIndex = languageMap.values.indexOf(savedLangTag)
        if (langIndex != -1) {
            binding.spinnerLanguage.setSelection(langIndex)
        }
    }

    // ✅ UPDATED: All click listeners are now correctly configured for the new layout
    private fun setupClickListeners() {
        binding.btnSaveContact.setOnClickListener {
            val phoneNumber = binding.etEmergencyContact.text.toString().trim()
            val instructions = binding.etUserInstructions.text.toString().trim()
            val selectedLanguageName = binding.spinnerLanguage.selectedItem.toString()
            val selectedLanguageTag = languageMap[selectedLanguageName] ?: "en-US"

            val previousLanguageTag = AppPreferences.getSelectedLanguage(this)

            if (phoneNumber.length > 6) {
                AppPreferences.saveEmergencyContact(this, phoneNumber)
                AppPreferences.saveUserInstructions(this, instructions)
                AppPreferences.saveSelectedLanguage(this, selectedLanguageTag)

                Toast.makeText(this, getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()

                if (previousLanguageTag != selectedLanguageTag) {
                    recreate()
                }
            } else {
                binding.etEmergencyContact.error = getString(R.string.error_invalid_phone_number)
            }
        }

        // Listener to open browser for the 2B (Medium) model
        binding.btnDownloadModel2b.setOnClickListener {
            openUrlInBrowser(model2bUrl)
        }

        // Listener to open browser for the 4B (Heavy) model
        binding.btnDownloadModel4b.setOnClickListener {
            openUrlInBrowser(model4bUrl)
        }

        // Listener for selecting the local downloaded file
        binding.btnSelectLocalModel.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("*/*"))
        }

        binding.btnStartAnalysis.setOnClickListener {
            val emergencyContact = AppPreferences.getEmergencyContact(this)
            val userInstructions = AppPreferences.getUserInstructions(this) ?: ""
            val languageTag = AppPreferences.getSelectedLanguage(this) ?: "en-US"

            when {
                modelUri == null -> Toast.makeText(this, getString(R.string.toast_select_model_first), Toast.LENGTH_SHORT).show()
                emergencyContact.isNullOrEmpty() -> Toast.makeText(this, getString(R.string.toast_save_contact_first), Toast.LENGTH_SHORT).show()
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> Toast.makeText(this, getString(R.string.toast_audio_permission_required), Toast.LENGTH_SHORT).show()
                else -> {
                    val intent = Intent(this, VideoAnalysisActivity::class.java).apply {
                        putExtra(VideoAnalysisActivity.EXTRA_MODEL_URI, modelUri.toString())
                        putExtra(VideoAnalysisActivity.EXTRA_EMERGENCY_CONTACT, emergencyContact)
                        putExtra(VideoAnalysisActivity.EXTRA_USER_INSTRUCTIONS, userInstructions)
                        putExtra(VideoAnalysisActivity.EXTRA_LANGUAGE_TAG, languageTag)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    // ✅ ADDED: Helper function to open a URL in the browser
    private fun openUrlInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open browser.", Toast.LENGTH_SHORT).show()
        }
    }
}