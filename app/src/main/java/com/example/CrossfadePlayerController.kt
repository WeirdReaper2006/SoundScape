package com.example

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import com.example.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the two real ExoPlayer instances behind a crossfade transition. Exactly one of them
 * ("canonical") holds the app's full queue playlist at any moment; the other ("idle") only ever
 * holds a single borrowed copy of the upcoming track while an overlap is in progress. This avoids
 * ever needing to keep two independent full playlists in sync - MusicViewModel's queue mutations
 * (add/remove/reorder/seek/skip) always target whichever player [canonicalPlayer] currently
 * returns, routed there by [RoutingPlayer].
 */
class CrossfadePlayerController(
    context: Context,
    private val scope: CoroutineScope,
    buildRenderersFactory: (MonoAudioProcessor) -> RenderersFactory,
    audioAttributes: AudioAttributes,
    private val onCanonicalPlayerChanged: (ExoPlayer) -> Unit,
    private val onAudioSessionIdChanged: (ExoPlayer, Int) -> Unit
) {
    private val monoProcessorA = MonoAudioProcessor()
    private val monoProcessorB = MonoAudioProcessor()

    private val playerA: ExoPlayer = ExoPlayer.Builder(context, buildRenderersFactory(monoProcessorA))
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val playerB: ExoPlayer = ExoPlayer.Builder(context, buildRenderersFactory(monoProcessorB))
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    @Volatile private var canonical: ExoPlayer = playerA
    @Volatile private var idle: ExoPlayer = playerB

    private var fadeJob: Job? = null
    private var fadeArmedForItemIndex: Int = -1

    init {
        playerA.addListener(sessionIdListener(playerA))
        playerB.addListener(sessionIdListener(playerB))
    }

    private fun sessionIdListener(player: ExoPlayer) = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            onAudioSessionIdChanged.invoke(player, audioSessionId)
        }
    }

    fun canonicalPlayer(): ExoPlayer = canonical

    private val isCrossfading: Boolean get() = fadeJob?.isActive == true

    fun setMonoEnabled(enabled: Boolean) {
        monoProcessorA.monoEnabled = enabled
        monoProcessorB.monoEnabled = enabled
    }

    /** Called every ~100ms from MusicService's progress poll while crossfade duration > 0. */
    fun scheduleCrossfadeCheck(crossfadeDurationSec: Int) {
        if (crossfadeDurationSec <= 0 || isCrossfading) return
        val p = canonical
        if (!p.isPlaying) return

        val duration = p.duration
        if (duration <= 0) return

        val position = p.currentPosition
        val currentIndex = p.currentMediaItemIndex
        if (fadeArmedForItemIndex == currentIndex) return
        if (!p.hasNextMediaItem()) return

        val configuredMs = crossfadeDurationSec * 1000L
        if (duration - position > configuredMs) return

        val nextIndex = currentIndex + 1
        val nextItem = p.getMediaItemAt(nextIndex)
        // Clamp to the current track's own remaining length so a track shorter than the
        // configured crossfade duration doesn't try to overlap past its own end.
        val overlapMs = minOf(configuredMs, duration - position).coerceAtLeast(250L)
        beginCrossfade(p, nextItem, nextIndex, overlapMs)
    }

    private fun beginCrossfade(fromPlayer: ExoPlayer, nextItem: MediaItem, nextItemIndex: Int, durationMs: Long) {
        fadeArmedForItemIndex = fromPlayer.currentMediaItemIndex
        val toPlayer = idle

        fadeJob = scope.launch(Dispatchers.Main) {
            try {
                toPlayer.setMediaItem(nextItem)
                toPlayer.volume = 0f
                toPlayer.prepare()

                var waitedMs = 0L
                while (toPlayer.playbackState != Player.STATE_READY && waitedMs < 4000L) {
                    delay(20)
                    waitedMs += 20
                }
                if (toPlayer.playbackState != Player.STATE_READY) {
                    AppLogger.e("CrossfadePlayerController", "Overlap player never became ready; skipping crossfade")
                    toPlayer.stop()
                    return@launch
                }

                toPlayer.play()

                val steps = (durationMs / 20L).toInt().coerceAtLeast(1)
                val stepDelayMs = durationMs / steps
                for (i in 1..steps) {
                    val t = i.toFloat() / steps
                    fromPlayer.volume = (1f - t).coerceIn(0f, 1f)
                    toPlayer.volume = t.coerceIn(0f, 1f)
                    delay(stepDelayMs)
                }
                fromPlayer.volume = 0f
                toPlayer.volume = 1f

                promoteToCanonical(fromPlayer, toPlayer, nextItemIndex)
            } finally {
                fadeArmedForItemIndex = -1
            }
        }
    }

    private fun promoteToCanonical(fromPlayer: ExoPlayer, toPlayer: ExoPlayer, nextItemIndex: Int) {
        val fullItems = (0 until fromPlayer.mediaItemCount).map { fromPlayer.getMediaItemAt(it) }
        val remainingAfterNext = fullItems.drop(nextItemIndex + 1)
        if (remainingAfterNext.isNotEmpty()) {
            toPlayer.addMediaItems(1, remainingAfterNext)
        }
        toPlayer.repeatMode = fromPlayer.repeatMode
        toPlayer.shuffleModeEnabled = fromPlayer.shuffleModeEnabled

        fromPlayer.pause()
        fromPlayer.stop()
        fromPlayer.clearMediaItems()
        fromPlayer.volume = 1f

        canonical = toPlayer
        idle = fromPlayer

        onCanonicalPlayerChanged(toPlayer)
    }

    /** Called before any manual play/pause/seek/skip is forwarded, so navigation is instant. */
    fun cancelActiveCrossfade() {
        val job = fadeJob ?: return
        if (!job.isActive) return
        job.cancel()
        fadeArmedForItemIndex = -1

        val activeIdle = idle
        activeIdle.pause()
        activeIdle.volume = 1f
        activeIdle.clearMediaItems()
        canonical.volume = 1f
    }

    fun release() {
        fadeJob?.cancel()
        playerA.release()
        playerB.release()
    }
}
