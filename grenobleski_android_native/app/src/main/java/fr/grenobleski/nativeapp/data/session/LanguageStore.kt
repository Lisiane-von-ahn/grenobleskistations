package fr.grenobleski.nativeapp.data.session

import android.content.Context

class LanguageStore(context: Context) {
    private val prefs = context.getSharedPreferences("grenobleski_native_settings", Context.MODE_PRIVATE)

    fun saveLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun loadLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE).orEmpty().ifBlank { DEFAULT_LANGUAGE }
    }

    private companion object {
        const val KEY_LANGUAGE = "app_language"
        const val DEFAULT_LANGUAGE = "fr"
    }
}
