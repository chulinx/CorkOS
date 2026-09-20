package com.winlator.cmod.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-app language override.
 *
 * The app ships English (`values/`) and Simplified Chinese (`values-zh-rCN/`). By default it follows
 * the system language; the "Mine" page lets the user pin a language instead.
 *
 * Activities must call [wrap] from `attachBaseContext` so the chosen locale applies everywhere —
 * resources, fragments and Compose content all read from the wrapped context.
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY_LANGUAGE = "language"

    const val SYSTEM = "system"
    const val CHINESE = "zh"
    const val ENGLISH = "en"

    @JvmStatic
    fun currentTag(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM)
            ?: SYSTEM

    @JvmStatic
    fun setLanguage(context: Context, tag: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, tag)
            .apply()
    }

    /** Returns a context configured with the user's chosen language, or [base] when following system. */
    @JvmStatic
    fun wrap(base: Context): Context {
        val tag = currentTag(base)
        if (tag == SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isEmpty()) return base
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }
}
