package com.fiangg20.wavelength.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Shared channel levels. The settings screen writes them, the audio thread reads them,
 * so every field is volatile and no locking is needed.
 */
object ChannelBalance {
    @Volatile var leftGain: Float = 1f
    @Volatile var rightGain: Float = 1f
    @Volatile var mono: Boolean = false

    /** Loudness correction for the song that is playing (ReplayGain), 1 means untouched. */
    @Volatile var trackGain: Float = 1f

    /**
     * @param balance -1 (all left) .. 1 (all right), 0 is the centre.
     * @param left level of the left channel, 0..1
     * @param right level of the right channel, 0..1
     */
    fun set(balance: Float, left: Float, right: Float, mono: Boolean) {
        val b = balance.coerceIn(-1f, 1f)
        // The louder side stays at full level, the other side fades out.
        leftGain = left.coerceIn(0f, 1f) * (if (b > 0f) 1f - b else 1f)
        rightGain = right.coerceIn(0f, 1f) * (if (b < 0f) 1f + b else 1f)
        this.mono = mono
    }
}

/** Applies [ChannelBalance] to stereo PCM. Anything that is not stereo 16 bit or float is left untouched. */
@UnstableApi
class BalanceProcessor : BaseAudioProcessor() {

    @Throws(UnhandledAudioFormatException::class)
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val supported = inputAudioFormat.channelCount == 2 &&
            (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)
        if (!supported) throw UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val gl = ChannelBalance.leftGain * ChannelBalance.trackGain
        val gr = ChannelBalance.rightGain * ChannelBalance.trackGain
        val mono = ChannelBalance.mono

        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 8) {
                var l = inputBuffer.getFloat()
                var r = inputBuffer.getFloat()
                if (mono) {
                    val m = (l + r) * 0.5f
                    l = m
                    r = m
                }
                out.putFloat(l * gl)
                out.putFloat(r * gr)
            }
        } else {
            while (inputBuffer.remaining() >= 4) {
                var l = inputBuffer.getShort().toInt()
                var r = inputBuffer.getShort().toInt()
                if (mono) {
                    val m = (l + r) / 2
                    l = m
                    r = m
                }
                out.putShort((l * gl).toInt().coerceIn(-32768, 32767).toShort())
                out.putShort((r * gr).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        // Keep any half frame for the next call instead of dropping it.
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }
}
