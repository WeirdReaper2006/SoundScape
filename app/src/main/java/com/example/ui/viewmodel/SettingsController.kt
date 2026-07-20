package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.MusicService
import com.example.util.InputValidator
import com.example.util.PrefsKeys

/**
 * Owns profile/theme, equalizer + bass boost, and playback-settings persistence, extracted out of
 * MusicViewModel as a facade delegate: MusicViewModel forwards its public properties/functions
 * for this domain here unchanged, so screen composables never see this type and don't change.
 * [onProfileUpdated] lets [updateProfile] trigger a library refresh without this class needing to
 * know about song loading.
 */
class SettingsController(
    private val application: Application,
    private val onProfileUpdated: () -> Unit
) {
    // Onboarding & User Profile States
    var userName by mutableStateOf("New Listener")
        private set

    var musicPath by mutableStateOf("Music")
        private set

    var isOnboardingCompleted by mutableStateOf(false)
        private set

    // Dynamic Theme Settings
    var themePreset by mutableStateOf("green")
        private set

    var themeIsDark by mutableStateOf(true)
        private set

    var themeCustomColor by mutableStateOf("#00E5FF")
        private set

    // Equalizer & Bass Boost states
    var eqEnabled by mutableStateOf(false)
        private set

    var bbEnabled by mutableStateOf(false)
        private set

    var bbStrength by mutableStateOf(0)
        private set

    val eqBands = mutableStateListOf<Int>()

    var eqActivePreset by mutableStateOf("Flat")
        private set

    // Playback settings states
    var gaplessPlaybackEnabled by mutableStateOf(true)
        private set

    var automixEnabled by mutableStateOf(true)
        private set

    var crossfadeDurationSec by mutableStateOf(0)
        private set

    var monoAudioEnabled by mutableStateOf(false)
        private set

    private fun prefs() = application.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)

    private fun showRejectionToast(reason: String) {
        Toast.makeText(application, reason, Toast.LENGTH_SHORT).show()
    }

    fun loadProfile() {
        val prefs = prefs()
        userName = prefs.getString("user_name", "Listener") ?: "Listener"
        musicPath = prefs.getString("music_path", "Music") ?: "Music"
        isOnboardingCompleted = prefs.getBoolean("onboarding_completed", false)
        themePreset = prefs.getString("theme_preset", "green") ?: "green"
        themeIsDark = prefs.getBoolean("theme_is_dark", true)
        themeCustomColor = prefs.getString("theme_custom_color", "#00E5FF") ?: "#00E5FF"
    }

    fun updateProfile(name: String, path: String) {
        val nameCheck = InputValidator.validateName(name)
        if (nameCheck is InputValidator.ValidationResult.Invalid) {
            showRejectionToast(nameCheck.reason)
            return
        }
        if (path.isNotBlank()) {
            val pathCheck = InputValidator.validateFolderSuffix(path)
            if (pathCheck is InputValidator.ValidationResult.Invalid) {
                showRejectionToast(pathCheck.reason)
                return
            }
        }
        prefs().edit()
            .putString("user_name", name.trim())
            .putString("music_path", path.trim())
            .putBoolean("onboarding_completed", true)
            .apply()
        userName = name.trim()
        musicPath = path.trim()
        isOnboardingCompleted = true
        onProfileUpdated()
    }

    fun updateTheme(preset: String, isDark: Boolean, customColorHex: String) {
        prefs().edit()
            .putString("theme_preset", preset)
            .putBoolean("theme_is_dark", isDark)
            .putString("theme_custom_color", customColorHex)
            .apply()
        themePreset = preset
        themeIsDark = isDark
        themeCustomColor = customColorHex
    }

    fun previewTheme(preset: String, isDark: Boolean, customColorHex: String) {
        themePreset = preset
        themeIsDark = isDark
        themeCustomColor = customColorHex
    }

    // ---------------- PREMIUM EQUALIZER & BASS BOOST ----------------
    fun initEqualizerSettings() {
        val prefs = prefs()
        eqEnabled = prefs.getBoolean("eq_enabled", false)
        bbEnabled = prefs.getBoolean("bb_enabled", false)
        bbStrength = prefs.getInt("bb_strength", 0)
        eqActivePreset = prefs.getString("eq_active_preset", "Balanced") ?: "Balanced"
        val bandsCount = prefs.getInt("eq_hardware_bands_count", 5)
        eqBands.clear()
        for (i in 0 until bandsCount) {
            val level = prefs.getInt("eq_band_$i", 0)
            eqBands.add(level)
        }
    }

    fun getBandFrequencyLabel(index: Int): String {
        val prefs = prefs()
        val defaultFreqs = listOf(60, 230, 910, 3600, 14000)
        val freqHz = prefs.getInt("eq_hardware_band_freq_$index", defaultFreqs.getOrElse(index) { 1000 })
        return if (freqHz >= 1000) {
            if (freqHz % 1000 == 0) {
                "${freqHz / 1000}k"
            } else {
                val divided = freqHz / 1000f
                if (divided == 3.6f) "3.6k" else "${divided}k"
            }
        } else {
            "$freqHz"
        }
    }

    fun toggleEqualizer() {
        eqEnabled = !eqEnabled
        prefs().edit().putBoolean("eq_enabled", eqEnabled).apply()
        notifyServiceReloadEffects()
    }

    fun toggleBassBoost() {
        bbEnabled = !bbEnabled
        prefs().edit().putBoolean("bb_enabled", bbEnabled).apply()
        notifyServiceReloadEffects()
    }

    fun updateEqualizerBand(bandIndex: Int, level: Int, isManual: Boolean = false) {
        if (bandIndex in eqBands.indices) {
            eqBands[bandIndex] = level
            val prefs = prefs()
            if (isManual) {
                eqActivePreset = "Custom"
                prefs.edit().putString("eq_active_preset", "Custom").apply()
            }
            prefs.edit().putInt("eq_band_$bandIndex", level).apply()
            notifyServiceReloadEffects()
        }
    }

    fun updateBassBoostStrength(strength: Int) {
        bbStrength = strength
        prefs().edit().putInt("bb_strength", strength).apply()
        notifyServiceReloadEffects()
    }

    fun applyEqualizerPreset(presetName: String) {
        val standard5Bands = when (presetName.lowercase()) {
            "balanced" -> listOf(0, 0, 0, 0, 0)
            "bass boost" -> listOf(800, 600, 300, 0, 0)
            "smooth" -> listOf(-200, 100, 300, 200, -100)
            "dynamic" -> listOf(600, 200, -200, 200, 600)
            "clear" -> listOf(-200, 0, 400, 300, 100)
            "treble boost" -> listOf(0, 0, 200, 600, 800)
            else -> listOf(0, 0, 0, 0, 0) // Flat / Custom default
        }
        eqActivePreset = presetName
        val prefs = prefs()
        prefs.edit().putString("eq_active_preset", presetName).apply()

        // Dynamic Bass Boost integration for Bass boost preset
        if (presetName.lowercase() == "bass boost") {
            bbEnabled = true
            bbStrength = 800
            prefs.edit().putBoolean("bb_enabled", true).putInt("bb_strength", 800).apply()
        } else {
            bbEnabled = false
            bbStrength = 0
            prefs.edit().putBoolean("bb_enabled", false).putInt("bb_strength", 0).apply()
        }

        val bandsCount = eqBands.size
        for (i in 0 until bandsCount) {
            val value = if (bandsCount == 5) {
                standard5Bands[i]
            } else {
                val fraction = i.toFloat() / (bandsCount - 1).coerceAtLeast(1)
                val sourceIndexFloat = fraction * 4
                val lowerIndex = sourceIndexFloat.toInt().coerceIn(0, 4)
                val upperIndex = (lowerIndex + 1).coerceIn(0, 4)
                val diff = sourceIndexFloat - lowerIndex
                val lowerVal = standard5Bands[lowerIndex]
                val upperVal = standard5Bands[upperIndex]
                (lowerVal + diff * (upperVal - lowerVal)).toInt()
            }
            updateEqualizerBand(i, value, isManual = false)
        }
    }

    // ---------------- PLAYBACK SETTINGS ----------------
    fun initPlaybackSettings() {
        val prefs = prefs()
        gaplessPlaybackEnabled = prefs.getBoolean("gapless_playback", true)
        automixEnabled = prefs.getBoolean("automix", true)
        crossfadeDurationSec = prefs.getInt("crossfade_duration", 0)
        monoAudioEnabled = prefs.getBoolean("mono_audio", false)
    }

    fun toggleGaplessPlayback() {
        gaplessPlaybackEnabled = !gaplessPlaybackEnabled
        prefs().edit().putBoolean("gapless_playback", gaplessPlaybackEnabled).apply()
        notifyServiceReloadEffects()
    }

    fun toggleAutomix() {
        automixEnabled = !automixEnabled
        prefs().edit().putBoolean("automix", automixEnabled).apply()
        notifyServiceReloadEffects()
    }

    fun updateCrossfadeDuration(seconds: Int) {
        crossfadeDurationSec = seconds
        prefs().edit().putInt("crossfade_duration", seconds).apply()
        notifyServiceReloadEffects()
    }

    fun toggleMonoAudio() {
        monoAudioEnabled = !monoAudioEnabled
        prefs().edit().putBoolean("mono_audio", monoAudioEnabled).apply()
        notifyServiceReloadEffects()
    }

    private fun notifyServiceReloadEffects() {
        val intent = Intent(application, MusicService::class.java).apply {
            action = "com.example.ACTION_RELOAD_EFFECTS"
        }
        application.startService(intent)
    }
}
