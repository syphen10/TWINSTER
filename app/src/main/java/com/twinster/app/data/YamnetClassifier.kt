package com.twinster.app.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier

/**
 * Thin wrapper around the TFLite Task Library's [AudioClassifier], loaded from the bundled YAMNet
 * model (assets/yamnet.tflite — Google's AudioSet-trained, Apache-licensed, ~4MB model, sourced from
 * TF Hub's official redistributable "yamnet/classification/tflite" release). The Task Library handles
 * tensor framing and label lookup (the model carries its own embedded 521-class label file) — this
 * class just owns the classifier's lifecycle and turns raw 16kHz mono float PCM (see
 * [AudioContentDecoder]) into an averaged label->score map for [AudioGenreTaxonomy] to interpret.
 *
 * One instance is created per Local Library scan and reused across all sampled tracks (model load is
 * the expensive part — reloading it per track would dominate scan time) — see LocalLibraryScanner.
 */
class YamnetClassifier private constructor(private val classifier: AudioClassifier) : AutoCloseable {

    /**
     * Splits [pcm] into consecutive, non-overlapping frames sized to whatever the model requires
     * (YAMNet: ~0.975s each) and classifies each frame, then averages every AudioSet class's score
     * across frames. Averaging over the whole sampled clip — rather than trusting a single frame —
     * smooths over quiet intros/outros or a one-off vocal ad-lib inside the clip.
     */
    fun classifyAveraged(pcm: FloatArray): Map<String, Float> {
        val frameSize = classifier.requiredInputBufferSize.toInt()
        if (frameSize <= 0 || pcm.size < frameSize) return emptyMap()

        val totals = HashMap<String, Float>()
        var frameCount = 0
        var offset = 0
        // One TensorAudio (and its underlying native buffer) is allocated once per track and reused
        // across every frame via load(), rather than the previous per-frame createInputTensorAudio()
        // call — a ~5s clip was allocating a fresh native buffer roughly 5 times per track for no
        // reason, since load() already fully overwrites the buffer's contents each time. Less native
        // allocation churn directly reduces memory pressure, which is a plausible contributor to the
        // isolated process's occasional native crash (never confirmed via a real device, but a
        // legitimate, evidence-based thing to fix regardless of whether it's the exact root cause).
        val tensorAudio = classifier.createInputTensorAudio()
        while (offset + frameSize <= pcm.size) {
            // Each frame is classified independently and a single bad frame (a native TFLite
            // failure, a tensor size mismatch, an OutOfMemoryError from a stray huge allocation)
            // must not abort every other frame already/still to be classified for this track — the
            // caller (LocalLibraryScanner) already treats a whole-track failure as "skip this
            // track's content analysis", but degrading per-frame first means one bad frame doesn't
            // even cost the rest of an otherwise-fine track's signal.
            try {
                val frame = pcm.copyOfRange(offset, offset + frameSize)
                tensorAudio.load(frame)
                val classifications = classifier.classify(tensorAudio)
                classifications.firstOrNull()?.categories?.forEach { category ->
                    totals[category.label] = (totals[category.label] ?: 0f) + category.score
                }
                frameCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Intentionally broad: native TFLite/Task Library calls can throw Error subtypes
                // (e.g. OutOfMemoryError) as well as ordinary exceptions, and neither may crash the
                // scan — this frame just contributes no signal.
            }
            offset += frameSize
        }
        if (frameCount == 0) return emptyMap()
        return totals.mapValues { it.value / frameCount }
    }

    override fun close() {
        try {
            classifier.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val MODEL_ASSET = "yamnet.tflite"

        /** Null if the model asset can't be loaded (corrupt install, OOM, unsupported device) — callers
         *  treat that as "content-based genre detection unavailable for this scan" and fall back to
         *  tag-only genre data, same as any other per-track decode failure. Catches Throwable, not just
         *  Exception, since a native TFLite load failure can surface as an Error (e.g. OutOfMemoryError,
         *  UnsatisfiedLinkError) rather than a checked/unchecked Exception. */
        fun createOrNull(context: Context): YamnetClassifier? = try {
            YamnetClassifier(AudioClassifier.createFromFile(context, MODEL_ASSET))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }
}
