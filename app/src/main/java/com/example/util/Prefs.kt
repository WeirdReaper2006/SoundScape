package com.example.util

import android.content.Context

/**
 * Single source of truth for the app's SharedPreferences file name, replacing 23 scattered
 * "spotify_clone_prefs" string-literal call sites. [FILE_NAME] is the current (SoundScape-branded)
 * file; [LEGACY_FILE_NAME] is the original name from before the app was rebranded from its
 * "Spotify clone" template origins - kept only as a one-time migration source, see [migrateLegacyPrefsIfNeeded].
 */
object PrefsKeys {
    const val FILE_NAME = "soundscape_prefs"
    const val LEGACY_FILE_NAME = "spotify_clone_prefs"
    private const val MIGRATION_MARKER = "migrated_from_legacy_prefs"

    /**
     * Copies every entry from the legacy prefs file into the new one, once. Idempotent and
     * additive only - the legacy file is left in place (never deleted) so a failure partway
     * through never loses user settings. Safe to call from multiple entry points (Activity and
     * Service can each start independently) since the marker short-circuits repeat runs.
     */
    fun migrateLegacyPrefsIfNeeded(context: Context) {
        val newPrefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        if (newPrefs.contains(MIGRATION_MARKER)) return

        val legacyPrefs = context.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE)
        newPrefs.edit().apply {
            legacyPrefs.all.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putStringSet(key, value as Set<String>)
                    }
                }
            }
            putBoolean(MIGRATION_MARKER, true)
        }.apply()
    }
}
