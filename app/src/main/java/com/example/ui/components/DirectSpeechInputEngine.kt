package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.util.AudioTranscriptionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class DirectSpeechInputEngine(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var recordingJob: Job? = null
    private var interimTranscribeJob: Job? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    var isListening: Boolean = false
        private set

    companion object {
        private const val TAG = "DirectSpeechEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Silence timeouts:
        // 1. If user taps mic and says nothing: allow 5.0 seconds before auto-stopping
        private const val INITIAL_SILENCE_TIMEOUT_MS = 5000L
        // 2. If user was speaking and then stops: auto-stop after 1.6s
        private const val POST_SPEECH_SILENCE_TIMEOUT_MS = 1600L
        // Safety cap: max 35 seconds per single recording
        private const val MAX_SESSION_TIMEOUT_MS = 35000L
    }

    /**
     * Starts listening directly from the device microphone via native AudioRecord.
     * ZERO Google SpeechRecognizer / Google Voice UI dialogs are used.
     * @param onRmsChanged Called with real-time RMS (0f to 10f) to drive wave and glow animations.
     * @param onInterimResult Called with live transcribed text as user speaks.
     * @param onFinalResult Called with the final transcription when user finishes speaking.
     * @param onStateChanged Called when listening starts or stops.
     */
    @SuppressLint("MissingPermission")
    fun startListening(
        onRmsChanged: (Float) -> Unit,
        onInterimResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onStateChanged: (Boolean) -> Unit
    ) {
        if (isListening) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "AudioRecord minBufferSize invalid: $minBufferSize")
            return
        }

        // Initialize pure direct hardware mic: standard MediaRecorder.AudioSource.MIC
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 2
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            null
        } ?: try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 2
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            null
        }

        if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return
        }

        isListening = true
        onStateChanged(true)

        recordingJob = scope.launch(Dispatchers.IO) {
            val pcmBuffer = ByteArrayOutputStream()
            val buffer = ShortArray(1024)
            var hasSpoken = false
            var consecutiveSilenceMs = 0L
            var speechFrames = 0
            val sessionStartTime = System.currentTimeMillis()
            var lastInterimTime = System.currentTimeMillis()
            var ambientNoiseFloor = 120.0
            var smoothedRms = 100.0
            var frameCount = 0
            var readErrorCount = 0

            try {
                audioRecord?.startRecording()
                Log.d(TAG, "Hardware mic recording started successfully (MIC direct)")

                while (isActive && isListening) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize <= 0) {
                        readErrorCount++
                        if (readErrorCount > 10) {
                            Log.w(TAG, "Multiple consecutive audio read errors ($readSize). Stopping...")
                            break
                        }
                        delay(20)
                        continue
                    }
                    readErrorCount = 0
                    frameCount++

                    // Calculate hardware RMS for this frame
                    var sum = 0.0
                    for (i in 0 until readSize) {
                        val sample = buffer[i].toDouble()
                        sum += sample * sample
                    }
                    val frameRms = kotlin.math.sqrt(sum / readSize)

                    // Exponential smoothing to ignore instant transient spikes
                    smoothedRms = if (frameCount <= 2) frameRms else (smoothedRms * 0.7 + frameRms * 0.3)

                    // Ambient noise floor tracking
                    if (frameCount <= 6) {
                        ambientNoiseFloor = minOf(ambientNoiseFloor, frameRms).coerceIn(40.0, 300.0)
                    } else if (frameRms < ambientNoiseFloor * 1.3) {
                        ambientNoiseFloor = (ambientNoiseFloor * 0.96 + frameRms * 0.04).coerceIn(40.0, 350.0)
                    }

                    // Adaptive speech threshold: voice is at least 1.45x ambient noise or >= 160
                    val speechThreshold = maxOf(160.0, ambientNoiseFloor * 1.45 + 30.0)
                    val isCurrentFrameSpeech = smoothedRms > speechThreshold

                    // Scaled level (0.0 to 10.0) for UI animation
                    val level = ((smoothedRms - ambientNoiseFloor) / 1800.0).coerceIn(0.0, 1.0).toFloat()
                    val scaledRms = level * 10f

                    // Non-blocking UI update for glow & responsive wave
                    scope.launch(Dispatchers.Main) {
                        if (isListening) {
                            onRmsChanged(scaledRms)
                        }
                    }

                    val now = System.currentTimeMillis()
                    val frameDurationMs = (readSize * 1000L) / SAMPLE_RATE

                    if (isCurrentFrameSpeech) {
                        speechFrames++
                        // Confirm speech if sustained or loud voice
                        if (speechFrames >= 1 || smoothedRms > speechThreshold * 1.5) {
                            hasSpoken = true
                        }
                        consecutiveSilenceMs = 0L

                        // Append raw PCM data
                        for (i in 0 until readSize) {
                            pcmBuffer.write(buffer[i].toInt() and 0xFF)
                            pcmBuffer.write((buffer[i].toInt() shr 8) and 0xFF)
                        }

                        // Interim live transcription while speaking (approx every 2.4s)
                        if (now - lastInterimTime > 2400L && pcmBuffer.size() > 32000) {
                            lastInterimTime = now
                            val snapshot = pcmBuffer.toByteArray()
                            interimTranscribeJob?.cancel()
                            interimTranscribeJob = scope.launch(Dispatchers.IO) {
                                val apiKey = AudioTranscriptionEngine.resolveActiveApiKey()
                                if (apiKey.isNotBlank()) {
                                    val wavData = AudioTranscriptionEngine.wrapPcmToWav(snapshot, SAMPLE_RATE, 1, 16)
                                    val base64 = Base64.encodeToString(wavData, Base64.NO_WRAP)
                                    val res = AudioTranscriptionEngine.transcribeWav(apiKey, base64, wavData)
                                    if (res.isSuccess) {
                                        val interimText = res.getOrNull()?.trim() ?: ""
                                        if (interimText.isNotBlank()) {
                                            withContext(Dispatchers.Main) {
                                                onInterimResult(interimText)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        speechFrames = 0
                        consecutiveSilenceMs += frameDurationMs

                        if (hasSpoken) {
                            // User was speaking: continue capturing trailing audio buffer so the word ending is not cut
                            for (i in 0 until readSize) {
                                pcmBuffer.write(buffer[i].toInt() and 0xFF)
                                pcmBuffer.write((buffer[i].toInt() shr 8) and 0xFF)
                            }

                            // Post-speech silence endpoint: auto-stop after 1.5 seconds
                            if (consecutiveSilenceMs >= POST_SPEECH_SILENCE_TIMEOUT_MS) {
                                Log.d(TAG, "Silence timeout detected after speech (${consecutiveSilenceMs}ms). Auto-stopping mic...")
                                break
                            }
                        } else {
                            // User tapped mic but remained quiet: auto-stop after 2.0 seconds
                            if (consecutiveSilenceMs >= INITIAL_SILENCE_TIMEOUT_MS) {
                                Log.d(TAG, "Initial silence timeout (${consecutiveSilenceMs}ms) without speech. Auto-stopping mic...")
                                break
                            }
                        }
                    }

                    // Safety timeout cap
                    if (now - sessionStartTime > MAX_SESSION_TIMEOUT_MS) {
                        Log.d(TAG, "Max session timeout reached ($MAX_SESSION_TIMEOUT_MS ms). Auto-stopping mic...")
                        break
                    }

                    delay(8)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording exception: ${e.message}")
            } finally {
                stopInternal()

                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        onRmsChanged(0f)
                        onStateChanged(false)
                    }

                    // Transcribe complete utterance via Gemini / Whisper / OpenRouter if speech was recorded
                    if ((hasSpoken || pcmBuffer.size() > 4000) && pcmBuffer.size() > 3200) {
                        interimTranscribeJob?.cancel()
                        val fullAudio = pcmBuffer.toByteArray()
                        val apiKey = AudioTranscriptionEngine.resolveActiveApiKey()
                        val wavData = AudioTranscriptionEngine.wrapPcmToWav(fullAudio, SAMPLE_RATE, 1, 16)
                        val base64 = Base64.encodeToString(wavData, Base64.NO_WRAP)
                        val res = AudioTranscriptionEngine.transcribeWav(apiKey, base64, wavData)
                        if (res.isSuccess) {
                            val finalText = res.getOrNull()?.trim() ?: ""
                            if (finalText.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    onFinalResult(finalText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        recordingJob?.cancel()
        stopInternal()
    }

    private fun stopInternal() {
        isListening = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {}
        try {
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
    }

    fun release() {
        stopListening()
        interimTranscribeJob?.cancel()
    }
}
