package com.example

import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private var equalizer: android.media.audiofx.Equalizer? = null
    private var bassBoost: android.media.audiofx.BassBoost? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Handles auto-pauses/resumes on call interruptions
            .setHandleAudioBecomingNoisy(true) // Pauses automatically when headphones are unplugged
            .build()

        player?.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    setupAudioEffects(audioSessionId)
                }
            }
        })

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.example.ACTION_RELOAD_EFFECTS") {
            reloadAudioEffects()
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
