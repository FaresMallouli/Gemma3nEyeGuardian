package com.example.eyeguardian

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.eyeguardian.helpers.AppPreferences
import java.util.Locale

// This class will be the parent for all activities in your app.
// It ensures the correct language is applied upon creation.
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Retrieve the saved language tag from SharedPreferences. Default to English if none is saved.
        val languageTag = AppPreferences.getSelectedLanguage(newBase) ?: "en-US"
        val locale = Locale.forLanguageTag(languageTag)

        // Create a new context with the updated locale configuration.
        val context = updateLocale(newBase, locale)

        // Attach the new, language-aware context to the activity.
        super.attachBaseContext(context)
    }

    private fun updateLocale(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val resources = context.resources
        val config = resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}