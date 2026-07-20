package com.example.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsControllerTest {

    private lateinit var controller: SettingsController
    private var profileUpdatedCount = 0

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        profileUpdatedCount = 0
        controller = SettingsController(application, onProfileUpdated = { profileUpdatedCount++ })
        controller.initEqualizerSettings()
        controller.initPlaybackSettings()
    }

    @Test
    fun `updateProfile rejects an invalid name and does not trigger refresh`() {
        controller.updateProfile("A", "Music")
        assertEquals(0, profileUpdatedCount)
        assertEquals("New Listener", controller.userName)
    }

    @Test
    fun `updateProfile applies a valid name and path, then triggers refresh`() {
        controller.updateProfile("Rayyan", "Rock")
        assertEquals("Rayyan", controller.userName)
        assertEquals("Rock", controller.musicPath)
        assertTrue(controller.isOnboardingCompleted)
        assertEquals(1, profileUpdatedCount)
    }

    @Test
    fun `applyEqualizerPreset bass boost enables bass boost at max strength`() {
        controller.applyEqualizerPreset("Bass boost")
        assertEquals("Bass boost", controller.eqActivePreset)
        assertTrue(controller.bbEnabled)
        assertEquals(800, controller.bbStrength)
    }

    @Test
    fun `applyEqualizerPreset balanced disables bass boost and zeroes bands`() {
        controller.applyEqualizerPreset("Bass boost")
        controller.applyEqualizerPreset("Balanced")
        assertFalse(controller.bbEnabled)
        assertEquals(0, controller.bbStrength)
        assertTrue(controller.eqBands.all { it == 0 })
    }

    @Test
    fun `updateEqualizerBand manual edit marks preset custom`() {
        controller.applyEqualizerPreset("Balanced")
        controller.updateEqualizerBand(0, 500, isManual = true)
        assertEquals("Custom", controller.eqActivePreset)
        assertEquals(500, controller.eqBands[0])
    }

    @Test
    fun `toggleGaplessPlayback flips and persists across reload`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val initial = controller.gaplessPlaybackEnabled
        controller.toggleGaplessPlayback()
        assertEquals(!initial, controller.gaplessPlaybackEnabled)

        val reloaded = SettingsController(application, onProfileUpdated = {})
        reloaded.initPlaybackSettings()
        assertEquals(!initial, reloaded.gaplessPlaybackEnabled)
    }
}
