package com.example.eyeguardian.helpers

import android.content.Context

object AppPreferences {
    private const val PREFS_NAME = "eyeguardian_prefs"
    private const val KEY_EMERGENCY_CONTACT = "emergency_contact_phone"
    private const val KEY_USER_INSTRUCTIONS = "user_instructions"
    private const val KEY_SELECTED_LANGUAGE = "selected_language_tag" // Key for selected language

    private fun getSharedPreferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveEmergencyContact(context: Context, phoneNumber: String) {
        getSharedPreferences(context).edit().putString(KEY_EMERGENCY_CONTACT, phoneNumber).apply()
    }

    fun getEmergencyContact(context: Context): String? {
        return getSharedPreferences(context).getString(KEY_EMERGENCY_CONTACT, null)
    }

    fun saveUserInstructions(context: Context, instructions: String) {
        getSharedPreferences(context).edit().putString(KEY_USER_INSTRUCTIONS, instructions).apply()
    }

    fun getUserInstructions(context: Context): String? {
        return getSharedPreferences(context).getString(KEY_USER_INSTRUCTIONS, null)
    }

    // Functions to save and retrieve the selected language tag (e.g., "en-US")
    fun saveSelectedLanguage(context: Context, languageTag: String) {
        getSharedPreferences(context).edit().putString(KEY_SELECTED_LANGUAGE, languageTag).apply()
    }

    fun getSelectedLanguage(context: Context): String? {
        return getSharedPreferences(context).getString(KEY_SELECTED_LANGUAGE, "en-US") // Default to US English
    }
}