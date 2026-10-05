package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatRepository
import com.example.data.local.ProviderSettingsManager
import com.example.data.local.SaifDatabase
import com.example.data.remote.AIApiUtility
import com.example.data.remote.GeminiService
import com.example.util.AudioTranscriptionEngine
import com.example.util.AutonomousScreenAgent
import com.example.util.DeviceAction
import com.example.util.DeviceActionManager
import com.example.util.DeviceActionParser
import com.example.util.SaifAccessibilityService
import com.example.util.SmartConversationMemory
import com.example.util.VoiceActivityDetector
import com.example.utils.TTSManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

enum class VoiceSessionState {
    IDLE, LISTENING, PROCESSING, SPEAKING
}

class LiveVoiceSessionManager(
    private val context: Context,
    private val onClose: () -> Unit
) {
    companion object {
        private const val TAG = "LiveVoiceSessionManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TTS_COOLDOWN_MS = 450L
    }

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private val _sessionState = MutableStateFlow(VoiceSessionState.IDLE)
    val sessionState: StateFlow<VoiceSessionState> = _sessionState

    private val _liveSubtitle = MutableStateFlow("")
    val liveSubtitle: StateFlow<String> = _liveSubtitle

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var isTtsSpeaking = false

    @Volatile
    private var lastTtsFinishTime = 0L

    private val isProcessingTurn = AtomicBoolean(false)

    private val minBufferSize = maxOf(
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
        2048
    )

    private val vad = VoiceActivityDetector(sampleRate = SAMPLE_RATE)

    private var animationJob: Job? = null
    private var processingJob: Job? = null

    // Streaming TTS Queue
    private val speechQueue = ConcurrentLinkedQueue<String>()
    private val isPlayingQueueItem = AtomicBoolean(false)
    private val isStreamGenerationComplete = AtomicBoolean(false)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager

    init {
        TTSManager.init(context)
        SmartConversationMemory.init(context)
        com.example.agent.PhoneAgentRuntime.init(context)
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.")
            onClose()
            return
        }

        try {
            audioManager?.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting audioManager mode: ${e.message}")
        }

        // Prefer VOICE_COMMUNICATION for hardware DSP echo cancellation and beamforming
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 2
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            Log.w(TAG, "VOICE_COMMUNICATION AudioRecord init failed, falling back to MIC: ${e.message}")
            null
        } ?: try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize * 2
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed: ${e.message}")
            null
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord could not be initialized")
            onClose()
            return
        }

        val sessionId = audioRecord?.audioSessionId ?: 0
        if (sessionId != 0) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                        Log.d(TAG, "AcousticEchoCanceler enabled on session $sessionId")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "AEC enable error: ${e.message}")
            }

            try {
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                        Log.d(TAG, "NoiseSuppressor enabled on session $sessionId")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "NoiseSuppressor enable error: ${e.message}")
            }

            try {
                if (AutomaticGainControl.isAvailable()) {
                    gainControl = AutomaticGainControl.create(sessionId)?.apply {
                        enabled = true
                        Log.d(TAG, "AutomaticGainControl enabled on session $sessionId")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "AGC enable error: ${e.message}")
            }
        }

        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed: ${e.message}")
            onClose()
            return
        }

        isRecording = true
        vad.reset()
        _sessionState.value = VoiceSessionState.LISTENING
        _liveSubtitle.value = "LISTENING"

        scope.launch {
            recordLoop()
        }
    }

    private suspend fun recordLoop() {
        val buffer = ShortArray(minBufferSize / 2)
        val byteBuffer = ByteArray(buffer.size * 2)
        val currentPcmBuffer = ByteArrayOutputStream()
        var hasSpokenInTurn = false

        while (isRecording) {
            val readShorts = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (readShorts > 0) {
                // Convert shorts to 16-bit PCM byte array (little-endian)
                for (i in 0 until readShorts) {
                    val s = buffer[i].toInt()
                    byteBuffer[i * 2] = (s and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }

                val frameBytes = byteBuffer.copyOf(readShorts * 2)
                val isCurrentlySpeaking = isTtsSpeaking || _sessionState.value == VoiceSessionState.SPEAKING
                val vadResult = vad.processFrame(frameBytes, isCurrentlySpeaking)

                // 1. Check for Smart Interruption during SPEAKING
                if (isCurrentlySpeaking) {
                    // Require confirmed vocal speech interruption from VAD (never trigger on ambient clatter/noise)
                    val isBargeIn = vadResult.isInterruption

                    if (isBargeIn) {
                        Log.d(TAG, "Smart user interruption detected during TTS! Stopping speech immediately.")
                        stopSpeaking(causedByUserInterruption = true)
                        currentPcmBuffer.reset()
                        hasSpokenInTurn = true
                        _sessionState.value = VoiceSessionState.LISTENING
                        _liveSubtitle.value = "Listening..."
                        _amplitude.value = vadResult.normalizedAmplitude

                        currentPcmBuffer.write(frameBytes)
                        vad.notifySpeechStarted()
                    }
                    delay(8)
                    continue
                }

                // 2. Cooldown period right after TTS finishes naturally to avoid room echo
                val now = System.currentTimeMillis()
                if (now - lastTtsFinishTime < TTS_COOLDOWN_MS) {
                    _amplitude.value = 0f
                    delay(8)
                    continue
                }

                val currentState = _sessionState.value

                // 3. User is in PROCESSING (THINKING / EXECUTING):
                // Do not cancel or drop the running task on ambient noise or clatter!
                if (currentState == VoiceSessionState.PROCESSING) {
                    delay(12)
                    continue
                }

                // 4. Normal LISTENING mode
                if (currentState == VoiceSessionState.LISTENING) {
                    _amplitude.value = vadResult.normalizedAmplitude

                    if (vadResult.isSpeechActive) {
                        if (!hasSpokenInTurn) {
                            hasSpokenInTurn = true
                            _liveSubtitle.value = "Listening..."
                        }
                        currentPcmBuffer.write(frameBytes)
                    } else if (hasSpokenInTurn) {
                        currentPcmBuffer.write(frameBytes)

                        // 5. Smart End-of-Speech Detection
                        if (vadResult.isEndOfSpeech) {
                            val pcmData = currentPcmBuffer.toByteArray()
                            currentPcmBuffer.reset()
                            hasSpokenInTurn = false
                            vad.reset()

                            val minVoiceBytes = (SAMPLE_RATE * 2 * 0.45).toInt() // At least 450ms of sustained speech
                            if (pcmData.size >= minVoiceBytes) {
                                Log.d(TAG, "Natural end of speech detected (${pcmData.size} bytes). Switching to THINKING.")
                                _sessionState.value = VoiceSessionState.PROCESSING
                                _liveSubtitle.value = "THINKING"
                                triggerTurnProcessing(pcmData)
                            } else {
                                Log.d(TAG, "Audio too short (${pcmData.size} bytes), ignoring ambient noise click and resuming listening.")
                                _sessionState.value = VoiceSessionState.LISTENING
                                _liveSubtitle.value = "LISTENING"
                            }
                        }
                    }
                }
            }
            delay(10)
        }
    }

    private fun triggerTurnProcessing(pcmData: ByteArray) {
        if (!isProcessingTurn.compareAndSet(false, true)) {
            Log.w(TAG, "Processing already in progress, dropping duplicate turn.")
            return
        }

        processingJob?.cancel()
        processingJob = scope.launch {
            try {
                processAudioTurn(pcmData)
            } finally {
                isProcessingTurn.set(false)
            }
        }
    }

    private suspend fun processAudioTurn(pcmData: ByteArray) {
        if (pcmData.isEmpty()) {
            _sessionState.value = VoiceSessionState.LISTENING
            _liveSubtitle.value = "LISTENING"
            return
        }

        try {
            val wavData = wrapPcmToWav(pcmData, SAMPLE_RATE, 1, 16)
            val base64Audio = android.util.Base64.encodeToString(wavData, android.util.Base64.NO_WRAP)
            val apiKey = AudioTranscriptionEngine.resolveActiveApiKey()

            val transcriptionResult = transcribeWav(apiKey, base64Audio, wavData)
            if (!transcriptionResult.isSuccess) {
                val err = transcriptionResult.exceptionOrNull()?.message ?: "Transcription failed"
                Log.w(TAG, "Transcription failed: $err")
                _liveSubtitle.value = "Listening..."
                _sessionState.value = VoiceSessionState.LISTENING
                return
            }

            val text = transcriptionResult.getOrNull()?.trim() ?: ""
            if (text.isBlank()) {
                Log.d(TAG, "Transcribed text is empty. Returning to LISTENING.")
                _sessionState.value = VoiceSessionState.LISTENING
                _liveSubtitle.value = "LISTENING"
                return
            }

            Log.i(TAG, "User Speech Transcribed: \"$text\"")

            // Quick Voice Cancellation Commands
            val lower = text.lowercase()
            if (lower in listOf("stop", "ruko", "chup", "chup ho jao", "cancel", "shant ho jao", "quiet", "bas")) {
                stopSpeaking()
                _sessionState.value = VoiceSessionState.LISTENING
                _liveSubtitle.value = "Listening..."
                return
            }

            // Extract facts from user utterance into Smart Conversation Memory
            SmartConversationMemory.extractAndSaveFacts(text)

            // Save user message to Room DB for the active session
            val db = SaifDatabase.getDatabase(context)
            val repo = ChatRepository(db.chatDao())
            val activeSessionId = LiveVoiceManager.currentSessionId

            if (activeSessionId != null) {
                repo.insertMessage(
                    ChatMessageEntity(
                        sessionId = activeSessionId,
                        role = "user",
                        content = text,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // Retrieve recent conversation history for multi-turn context
            val recentEntities = if (activeSessionId != null) {
                repo.getRecentMessages(activeSessionId, 14)
            } else {
                emptyList()
            }

            val conversationHistory = recentEntities.map { entity ->
                Pair(if (entity.role == "user") "user" else "model", entity.content)
            }

            val isAgentRunning = com.example.agent.PhoneAgentRuntime.status.value.state == com.example.agent.AgentStatusState.RUNNING
            val routeDecision = com.example.agent.IntentRouter.route(text, isAgentRunning)

            if (routeDecision.route == com.example.agent.RouteDestination.CONTROL) {
                val reply = com.example.agent.PhoneAgentRuntime.control(routeDecision.controlCmd ?: text)
                enqueueSentenceForSpeech(reply)
                playNextSentenceIfAvailable()
                _sessionState.value = VoiceSessionState.LISTENING
                _liveSubtitle.value = "LISTENING"
                return
            }

            if (routeDecision.route == com.example.agent.RouteDestination.TASK) {
                // Immediate acknowledgement <= 6 words
                val immediateAck = when {
                    text.contains("youtube", ignoreCase = true) -> "Ji, YouTube par kar raha hu."
                    text.contains("whatsapp", ignoreCase = true) -> "Ji, WhatsApp par bhej raha hu."
                    text.contains("call", ignoreCase = true) -> "Ji, call mila raha hu."
                    text.contains("instagram", ignoreCase = true) -> "Ji, Instagram par dekh raha hu."
                    else -> "Ji, phone par execute kar raha hu."
                }
                enqueueSentenceForSpeech(immediateAck)
                playNextSentenceIfAvailable()

                // Submit to PhoneAgentRuntime with decoupled scope so mic stays LIVE!
                com.example.agent.PhoneAgentRuntime.submit(
                    goal = routeDecision.goal,
                    listener = object : com.example.agent.AgentListener {
                        override fun onMilestone(text: String) {
                            enqueueSentenceForSpeech(text)
                            playNextSentenceIfAvailable()
                        }

                        override fun onAskUser(question: String, options: List<String>?) {
                            enqueueSentenceForSpeech(question)
                            playNextSentenceIfAvailable()
                        }

                        override fun onFinished(outcome: com.example.agent.AgentOutcome) {
                            enqueueSentenceForSpeech(outcome.summary)
                            playNextSentenceIfAvailable()

                            if (activeSessionId != null && outcome.summary.isNotBlank()) {
                                scope.launch {
                                    repo.insertMessage(
                                        ChatMessageEntity(
                                            sessionId = activeSessionId,
                                            role = "model",
                                            content = outcome.summary,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                    }
                )

                // Return immediately to listening so mic is NEVER deaf!
                _sessionState.value = VoiceSessionState.LISTENING
                _liveSubtitle.value = "LISTENING"
                return
            }

            _sessionState.value = VoiceSessionState.PROCESSING
            _liveSubtitle.value = "THINKING"

            // Construct System Prompt with Automation Actions + Smart Memory + Spoken Polish
            val memoryBlock = SmartConversationMemory.buildMemoryPromptBlock()
            val service = SaifAccessibilityService.instance
            val activeApp = service?.getCurrentAppLabel(context) ?: "Home Screen"
            val currentPkg = service?.getCurrentPackage() ?: ""

            val fullSystemPrompt = """
You are SAIF AI, an advanced, ultra-capable real-time Android voice assistant.
$memoryBlock

CURRENT ACTIVE SCREEN: You are currently on the screen of '$activeApp' (Package: $currentPkg).

AUTONOMOUS DEVICE AUTOMATION ACTIONS:
You have native access to control the Android phone, perform human-like touches, navigate apps, and execute multi-step macros.
When the user asks to perform device actions or macros, output an action block at the beginning of your response:
<<<ACTIONS
[
  {"type": "YOUTUBE_PLAY_SUBSCRIBE", "query": "trending song", "subscribe": true},
  {"type": "CALL", "target": "papa"},
  {"type": "WHATSAPP", "recipient": "Dad", "message": "Hii"},
  {"type": "ACCESSIBILITY_NAV", "action": "home"},
  {"type": "ACCESSIBILITY_SCROLL", "direction": "down"}
]
ACTIONS>>>

SPOKEN RESPONSE FORMAT:
- If actions are executed, confirm what was done briefly and confidently like Jarvis ("Yes sir, ...", "Theek hai sir, ...").
- Provide a friendly, concise 1-2 sentence spoken reply in Hindi/English/Hinglish (matching the user's spoken language).
- Speak naturally like a real human.
- STRICT RULE: NEVER output programming code, Kotlin, Java, markdown blocks, JSON, asterisks, or bullet points in your spoken reply.
""".trimIndent()

            // Prepare Streaming Speech Queue
            clearStreamingQueue()
            isStreamGenerationComplete.set(false)

            val fullModelResponseBuilder = StringBuilder()
            val streamingTextBuffer = StringBuilder()
            var isInsideActionBlock = false

            val apiResult = AIApiUtility.executeWithFallback(
                prompt = text,
                mode = "general",
                history = conversationHistory,
                customSystemPrompt = fullSystemPrompt,
                base64Images = emptyList(),
                onChunk = { chunk ->
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) return@executeWithFallback
                    fullModelResponseBuilder.append(chunk)
                    streamingTextBuffer.append(chunk)

                    // Process stream and feed complete sentences to TTS
                    var bufferContent = streamingTextBuffer.toString()

                    // Handle <<<ACTIONS...ACTIONS>>> removal from the spoken buffer
                    if (!isInsideActionBlock && bufferContent.contains("<<<ACTIONS")) {
                        isInsideActionBlock = true
                    }
                    if (isInsideActionBlock) {
                        if (bufferContent.contains("ACTIONS>>>")) {
                            bufferContent = bufferContent.substringAfter("ACTIONS>>>").trimStart()
                            streamingTextBuffer.clear()
                            streamingTextBuffer.append(bufferContent)
                            isInsideActionBlock = false
                        } else {
                            // Still waiting for end of actions block
                            return@executeWithFallback
                        }
                    }

                    // Look for complete sentence boundaries in natural speech
                    val sentenceRegex = Regex("""(?<=[.?!।\n])\s+""")
                    val parts = bufferContent.split(sentenceRegex)

                    if (parts.size > 1) {
                        for (i in 0 until parts.size - 1) {
                            val sentence = parts[i].trim()
                            if (sentence.isNotBlank() && !sentence.contains("<<<ACTIONS")) {
                                enqueueSentenceForSpeech(sentence)
                            }
                        }
                        // Keep remaining uncompleted part in the buffer
                        streamingTextBuffer.clear()
                        streamingTextBuffer.append(parts.last())
                    } else if (bufferContent.length > 55 && bufferContent.contains(",")) {
                        // For long continuous streams without periods, split at comma
                        val commaIdx = bufferContent.lastIndexOf(',')
                        if (commaIdx > 20) {
                            val sentence = bufferContent.substring(0, commaIdx).trim()
                            if (sentence.isNotBlank() && !sentence.contains("<<<ACTIONS")) {
                                enqueueSentenceForSpeech(sentence)
                            }
                            val remainder = bufferContent.substring(commaIdx + 1).trimStart()
                            streamingTextBuffer.clear()
                            streamingTextBuffer.append(remainder)
                        }
                    }
                }
            )

            isStreamGenerationComplete.set(true)

            // Any remaining spoken text in buffer
            var remainingText = streamingTextBuffer.toString().trim()
            if (remainingText.contains("ACTIONS>>>")) {
                remainingText = remainingText.substringAfter("ACTIONS>>>").trim()
            }
            if (remainingText.isNotBlank() && !remainingText.contains("<<<ACTIONS")) {
                enqueueSentenceForSpeech(remainingText)
            }

            val rawReply = apiResult.getOrNull()
                ?: fullModelResponseBuilder.toString().ifBlank {
                    GeminiService.generateLocalSmartResponse(text, "general")
                }

            // Parse any dynamic AI actions emitted in response
            var actionAnnouncement: String? = null
            val parsed = DeviceActionParser.parse(rawReply, text)
            if (parsed.actions.isNotEmpty()) {
                val actionResults = DeviceActionManager.executeActionsSequentially(context, parsed.actions)
                if (parsed.actions.any { it is DeviceAction.ReadNotifications }) {
                    val notifResult = actionResults.firstOrNull {
                        it.contains("notification", ignoreCase = true) || it.contains("SMS", ignoreCase = true) || it.contains("messages", ignoreCase = true)
                    } ?: DeviceActionManager.readNotificationsSummary(context, null)
                    if (!notifResult.isNullOrBlank()) {
                        enqueueSentenceForSpeech(notifResult)
                    }
                } else if (parsed.actions.any { it is DeviceAction.InspectScreen }) {
                    val inspectResult = actionResults.firstOrNull { it.contains("Active screen", ignoreCase = true) }
                    if (!inspectResult.isNullOrBlank()) {
                        enqueueSentenceForSpeech(inspectResult)
                    }
                } else {
                    val macroResult = actionResults.firstOrNull { it.isNotBlank() && !it.startsWith("Error:") }
                    if (!macroResult.isNullOrBlank()) {
                        actionAnnouncement = macroResult
                    }
                }
            }

            val parsedFallback = DeviceActionParser.parse(rawReply, text)
            val cleanCompleteReply = if (!actionAnnouncement.isNullOrBlank() && (parsedFallback.cleanSpokenText.isBlank() || parsedFallback.cleanSpokenText == rawReply)) {
                actionAnnouncement
            } else {
                parsedFallback.cleanSpokenText.ifBlank {
                    if (parsedFallback.actions.isNotEmpty()) "Theek hai sir, sabhi tasks execute kar diye hain." else rawReply
                }
            }

            // Save AI reply to Room DB
            if (activeSessionId != null && cleanCompleteReply.isNotBlank()) {
                repo.insertMessage(
                    ChatMessageEntity(
                        sessionId = activeSessionId,
                        role = "model",
                        content = cleanCompleteReply,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // If nothing was enqueued for speech, speak cleanCompleteReply
            if (speechQueue.isEmpty() && !isPlayingQueueItem.get()) {
                enqueueSentenceForSpeech(cleanCompleteReply)
            }

            // Trigger player if not yet playing
            playNextSentenceIfAvailable()

        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.d(TAG, "Turn processing cancelled by user barge-in.")
                return
            }
            Log.e(TAG, "Error in processAudioTurn: ${e.message}", e)
            _liveSubtitle.value = "Listening..."
            _sessionState.value = VoiceSessionState.LISTENING
        }
    }

    private fun enqueueSentenceForSpeech(rawSentence: String) {
        val clean = TTSManager.cleanTextForSpeech(rawSentence)
        if (clean.isNotBlank()) {
            speechQueue.add(clean)
            playNextSentenceIfAvailable()
        }
    }

    private fun playNextSentenceIfAvailable() {
        if (!isRecording) return

        if (isPlayingQueueItem.compareAndSet(false, true)) {
            val nextSentence = speechQueue.poll()
            if (nextSentence != null) {
                _sessionState.value = VoiceSessionState.SPEAKING
                isTtsSpeaking = true
                _liveSubtitle.value = nextSentence

                startVoiceWaveAnimation()

                TTSManager.speak(
                    text = nextSentence,
                    context = context,
                    onStart = {
                        _sessionState.value = VoiceSessionState.SPEAKING
                        isTtsSpeaking = true
                        _liveSubtitle.value = nextSentence
                    },
                    onDone = {
                        isPlayingQueueItem.set(false)
                        // If more sentences exist, play immediately
                        if (speechQueue.isNotEmpty()) {
                            playNextSentenceIfAvailable()
                        } else if (isStreamGenerationComplete.get()) {
                            // Turn finished completely! Return to LISTENING seamlessly.
                            onSpeechPlaybackComplete()
                        }
                    }
                )
            } else {
                isPlayingQueueItem.set(false)
                if (isStreamGenerationComplete.get()) {
                    onSpeechPlaybackComplete()
                }
            }
        }
    }

    private fun onSpeechPlaybackComplete() {
        isTtsSpeaking = false
        lastTtsFinishTime = System.currentTimeMillis()
        animationJob?.cancel()
        _amplitude.value = 0f

        if (_sessionState.value == VoiceSessionState.SPEAKING) {
            _sessionState.value = VoiceSessionState.LISTENING
            _liveSubtitle.value = "LISTENING"
            vad.reset()
        }
    }

    private fun startVoiceWaveAnimation() {
        animationJob?.cancel()
        animationJob = scope.launch {
            while (_sessionState.value == VoiceSessionState.SPEAKING) {
                _amplitude.value = kotlin.random.Random.nextFloat() * 0.55f + 0.2f
                delay(90)
            }
        }
    }

    private fun clearStreamingQueue() {
        speechQueue.clear()
        isPlayingQueueItem.set(false)
    }

    suspend fun transcribeWav(apiKey: String, base64Audio: String, rawWavBytes: ByteArray): Result<String> {
        return AudioTranscriptionEngine.transcribeWav(apiKey, base64Audio, rawWavBytes)
    }

    fun stopSpeaking(causedByUserInterruption: Boolean = false) {
        isTtsSpeaking = false
        isStreamGenerationComplete.set(true)
        if (causedByUserInterruption) {
            lastTtsFinishTime = 0L // No cooldown, immediately allow user speech input!
        } else {
            lastTtsFinishTime = System.currentTimeMillis()
        }
        clearStreamingQueue()
        TTSManager.stop()
        animationJob?.cancel()
        // When speech is interrupted by voice barge-in, silence the TTS, but do NOT abort background device execution
        if (!causedByUserInterruption) {
            processingJob?.cancel()
            isProcessingTurn.set(false)
        }
        vad.reset()

        _sessionState.value = VoiceSessionState.LISTENING
        _amplitude.value = 0f
        _liveSubtitle.value = "Listening..."
    }

    fun stop() {
        isRecording = false
        isTtsSpeaking = false
        clearStreamingQueue()

        processingJob?.cancel()
        animationJob?.cancel()

        try {
            audioManager?.mode = android.media.AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.w(TAG, "Audio manager reset error: ${e.message}")
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord release error: ${e.message}")
        }
        audioRecord = null

        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
            gainControl?.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioFx release error: ${e.message}")
        }
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null

        TTSManager.stop()
        _sessionState.value = VoiceSessionState.IDLE
        _amplitude.value = 0f

        scope.cancel()
    }

    private fun wrapPcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        return AudioTranscriptionEngine.wrapPcmToWav(pcmData, sampleRate, channels, bitDepth)
    }
}
