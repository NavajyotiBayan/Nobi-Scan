package com.nobi.scan.data

import android.content.Context
import androidx.core.content.edit
import com.nobi.scan.cv.DocumentFilter
import com.nobi.scan.ocr.OcrLanguage

internal enum class AppAppearance(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

internal data class ScanPreferences(
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val filter: DocumentFilter = DocumentFilter.DOCUMENT,
    val language: OcrLanguage = OcrLanguage.ENGLISH,
    val searchable: Boolean = false
)

internal class ScanPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun read(): ScanPreferences {
        val defaults = ScanPreferences()
        return ScanPreferences(
            appearance = when {
                !preferences.contains("dark_mode") -> AppAppearance.SYSTEM
                preferences.getBoolean("dark_mode", false) -> AppAppearance.DARK
                else -> AppAppearance.LIGHT
            },
            filter = DocumentFilter.entries.firstOrNull { it.name == preferences.getString("scan_filter", null) }
                ?: defaults.filter,
            language = OcrLanguage.entries.firstOrNull { it.name == preferences.getString("ocr_language", null) }
                ?: defaults.language,
            searchable = preferences.getBoolean("searchable_pdf", defaults.searchable)
        )
    }

    fun write(value: ScanPreferences) {
        preferences.edit {
            when (value.appearance) {
                AppAppearance.SYSTEM -> remove("dark_mode")
                AppAppearance.LIGHT -> putBoolean("dark_mode", false)
                AppAppearance.DARK -> putBoolean("dark_mode", true)
            }
            putString("scan_filter", value.filter.name)
            putString("ocr_language", value.language.name)
            putBoolean("searchable_pdf", value.searchable)
        }
    }
}
