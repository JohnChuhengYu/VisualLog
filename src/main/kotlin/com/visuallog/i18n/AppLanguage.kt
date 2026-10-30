package com.visuallog.i18n

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "Auto / 跟随系统"),
    ENGLISH("en", "English"),
    CHINESE("zh", "简体中文");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
        }
    }
}
