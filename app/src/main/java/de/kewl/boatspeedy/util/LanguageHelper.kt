package de.kewl.boatspeedy.util

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/** Unterstützte App-Sprachen. */
enum class AppLanguage(val tag: String) { ENGLISH("en"), GERMAN("de") }

/**
 * App-Sprache über die System-Pro-App-Sprache (LocaleManager, Android 13+).
 * Das System persistiert die Wahl; Setzen löst ein Neuerstellen der Activity aus.
 */
object LanguageHelper {
    private fun manager(context: Context): LocaleManager =
        context.getSystemService(LocaleManager::class.java)

    fun current(context: Context): AppLanguage {
        val locales = manager(context).applicationLocales
        val lang = if (!locales.isEmpty) locales[0].language else "en"
        return if (lang == "de") AppLanguage.GERMAN else AppLanguage.ENGLISH
    }

    fun set(context: Context, language: AppLanguage) {
        manager(context).applicationLocales = LocaleList.forLanguageTags(language.tag)
    }

    /** Beim ersten Start Englisch erzwingen, falls noch keine App-Sprache gesetzt ist. */
    fun ensureDefault(context: Context) {
        if (manager(context).applicationLocales.isEmpty) {
            manager(context).applicationLocales = LocaleList.forLanguageTags("en")
        }
    }
}
