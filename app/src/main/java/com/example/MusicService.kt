package com.example

import android.content.Intent
import android.os.Build
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
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.data.db.AppDatabase
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private var equalizer: android.media.audiofx.Equalizer? = null
    private var bassBoost: android.media.audiofx.BassBoost? = null

    private val monoAudioProcessor = MonoAudioProcessor()
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

        repository = MusicRepository(this, AppDatabase.getDatabase(this).musicDao())

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                val processors = arrayOf<androidx.media3.common.audio.AudioProcessor>(monoAudioProcessor)
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(processors)
                    .build()
            }
        }
            
        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true) // Handles auto-pauses/resumes on call interruptions
            .setHandleAudioBecomingNoisy(true) // Pauses automatically when headphones are unplugged
            .build()

        player?.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    setupAudioEffects(audioSessionId)
                }
            }

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

                val prefs = getSharedPreferences("spotify_clone_prefs", MODE_PRIVATE)
                val gapless = prefs.getBoolean("gapless_playback", true)
                
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !gapless) {
                    player?.pause()
                    mainHandler.postDelayed({
                        player?.play()
                    }, 1000)
                }
                
                val crossfadeDurationSec = prefs.getInt("crossfade_duration", 0)
                if (crossfadeDurationSec > 0) {
                    player?.volume = 0f
                } else {
                    player?.volume = 1.0f
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

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .build()
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        try {
            equalizer?.release()
            bassBoost?.release()
            
            equalizer = android.media.audiofx.Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            
            // Save hardware bands info for UI
            equalizer?.let { eq ->
                val prefs = getSharedPreferences("spotify_clone_prefs", MODE_PRIVATE)
                val bandsCount = eq.numberOfBands.toInt()
                prefs.edit().putInt("eq_hardware_bands_count", bandsCount).apply()
                for (i in 0 until bandsCount) {
                    val freqHz = eq.getCenterFreq(i.toShort()) / 1000
                    prefs.edit().putInt("eq_hardware_band_freq_$i", freqHz).apply()
                }
            }

            bassBoost = android.media.audiofx.BassBoost(0, audioSessionId).apply {
                enabled = true
            }
            reloadAudioEffects()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun reloadAudioEffects() {
        try {
            val prefs = getSharedPreferences("spotify_clone_prefs", MODE_PRIVATE)
            val eqEnabled = prefs.getBoolean("eq_enabled", false)
            
            equalizer?.let { eq ->
                eq.enabled = eqEnabled
                if (eqEnabled) {
                    val bands = eq.numberOfBands.toInt()
                    for (i in 0 until bands) {
                        val level = prefs.getInt("eq_band_$i", 0)
                        eq.setBandLevel(i.toShort(), level.toShort())
                    }
                }
            }

            bassBoost?.let { bb ->
                val bbEnabled = prefs.getBoolean("bb_enabled", false)
                bb.enabled = bbEnabled
                if (bbEnabled) {
                    val strength = prefs.getInt("bb_strength", 0)
                    bb.setStrength(strength.toShort())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun reloadPlaybackSettings() {
        try {
            val prefs = getSharedPreferences("spotify_clone_prefs", MODE_PRIVATE)
            val monoEnabled = prefs.getBoolean("mono_audio", false)
            monoAudioProcessor.monoEnabled = monoEnabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkPlaybackProgress() {
        val p = player ?: return
        if (!p.isPlaying) return

        val currentPosition = p.currentPosition
        val duration = p.duration

        if (duration <= 0) return

        maybeRecordRecentPlay(p, currentPosition, duration)

        val prefs = getSharedPreferences("spotify_clone_prefs", MODE_PRIVATE)
        val crossfadeDurationSec = prefs.getInt("crossfade_duration", 0)
        val automixEnabled = prefs.getBoolean("automix", true)

        if (crossfadeDurationSec > 0) {
            val fadeDurationMs = crossfadeDurationSec * 1000L

            // With a single player (no true dual-track overlap), the fade-out below is what
            // makes crossfade audible: volume ramps down to 0 by the natural end of the track,
            // and the next item then starts via the normal auto transition. The manual "automix"
            // early-seek (used to skip trailing silence when crossfade is off) is skipped here:
            // triggering it during the fade window would cut the fade-out short at whatever
            // level it had reached, producing an audible jump instead of a smooth fade.

            // Crossfade volume
            if (duration - currentPosition <= fadeDurationMs) {
                // Fade out
                val fadeOutFactor = (duration - currentPosition).toFloat() / fadeDurationMs
                p.volume = fadeOutFactor.coerceIn(0f, 1f)
            } else if (currentPosition < fadeDurationMs) {
                // Fade in
                val fadeInFactor = currentPosition.toFloat() / fadeDurationMs
                p.volume = fadeInFactor.coerceIn(0f, 1f)
            } else {
                p.volume = 1.0f
            }
        } else {
            // No crossfade, check automix
            if (automixEnabled && !hasAutomixedCurrentTrack && p.hasNextMediaItem() && (duration - currentPosition <= 3000L)) {
                hasAutomixedCurrentTrack = true
                p.seekToNextMediaItem()
                return
            }
            p.volume = 1.0f
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
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE) {
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
        equalizer?.release()
        bassBoost?.release()
        equalizer = null
        bassBoost = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
