package com.visuallog.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.visuallog.data.repository.VisualLogRepository
import java.util.Locale

object LanguageManager {
    private const val PREF_KEY = "app_language"
    private var repository: VisualLogRepository? = null

    var currentLanguage by mutableStateOf(AppLanguage.SYSTEM)
        private set

    val effectiveLanguage: AppLanguage
        get() = when (currentLanguage) {
            AppLanguage.ENGLISH -> AppLanguage.ENGLISH
            AppLanguage.CHINESE -> AppLanguage.CHINESE
            AppLanguage.SYSTEM -> {
                val locale = Locale.getDefault()
                if (locale.language.equals("zh", ignoreCase = true)) {
                    AppLanguage.CHINESE
                } else {
                    AppLanguage.ENGLISH
                }
            }
        }

    val strings: AppStrings
        get() = when (effectiveLanguage) {
            AppLanguage.CHINESE -> ZhStrings
            else -> EnStrings
        }

    fun init(repo: VisualLogRepository) {
        repository = repo
        val saved = repo.getAppSetting(PREF_KEY)
        if (saved != null) {
            currentLanguage = AppLanguage.fromCode(saved)
        }
    }

    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
        repository?.setAppSetting(PREF_KEY, language.code)
    }

    fun toggleLanguage() {
        val next = when (effectiveLanguage) {
            AppLanguage.ENGLISH -> AppLanguage.CHINESE
            AppLanguage.CHINESE -> AppLanguage.ENGLISH
            else -> AppLanguage.CHINESE
        }
        setLanguage(next)
    }
}

/**
 * Convenient global accessor for reactive strings: S.save, S.export, etc.
 */
val S: AppStrings
    get() = LanguageManager.strings

