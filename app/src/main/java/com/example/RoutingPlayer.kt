package com.example

import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Presents a single, stable [Player] identity to [androidx.media3.session.MediaSession] and the
 * app's MediaController/UI, backed by a swappable ExoPlayer delegate. [CrossfadePlayerController]
 * calls [switchDelegate] to hand off "which physical ExoPlayer is currently playing the queue"
 * between its two ExoPlayer instances during a crossfade, without MediaSession or MusicViewModel
 * ever needing to know two players exist.
 *
 * Built on [java.lang.reflect.Proxy] rather than extending [androidx.media3.common.ForwardingPlayer]:
 * that class's wrapped player field is private and fixed at construction, so a subclass would have
 * to override every one of Player's ~100 methods to guarantee none of them silently keep reading a
 * stale delegate after a swap. A dynamic proxy routes every call - known or not - through one
 * invocation path that always reads the current delegate.
 */
class RoutingPlayer(
    initial: ExoPlayer,
    onNavigationCommand: () -> Unit,
    onRelease: () -> Unit
) {
    private val invocationHandler = RoutingInvocationHandler(initial, onNavigationCommand, onRelease)

    val player: Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java)
    ) { proxy, method, args -> invocationHandler.invoke(proxy, method, args) } as Player

    fun switchDelegate(newDelegate: ExoPlayer) = invocationHandler.switchDelegate(newDelegate)

    private class RoutingInvocationHandler(
        initial: ExoPlayer,
        private val onNavigationCommand: () -> Unit,
        private val onRelease: () -> Unit
    ) : InvocationHandler {

        @Volatile
        private var current: ExoPlayer = initial

        private val externalListeners = CopyOnWriteArrayList<Player.Listener>()
        private var internalListener: Player.Listener? = null

        private val navigationMethods = setOf(
            "play", "pause", "stop", "seekTo", "seekToDefaultPosition", "seekBack", "seekForward",
            "seekToNext", "seekToNextMediaItem", "seekToNextWindow",
            "seekToPrevious", "seekToPreviousMediaItem", "seekToPreviousWindow",
            // Queue-mutation commands also need to cancel an in-flight crossfade: it captures the
            // upcoming-item index once at fade-start, so an edit mid-fade (reorder/remove/add)
            // would otherwise let the fade complete against a now-stale index.
            "addMediaItem", "addMediaItems", "removeMediaItem", "removeMediaItems",
            "moveMediaItem", "moveMediaItems", "clearMediaItems",
            "replaceMediaItem", "replaceMediaItems", "setMediaItem", "setMediaItems"
        )

        init {
            attachInternalListener(initial, synthesizeTransitionEvent = false)
        }

        fun switchDelegate(newDelegate: ExoPlayer) {
            internalListener?.let { current.removeListener(it) }
            current = newDelegate
            attachInternalListener(newDelegate, synthesizeTransitionEvent = true)
        }

        private fun attachInternalListener(delegate: ExoPlayer, synthesizeTransitionEvent: Boolean) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = forward { it.onIsPlayingChanged(isPlaying) }
                override fun onPlaybackStateChanged(state: Int) = forward { it.onPlaybackStateChanged(state) }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
                    forward { it.onMediaItemTransition(mediaItem, reason) }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) = forward { it.onPositionDiscontinuity(oldPosition, newPosition, reason) }
                override fun onRepeatModeChanged(mode: Int) = forward { it.onRepeatModeChanged(mode) }
                override fun onShuffleModeEnabledChanged(enabled: Boolean) =
                    forward { it.onShuffleModeEnabledChanged(enabled) }
                override fun onPlaybackParametersChanged(params: PlaybackParameters) =
                    forward { it.onPlaybackParametersChanged(params) }
                override fun onPlayerError(error: PlaybackException) = forward { it.onPlayerError(error) }
                override fun onTimelineChanged(timeline: Timeline, reason: Int) =
                    forward { it.onTimelineChanged(timeline, reason) }

                // Everything below is forwarded because MediaSession's own internal
                // Player.Listener subscribes to all of it - anything not relayed here leaves the
                // session (and therefore the notification, Bluetooth/AVRCP and Android Auto)
                // publishing stale state. onPlayWhenReadyChanged in particular is how a pause
                // caused by audio focus loss reaches the remote controls.
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
                    forward { it.onPlayWhenReadyChanged(playWhenReady, reason) }
                override fun onPlaybackSuppressionReasonChanged(reason: Int) =
                    forward { it.onPlaybackSuppressionReasonChanged(reason) }
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) =
                    forward { it.onMediaMetadataChanged(mediaMetadata) }
                override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) =
                    forward { it.onPlaylistMetadataChanged(mediaMetadata) }
                override fun onTracksChanged(tracks: Tracks) = forward { it.onTracksChanged(tracks) }
                override fun onIsLoadingChanged(isLoading: Boolean) =
                    forward { it.onIsLoadingChanged(isLoading) }
                override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
                    forward { it.onAvailableCommandsChanged(availableCommands) }
                override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) =
                    forward { it.onTrackSelectionParametersChanged(parameters) }
                override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) =
                    forward { it.onAudioAttributesChanged(audioAttributes) }
                override fun onVolumeChanged(volume: Float) = forward { it.onVolumeChanged(volume) }
                override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) =
                    forward { it.onDeviceInfoChanged(deviceInfo) }
                override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) =
                    forward { it.onDeviceVolumeChanged(volume, muted) }
                override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) =
                    forward { it.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs) }
                override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) =
                    forward { it.onSeekBackIncrementChanged(seekBackIncrementMs) }
                override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) =
                    forward { it.onSeekForwardIncrementChanged(seekForwardIncrementMs) }
                override fun onVideoSizeChanged(videoSize: VideoSize) =
                    forward { it.onVideoSizeChanged(videoSize) }
                override fun onRenderedFirstFrame() = forward { it.onRenderedFirstFrame() }
                override fun onCues(cueGroup: CueGroup) = forward { it.onCues(cueGroup) }
            }
            internalListener = listener
            delegate.addListener(listener)

            if (synthesizeTransitionEvent) {
                // The new delegate already transitioned to its current item before becoming
                // canonical (it was playing the upcoming track as the crossfade overlap player),
                // so the swap itself must manually notify listeners once here - otherwise the UI
                // never learns the song changed, since that transition happened before this
                // listener was attached.
                forward { it.onMediaItemTransition(delegate.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) }
            }
        }

        private fun forward(action: (Player.Listener) -> Unit) {
            externalListeners.forEach(action)
        }

        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            return when (method.name) {
                "addListener" -> {
                    externalListeners.add(args!![0] as Player.Listener)
                    null
                }
                "removeListener" -> {
                    externalListeners.remove(args!![0] as Player.Listener)
                    null
                }
                "release" -> {
                    onRelease()
                    null
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "RoutingPlayer(current=$current)"
                else -> {
                    if (method.name in navigationMethods) onNavigationCommand()
                    try {
                        method.invoke(current, *(args ?: emptyArray()))
                    } catch (e: InvocationTargetException) {
                        throw e.targetException
                    }
                }
            }
        }
    }
}
