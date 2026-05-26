package com.example

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class MonoAudioProcessor : BaseAudioProcessor() {
    var monoEnabled: Boolean = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // We accept the input format as-is and preserve it
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position

        if (remaining <= 0) return

        val outputBuffer = replaceOutputBuffer(remaining)

        if (!monoEnabled || inputAudioFormat.channelCount != 2 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // If mono is disabled, or channels are not stereo, or it is not 16-bit PCM,
            // copy the input buffer directly to the output buffer without modification.
            outputBuffer.put(inputBuffer)
            return
        }

        // Configure native byte order for buffer reads/writes
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        while (inputBuffer.hasRemaining()) {
            if (inputBuffer.remaining() < 4) {
                // Copy any remaining bytes if less than a full stereo frame (4 bytes)
                outputBuffer.put(inputBuffer)
                break
            }
            val left = inputBuffer.getShort()
            val right = inputBuffer.getShort()
            val average = ((left.toInt() + right.toInt()) / 2).toShort()
            outputBuffer.putShort(average) // Write averaged value to Left channel
            outputBuffer.putShort(average) // Write averaged value to Right channel
        }
    }
}
