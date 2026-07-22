package com.example

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.data.AppContainer
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import com.example.util.AppLogger
import com.example.util.PrefsKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var crossfadePlayerController: CrossfadePlayerController
    private lateinit var routingPlayer: RoutingPlayer

    private data class AudioEffectsPair(
        var equalizer: android.media.audiofx.Equalizer?,
        var bassBoost: android.media.audiofx.BassBoost?
    )
    private val effectsBySessionId = mutableMapOf<Int, AudioEffectsPair>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasAutomixedCurrentTrack = false
    private var hasRecordedCurrentTrack = false

    private lateinit var repository: MusicRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val checkPlaybackRunnable = object : Runnable {
        override fun run() {
            checkPlaybackProgress()
            mainHandler.postDelayed(this, 100)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        PrefsKeys.migrateLegacyPrefsIfNeeded(this)
        repository = AppContainer.getRepository(this)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        crossfadePlayerController = CrossfadePlayerController(
            context = this,
            scope = serviceScope,
            buildRenderersFactory = ::buildRenderersFactory,
            audioAttributes = audioAttributes,
            onCanonicalPlayerChanged = { newPlayer -> routingPlayer.switchDelegate(newPlayer) },
            onAudioSessionIdChanged = { _, audioSessionId ->
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    setupAudioEffects(audioSessionId)
                }
            }
        )

        routingPlayer = RoutingPlayer(
            initial = crossfadePlayerController.canonicalPlayer(),
            onNavigationCommand = { crossfadePlayerController.cancelActiveCrossfade() },
            onRelease = { crossfadePlayerController.release() }
        )

        routingPlayer.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    mainHandler.removeCallbacks(checkPlaybackRunnable)
                    mainHandler.post(checkPlaybackRunnable)
                } else {
                    mainHandler.removeCallbacks(checkPlaybackRunnable)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                hasAutomixedCurrentTrack = false
                hasRecordedCurrentTrack = false

                val prefs = getSharedPreferences(PrefsKeys.FILE_NAME, MODE_PRIVATE)
                val gapless = prefs.getBoolean("gapless_playback", true)
                val crossfadeDurationSec = prefs.getInt("crossfade_duration", 0)

                // Deliberate, user-requested breathing room between tracks - orthogonal to true
                // gapless/crossfade, which is why it stays gated to crossfade being off: a fade
                // that's already in progress must never be followed by an artificial pause.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !gapless && crossfadeDurationSec == 0) {
                    val canonical = crossfadePlayerController.canonicalPlayer()
                    canonical.pause()
                    mainHandler.postDelayed({ canonical.play() }, 1000)
                }
            }
        })

        reloadPlaybackSettings()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, routingPlayer.player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun buildRenderersFactory(processor: MonoAudioProcessor): RenderersFactory {
        return object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                val processors = arrayOf<androidx.media3.common.audio.AudioProcessor>(processor)
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(processors)
                    .build()
            }
        }
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        try {
            effectsBySessionId[audioSessionId]?.let {
                it.equalizer?.release()
                it.bassBoost?.release()
            }

            val equalizer = android.media.audiofx.Equalizer(0, audioSessionId).apply {
                enabled = true
            }

            // Save hardware bands info for UI
            val prefs = getSharedPreferences(PrefsKeys.FILE_NAME, MODE_PRIVATE)
            val bandsCount = equalizer.numberOfBands.toInt()
            prefs.edit().apply {
                putInt("eq_hardware_bands_count", bandsCount)
                for (i in 0 until bandsCount) {
                    val freqHz = equalizer.getCenterFreq(i.toShort()) / 1000
                    putInt("eq_hardware_band_freq_$i", freqHz)
                }
            }.apply()

            val bassBoost = android.media.audiofx.BassBoost(0, audioSessionId).apply {
                enabled = true
            }

            effectsBySessionId[audioSessionId] = AudioEffectsPair(equalizer, bassBoost)
            reloadAudioEffectsFor(equalizer, bassBoost)
        } catch (e: Exception) {
            AppLogger.e("MusicService", "Failed to set up audio effects", e)
        }
    }

    private fun reloadAudioEffectsFor(eq: android.media.audiofx.Equalizer?, bb: android.media.audiofx.BassBoost?) {
        val prefs = getSharedPreferences(PrefsKeys.FILE_NAME, MODE_PRIVATE)
        val eqEnabled = prefs.getBoolean("eq_enabled", false)

        eq?.let {
            it.enabled = eqEnabled
            if (eqEnabled) {
                val bands = it.numberOfBands.toInt()
                for (i in 0 until bands) {
                    val level = prefs.getInt("eq_band_$i", 0)
                    it.setBandLevel(i.toShort(), level.toShort())
                }
            }
        }

        bb?.let {
            val bbEnabled = prefs.getBoolean("bb_enabled", false)
            it.enabled = bbEnabled
            if (bbEnabled) {
                val strength = prefs.getInt("bb_strength", 0)
                it.setStrength(strength.toShort())
            }
        }
    }

    private fun reloadAudioEffects() {
        try {
            effectsBySessionId.values.forEach { reloadAudioEffectsFor(it.equalizer, it.bassBoost) }
        } catch (e: Exception) {
            AppLogger.e("MusicService", "Failed to reload audio effects", e)
        }
    }

    private fun reloadPlaybackSettings() {
        try {
            val prefs = getSharedPreferences(PrefsKeys.FILE_NAME, MODE_PRIVATE)
            val monoEnabled = prefs.getBoolean("mono_audio", false)
            crossfadePlayerController.setMonoEnabled(monoEnabled)
        } catch (e: Exception) {
            AppLogger.e("MusicService", "Failed to reload playback settings", e)
        }
    }

    private fun checkPlaybackProgress() {
        val p = crossfadePlayerController.canonicalPlayer()
        if (!p.isPlaying) return

        val currentPosition = p.currentPosition
        val duration = p.duration

        if (duration <= 0) return

        maybeRecordRecentPlay(p, currentPosition, duration)

        val prefs = getSharedPreferences(PrefsKeys.FILE_NAME, MODE_PRIVATE)
        val crossfadeDurationSec = prefs.getInt("crossfade_duration", 0)
        val automixEnabled = prefs.getBoolean("automix", true)

        if (crossfadeDurationSec > 0) {
            // True overlap crossfade: both tracks are genuinely audible together, handled by
            // CrossfadePlayerController via a second ExoPlayer. Automix (early-seek to skip
            // trailing silence) is mutually exclusive with crossfade - triggering it during an
            // overlap window would cut the fade short instead of letting it complete.
            crossfadePlayerController.scheduleCrossfadeCheck(crossfadeDurationSec)
        } else if (automixEnabled && !hasAutomixedCurrentTrack && p.hasNextMediaItem() && (duration - currentPosition <= 3000L)) {
            hasAutomixedCurrentTrack = true
            p.seekToNextMediaItem()
        }
    }

    private fun maybeRecordRecentPlay(p: Player, currentPosition: Long, duration: Long) {
        if (hasRecordedCurrentTrack) return
        val threshold = minOf(30_000L, duration / 2)
        if (currentPosition < threshold) return

        hasRecordedCurrentTrack = true
        val mediaItem = p.currentMediaItem ?: return
        val song = songFromMediaItem(mediaItem, duration) ?: return
        serviceScope.launch { repository.addRecentPlay(song) }
    }

    private fun songFromMediaItem(item: MediaItem, playerDuration: Long): Song? {
        val md = item.mediaMetadata
        val extras = md.extras ?: return null
        val path = extras.getString("path") ?: return null
        return Song(
            id = item.mediaId,
            title = md.title?.toString() ?: "",
            artist = md.artist?.toString() ?: "",
            album = md.albumTitle?.toString() ?: "",
            path = path,
            durationMs = extras.getLong("durationMs", playerDuration),
            albumArtUri = extras.getString("albumArtUri"),
            isLocal = extras.getBoolean("isLocal", true),
            mimeType = item.localConfiguration?.mimeType
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.example.ACTION_RELOAD_EFFECTS") {
            reloadAudioEffects()
            reloadPlaybackSettings()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // playWhenReady alone reflects whether the user wants playback to continue. Also
        // requiring mediaItemCount > 0 / a non-IDLE state was racy: MediaController commands
        // (addMediaItem/prepare/play sent from playSong()) are dispatched to this service
        // asynchronously, so a queue started immediately before a task swipe could still show
        // mediaItemCount == 0 or STATE_IDLE here even though playback was genuinely requested,
        // causing playback to be killed instead of continuing in the background.
        if (player == null || !player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(checkPlaybackRunnable)
        serviceScope.cancel()
        effectsBySessionId.values.forEach {
            it.equalizer?.release()
            it.bassBoost?.release()
        }
        effectsBySessionId.clear()
        crossfadePlayerController.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
