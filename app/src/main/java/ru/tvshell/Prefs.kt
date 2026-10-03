package ru.tvshell

import android.content.Context

/** Настройки оболочки: порядок избранного и скрытые приложения. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("shell", Context.MODE_PRIVATE)

    val favoritesInitialized: Boolean get() = sp.contains(KEY_FAV)

    var favorites: List<String>
        get() = sp.getString(KEY_FAV, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) {
            sp.edit().putString(KEY_FAV, value.distinct().joinToString(",")).apply()
        }

    var hidden: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, null)?.toSet() ?: emptySet()
        set(value) {
            sp.edit().putStringSet(KEY_HIDDEN, HashSet(value)).apply()
        }

    var signature: String
        get() = sp.getString(KEY_SIGNATURE, null) ?: DEFAULT_SIGNATURE
        set(value) {
            sp.edit().putString(KEY_SIGNATURE, value.trim()).apply()
        }

    companion object {
        const val DEFAULT_SIGNATURE = "made by kal1gula"
        private const val KEY_SIGNATURE = "signature"
        private const val KEY_FAV = "favorites"
        private const val KEY_HIDDEN = "hidden"
    }
}
