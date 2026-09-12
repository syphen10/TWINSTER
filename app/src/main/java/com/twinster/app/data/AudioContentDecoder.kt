package com.twinster.app.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/** Thrown for any decode failure — callers (see LocalLibraryScanner) treat this as "skip this
 *  track's content analysis" rather than a fatal scan error. */
class AudioDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Decodes a short clip of a local audio file to 16kHz mono float PCM in [-1, 1] — the exact input
 * format YAMNet (and the Task Library's AudioClassifier) requires. Uses MediaExtractor + MediaCodec
 * directly (both already part of the Android SDK, no extra dependency) rather than any full-file
 * decode, since only a few seconds are needed per track for content classification.
 */
object AudioContentDecoder {

    private const val TARGET_SAMPLE_RATE = 16_000

    // Hard wall-clock ceiling on the decode loop below, independent of the codec ever reporting
    // end-of-stream. A corrupt file, a DRM'd track, or an unusual codec edge case can decode input
    // without ever emitting a qualifying output buffer, which would otherwise spin the loop forever
    // and (pre-concurrency-fix) wedge every future request behind it. 8s is comfortably more than a
    // healthy device needs to decode a ~7s clip, so it essentially never fires for a normal file.
    private const val DECODE_LOOP_TIMEOUT_NANOS = 8_000L * 1_000_000L

    /**
     * Decodes up to [clipMillis] of audio starting from roughly [startFraction] of the way through
     * the track (a middle segment tends to be more representative of a song's actual character than
     * its intro — cold opens, count-ins, silence). Falls back to the very start if the track is too
     * short for that offset to leave a full clip.
     */
    fun decodeClipToMono16k(context: Context, uri: Uri, clipMillis: Long = 5_000L, startFraction: Float = 0.35f): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioDecodeException("No audio track found")

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw AudioDecodeException("No MIME type")
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val clipUs = clipMillis * 1000L

            val seekUs = if (durationUs > clipUs) {
                min((durationUs * startFraction).toLong(), durationUs - clipUs).coerceAtLeast(0L)
            } else 0L

            extractor.selectTrack(trackIndex)
            if (seekUs > 0L) extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val endUs = seekUs + clipUs
            // A growable ShortArray instead of ArrayList<Short> — boxing every PCM sample into a
            // Short object (7s @16kHz mono is ~112,000 samples, more for stereo/higher source rates)
            // adds meaningful per-object overhead and GC pressure per track; a plain primitive array
            // avoids that entirely.
            var pcmArray = ShortArray(TARGET_SAMPLE_RATE * 2 * 7)
            var pcmLength = 0
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            val loopStartNanos = System.nanoTime()

            while (!sawOutputEos) {
                if (System.nanoTime() - loopStartNanos > DECODE_LOOP_TIMEOUT_NANOS) {
                    throw AudioDecodeException("Decode loop exceeded time limit")
                }
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        val sampleTime = extractor.sampleTime
                        if (sampleSize < 0 || sampleTime > endUs) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        sourceSampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        sourceChannels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIndex >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuffer.asShortBuffer()
                            val newSamples = shortBuf.remaining()
                            if (pcmLength + newSamples > pcmArray.size) {
                                pcmArray = pcmArray.copyOf(max(pcmArray.size * 2, pcmLength + newSamples))
                            }
                            shortBuf.get(pcmArray, pcmLength, newSamples)
                            pcmLength += newSamples
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEos = true
                        if (bufferInfo.presentationTimeUs > endUs) sawOutputEos = true
                    }
                }
            }

            if (pcmLength == 0) throw AudioDecodeException("Decoded zero samples")

            val mono = downmixToMono(pcmArray, pcmLength, sourceChannels)
            return resampleLinear(mono, sourceSampleRate, TARGET_SAMPLE_RATE)
        } catch (e: AudioDecodeException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Deliberately broad: MediaCodec/MediaExtractor and the resampling math below can throw
            // Error subtypes (e.g. OutOfMemoryError on a pathological file) as well as ordinary
            // exceptions on a corrupt/unsupported file — both must degrade to "skip this track's
            // content analysis" (see LocalLibraryScanner), never crash the scan or the app.
            throw AudioDecodeException("Decode failed: ${e.message}", e)
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun downmixToMono(samples: ShortArray, length: Int, channelCount: Int): FloatArray {
        val channels = max(1, channelCount)
        if (channels == 1) {
            return FloatArray(length) { samples[it] / 32768f }
        }
        val frameCount = length / channels
        val mono = FloatArray(frameCount)
        for (frame in 0 until frameCount) {
            var sum = 0f
            for (c in 0 until channels) sum += samples[frame * channels + c] / 32768f
            mono[frame] = sum / channels
        }
        return mono
    }

    /** Simple linear-interpolation resampler — not a brick-wall bandlimited resample, but more than
     *  adequate for feeding a classification model that pools spectral content over ~1s windows
     *  anyway; a full polyphase resampler would be wasted precision here. */
    private fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLength = (input.size / ratio).toInt()
        val output = FloatArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val frac = (srcPos - srcIndex).toFloat()
            val a = input[srcIndex.coerceIn(0, input.size - 1)]
            val b = input[(srcIndex + 1).coerceIn(0, input.size - 1)]
            output[i] = a + (b - a) * frac
        }
        return output
    }
}
