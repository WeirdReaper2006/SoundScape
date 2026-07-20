package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrefsKeysTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `migration copies legacy values into the new file`() {
        val legacyPrefs = context.getSharedPreferences(PrefsKeys.LEGACY_FILE_NAME, Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putString("music_path", "/storage/emulated/0/Music")
            .putBoolean("eq_enabled", true)
            .putInt("crossfade_duration", 5)
            .apply()

        PrefsKeys.migrateLegacyPrefsIfNeeded(context)

        val newPrefs = context.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
        assertEquals("/storage/emulated/0/Music", newPrefs.getString("music_path", null))
        assertTrue(newPrefs.getBoolean("eq_enabled", false))
        assertEquals(5, newPrefs.getInt("crossfade_duration", 0))
    }

    @Test
    fun `migration is idempotent and does not overwrite newer values on a second run`() {
        val legacyPrefs = context.getSharedPreferences(PrefsKeys.LEGACY_FILE_NAME, Context.MODE_PRIVATE)
        legacyPrefs.edit().putInt("crossfade_duration", 5).apply()

        PrefsKeys.migrateLegacyPrefsIfNeeded(context)

        val newPrefs = context.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
        newPrefs.edit().putInt("crossfade_duration", 9).apply()

        PrefsKeys.migrateLegacyPrefsIfNeeded(context)

        assertEquals(9, newPrefs.getInt("crossfade_duration", 0))
    }

    @Test
    fun `migration with no legacy data still marks migration complete`() {
        PrefsKeys.migrateLegacyPrefsIfNeeded(context)

        val newPrefs = context.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
        newPrefs.edit().putInt("crossfade_duration", 9).apply()

        PrefsKeys.migrateLegacyPrefsIfNeeded(context)

        assertEquals(9, newPrefs.getInt("crossfade_duration", 0))
    }
}
