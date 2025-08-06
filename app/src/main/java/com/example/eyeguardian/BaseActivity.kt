package com.example.eyeguardian

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.eyeguardian.helpers.AppPreferences
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val languageTag = AppPreferences.getSelectedLanguage(newBase) ?: "en-US"
        val locale = Locale.forLanguageTag(languageTag)

        val context = updateLocale(newBase, locale)

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