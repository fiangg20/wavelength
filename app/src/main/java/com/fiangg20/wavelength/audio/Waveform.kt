package com.fiangg20.wavelength.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Loudness bars for the waveform progress bar. A song is decoded once in the background,
 * squeezed into [BARS] bars and kept in a small file, so the next play is instant.
 */
object Waveform {
    const val BARS = 96

    private fun file(context: Context, id: Long): File =
        File(File(context.applicationContext.filesDir, "waves").also { it.mkdirs() }, "$id.bin")

    fun cached(context: Context, id: Long): FloatArray? {
        val f = file(context, id)
        if (!f.exists() || f.length() != BARS.toLong()) return null
        return try {
            val bytes = f.readBytes()
            FloatArray(BARS) { (bytes[it].toInt() and 0xFF) / 255f }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun load(context: Context, id: Long, uri: Uri, durationMs: Long): FloatArray? =
        withContext(Dispatchers.IO) {
            cached(context, id)?.let { return@withContext it }
            val bars = try {
                decode(context, uri, durationMs)
            } catch (e: Exception) {
                null
            }
            if (bars != null) {
                try {
                    file(context, id).writeBytes(ByteArray(BARS) { (bars[it].coerceIn(0f, 1f) * 255f).toInt().toByte() })
                } catch (e: Exception) {
                    // The bars still work for this session.
                }
            }
            bars
        }

    private fun decode(context: Context, uri: Uri, knownDurationMs: Long): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = when {
                format.containsKey(MediaFormat.KEY_DURATION) -> format.getLong(MediaFormat.KEY_DURATION)
                knownDurationMs > 0L -> knownDurationMs * 1000L
                else -> return null
            }
            if (durationUs <= 0L) return null

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            val sums = DoubleArray(BARS)
            val counts = LongArray(BARS)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            if (channels < 1) channels = 1
            var idle = 0

            while (!outputDone && idle < 2000) {
                if (!inputDone) {
                    val inIndex = c.dequeueInputBuffer(5000)
                    if (inIndex >= 0) {
                        val buf = c.getInputBuffer(inIndex)
                        val size = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        if (size < 0) {
                            c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = c.dequeueOutputBuffer(info, 5000)
                if (outIndex >= 0) {
                    idle = 0
                    val out = c.getOutputBuffer(outIndex)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val shorts = out.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                        val frames = shorts.remaining() / channels
                        if (frames > 0) {
                            val bar = ((info.presentationTimeUs.toDouble() / durationUs) * BARS).toInt().coerceIn(0, BARS - 1)
                            // Sample a handful of frames from the buffer, that is plenty for a 96 bar picture.
                            val step = maxOf(1, frames / 48)
                            var f = 0
                            var acc = 0.0
                            var n = 0
                            while (f < frames) {
                                val v = shorts.get(f * channels).toInt()
                                acc += v.toDouble() * v.toDouble()
                                n++
                                f += step
                            }
                            if (n > 0) {
                                sums[bar] += acc / n
                                counts[bar]++
                            }
                        }
                    }
                    c.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    idle++
                }
            }

            val rms = FloatArray(BARS) { if (counts[it] > 0) sqrt(sums[it] / counts[it]).toFloat() else 0f }
            val peak = rms.maxOrNull() ?: 0f
            if (peak <= 0f) return null
            // A gentle curve keeps quiet parts visible instead of flat.
            return FloatArray(BARS) { sqrt((abs(rms[it]) / peak).coerceIn(0f, 1f)) }
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
                // Already stopped.
            }
            try {
                codec?.release()
            } catch (e: Exception) {
                // Already released.
            }
            extractor.release()
        }
    }
}
