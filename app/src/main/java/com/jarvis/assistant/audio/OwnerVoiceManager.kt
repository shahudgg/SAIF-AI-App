package com.jarvis.assistant.audio

import android.util.Log
import com.jarvis.assistant.data.preferences.AppPreferences
import kotlin.math.sqrt

data class VoiceMatchResult(
    val isMatch: Boolean,
    val distance: Float,
    val similarityPercent: Int,
    val threshold: Float,
    val hasExtractedVector: Boolean = true
)

object OwnerVoiceManager {
    private const val TAG = "OwnerVoiceManager"

    const val DEFAULT_THRESHOLD = 0.70f
    const val STRICT_THRESHOLD = 0.62f
    const val RELAXED_THRESHOLD = 0.78f

    fun calculateCosineDistance(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 1.0f

        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in v1.indices) {
            val a = v1[i].toDouble()
            val b = v2[i].toDouble()
            dot += a * b
            norm1 += a * a
            norm2 += b * b
        }

        if (norm1 <= 0.0 || norm2 <= 0.0) return 1.0f

        val cosSim = (dot / (sqrt(norm1) * sqrt(norm2))).coerceIn(-1.0, 1.0)
        return (1.0 - cosSim).toFloat().coerceIn(0.0f, 2.0f)
    }

    fun distanceToSimilarityPercent(distance: Float): Int {
        val sim = (1.0f - (distance / 1.35f)) * 100f
        return sim.toInt().coerceIn(0, 100)
    }

    /**
     * Extracts a 64-dimensional acoustic frequency & energy fingerprint vector directly
     * from 16kHz 16-bit PCM mono audio buffers.
     */
    @Synchronized
    fun extractSpeakerVector(pcmAudio: ByteArray): FloatArray? {
        if (pcmAudio.size < 1600) { // Require at least 50ms of audio
            Log.w(TAG, "Audio buffer too short for speaker extraction (${pcmAudio.size} bytes)")
            return null
        }

        val numSamples = pcmAudio.size / 2
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val low = pcmAudio[i * 2].toInt() and 0xFF
            val high = pcmAudio[i * 2 + 1].toInt()
            samples[i] = ((high shl 8) or low).toShort()
        }

        // Subdivide audio into 64 energy and frequency bands
        val vector = FloatArray(64)
        val bandSize = maxOf(1, numSamples / 64)

        for (b in 0 until 64) {
            var sumSquare = 0.0
            var zeroCrossings = 0
            val start = b * bandSize
            val end = minOf(start + bandSize, numSamples)

            for (s in start until end) {
                val v = samples[s].toDouble()
                sumSquare += v * v
                if (s > start && ((samples[s] >= 0 && samples[s - 1] < 0) || (samples[s] < 0 && samples[s - 1] >= 0))) {
                    zeroCrossings++
                }
            }

            val rms = sqrt(sumSquare / maxOf(1, end - start))
            vector[b] = (rms + zeroCrossings * 20.0).toFloat()
        }

        // Normalize vector to unit length
        var norm = 0.0
        for (v in vector) norm += v * v
        val len = sqrt(norm)
        if (len > 0.0001) {
            for (i in vector.indices) vector[i] = (vector[i] / len).toFloat()
        }

        return vector
    }

    fun verifySpeakerVector(currentVector: FloatArray, prefs: AppPreferences): VoiceMatchResult {
        val masterProfile = prefs.getOwnerVoiceProfile()
        val sampleProfiles = prefs.getOwnerVoiceSamples()
        val threshold = prefs.ownerVoiceThreshold

        if ((masterProfile == null || masterProfile.isEmpty()) && sampleProfiles.isEmpty()) {
            return VoiceMatchResult(
                isMatch = true,
                distance = 0.0f,
                similarityPercent = 100,
                threshold = threshold
            )
        }

        var minDistance = 2.0f

        if (masterProfile != null && masterProfile.isNotEmpty()) {
            val d = calculateCosineDistance(currentVector, masterProfile)
            if (d < minDistance) minDistance = d
        }

        for (sample in sampleProfiles) {
            val d = calculateCosineDistance(currentVector, sample)
            if (d < minDistance) minDistance = d
        }

        val isMatch = minDistance <= threshold
        val similarityPercent = distanceToSimilarityPercent(minDistance)

        return VoiceMatchResult(
            isMatch = isMatch,
            distance = minDistance,
            similarityPercent = similarityPercent,
            threshold = threshold
        )
    }

    fun verifySpeakerAudio(pcmAudio: ByteArray, prefs: AppPreferences): VoiceMatchResult {
        if (!prefs.isOwnerVoiceEnabled || !prefs.hasOwnerVoiceProfile()) {
            return VoiceMatchResult(
                isMatch = true,
                distance = 0.0f,
                similarityPercent = 100,
                threshold = prefs.ownerVoiceThreshold
            )
        }

        val vector = extractSpeakerVector(pcmAudio)
        if (vector == null) {
            return VoiceMatchResult(
                isMatch = false,
                distance = 1.0f,
                similarityPercent = 0,
                threshold = prefs.ownerVoiceThreshold,
                hasExtractedVector = false
            )
        }

        return verifySpeakerVector(vector, prefs)
    }

    fun computeCentroid(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(0)
        val dim = vectors.first().size
        val centroid = FloatArray(dim)

        for (vec in vectors) {
            if (vec.size == dim) {
                for (i in 0 until dim) centroid[i] += vec[i]
            }
        }

        val count = vectors.size.toFloat()
        var norm = 0.0
        for (i in 0 until dim) {
            centroid[i] /= count
            norm += centroid[i] * centroid[i]
        }

        val len = sqrt(norm)
        if (len > 0.0001) {
            for (i in 0 until dim) centroid[i] = (centroid[i] / len).toFloat()
        }

        return centroid
    }

    fun averageVectors(vectors: List<FloatArray>): FloatArray? {
        val centroid = computeCentroid(vectors)
        return if (centroid.isNotEmpty()) centroid else null
    }
}
