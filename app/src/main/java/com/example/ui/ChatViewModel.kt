package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatRepository
import com.example.data.local.ChatSessionEntity
import com.example.data.remote.GeminiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import android.net.Uri
import java.io.File
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    val allSessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedMessages: StateFlow<List<ChatMessageEntity>> = repository.bookmarkedMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _currentMode = MutableStateFlow("general")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _isFilterBookmarked = MutableStateFlow(false)
    val isFilterBookmarked: StateFlow<Boolean> = _isFilterBookmarked.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _selectedAttachments = MutableStateFlow<List<Uri>>(emptyList())
    val selectedAttachments: StateFlow<List<Uri>> = _selectedAttachments.asStateFlow()

    
    fun processAudioToText(context: android.content.Context, file: File) {
        viewModelScope.launch {
            try {
                val providerState = com.example.data.local.ProviderSettingsManager.loadState()
                val keys = providerState.apiKeys
                val activeKey = if (keys.isNotEmpty()) keys[providerState.currentKeyIndex % keys.size] else ""

                // Call multi-provider AudioTranscriptionEngine supporting Groq, OpenAI, Gemini, OpenRouter
                val result = com.example.util.AudioTranscriptionEngine.transcribeAudioFile(file, activeKey)
                if (result.isSuccess) {
                    val text = result.getOrNull()?.trim() ?: ""
                    val currentText = _input.value
                    if (currentText.isBlank()) {
                        _input.value = text
                    } else {
                        _input.value = currentText + " " + text
                    }
                } else {
                    _input.value = _input.value + " (Transcription failed: ${result.exceptionOrNull()?.message ?: "Check API key"})"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _input.value = _input.value + " (Transcription error: ${e.message})"
            } finally {
                withContext(Dispatchers.IO) {
                    try { if (file.exists()) file.delete() } catch(e: Exception) {}
                }
            }
        }
    }

    fun addAttachments(uris: List<Uri>) {
        _selectedAttachments.value = _selectedAttachments.value + uris
    }

    fun removeAttachment(uri: Uri) {
        _selectedAttachments.value = _selectedAttachments.value.filter { it != uri }
    }
    
    fun clearAttachments() {
        _selectedAttachments.value = emptyList()
    }

    private val _currentStreamingContent = MutableStateFlow("")
    val currentStreamingContent: StateFlow<String> = _currentStreamingContent.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _customSystemPrompt = MutableStateFlow("")
    val customSystemPrompt: StateFlow<String> = _customSystemPrompt.asStateFlow()

    private var activeGenerationJob: Job? = null

    // Observe messages for active session
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // We no longer auto-select the first session so the app starts fresh.
        viewModelScope.launch {
            repository.allSessions.collect { sessions ->
                // Keep observing but do nothing on initial load
            }
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun setMode(mode: String) {
        _currentMode.value = mode
        val curId = _activeSessionId.value
        if (curId != null) {
            viewModelScope.launch {
                val session = repository.getSessionById(curId)
                if (session != null) {
                    repository.updateSessionTitle(curId, session.title)
                }
            }
        }
    }

    fun toggleBookmarkFilter() {
        _isFilterBookmarked.value = !_isFilterBookmarked.value
    }

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            if (session != null) {
                _currentMode.value = session.mode
            }
        }
    }

    fun createNewSession(mode: String = _currentMode.value) {
        viewModelScope.launch {
            val newSession = repository.createSession(
                title = "New Chat",
                mode = mode
            )
            _activeSessionId.value = newSession.id
            _currentMode.value = mode
            _isFilterBookmarked.value = false
        }
    }

    fun createProjectSession(
        appName: String,
        packageName: String,
        minSdk: Int,
        targetSdk: Int,
        buildStudio: String,
        language: String,
        iconBitmap: android.graphics.Bitmap?,
        context: android.content.Context
    ) {
        viewModelScope.launch {
            // Save ProjectData for persistent lookup when reopened from history drawer
            val projData = com.example.ui.components.ProjectData(
                appName = appName,
                packageName = packageName,
                minSdk = minSdk,
                targetSdk = targetSdk,
                buildStudio = buildStudio,
                language = language,
                iconBitmap = iconBitmap
            )
            com.example.util.ProjectDataManager.saveProject(context, projData)

            val sessionTitle = appName.trim()
            val newSession = repository.createSession(
                title = sessionTitle,
                mode = "project"
            )
            _activeSessionId.value = newSession.id
            _currentMode.value = "coding"
            _isFilterBookmarked.value = false

            // Do not insert developer initialization message into chatDao so home screen chat stays clean!

            // Seed initial chat history for Build AI studio
            val welcomeBuildMsg = com.example.ui.components.BuildAiMessage(
                sender = "assistant",
                content = "Hello! I am SAIF AI, your coding specialist for **$appName**. Tell me what to build, modify, or fix, and I will write and auto-apply the changes directly to your files.",
                timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            )
            com.example.util.ProjectChatHistoryManager.saveMessages(context, appName, listOf(welcomeBuildMsg))
        }
    }

    private fun safeUriToBase64(context: android.content.Context, uri: android.net.Uri, maxDim: Int = 1024): String? {
        return try {
            val boundsOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var sampleSize = 1
            var w = origWidth
            var h = origHeight
            while (w > maxDim * 2 || h > maxDim * 2) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val scale = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height, 1f)
            val finalBmp = if (scale < 1f) {
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                if (scaled != bmp) bmp.recycle()
                scaled
            } else {
                bmp
            }

            val out = java.io.ByteArrayOutputStream()
            finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            finalBmp.recycle()
            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            android.util.Log.e("ChatViewModel", "Failed to convert image to Base64: ${t.message}", t)
            null
        }
    }

    fun sendMessage(context: android.content.Context, userPrompt: String = _input.value) {
        val trimmed = userPrompt.trim()
        val currentAttachments = _selectedAttachments.value.toList()
        
        if ((trimmed.isEmpty() && currentAttachments.isEmpty()) || _isGenerating.value) return

        _input.value = ""
        _selectedAttachments.value = emptyList() // clear

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isGenerating.value = true

            // Ensure active session exists
            var sessionId = _activeSessionId.value
            if (sessionId == null) {
                val sessionTitle = if (trimmed.length > 28) trimmed.take(28) + "..." else trimmed.ifBlank { "Photo Question" }
                val newSession = repository.createSession(
                    title = sessionTitle,
                    mode = _currentMode.value
                )
                sessionId = newSession.id
                _activeSessionId.value = sessionId
            } else {
                // If it was default "New Chat", update title to first prompt
                val session = repository.getSessionById(sessionId)
                if (session?.title == "New Chat") {
                    val sessionTitle = if (trimmed.length > 28) trimmed.take(28) + "..." else trimmed.ifBlank { "Photo Question" }
                    repository.updateSessionTitle(sessionId, sessionTitle)
                }
            }

            // Gather context history BEFORE inserting new message (prevents duplicate user turns)
            val existing = activeMessages.value.filter { it.content.isNotBlank() }.map { it.role to it.content }

            // Convert attachments to Base64 safely
            val base64Images = mutableListOf<String>()
            for (uri in currentAttachments) {
                val b64 = safeUriToBase64(context, uri)
                if (!b64.isNullOrBlank()) {
                    base64Images.add(b64)
                }
            }

            val effectivePrompt = if (trimmed.isNotBlank()) {
                trimmed
            } else if (base64Images.isNotEmpty()) {
                "Is image ko analyze karke batayein ki isme kya hai aur iski details kya hain."
            } else {
                "Hello"
            }

            // Save user message
            val attachmentUrisStr = if (currentAttachments.isNotEmpty()) currentAttachments.joinToString(",") { it.toString() } else null
            val userMsg = ChatMessageEntity(
                sessionId = sessionId,
                role = "user",
                content = trimmed.ifBlank { if (currentAttachments.isNotEmpty()) "🖼️ Image" else "" },
                attachmentUris = attachmentUrisStr
            )
            repository.insertMessage(userMsg)

            try {
            // Stream AI Response
            val accumulated = StringBuilder()
            val result = com.example.data.remote.AIApiUtility.executeWithFallback(
                prompt = effectivePrompt,
                mode = _currentMode.value,
                history = existing,
                customSystemPrompt = _customSystemPrompt.value,
                base64Images = base64Images
            ) { chunk ->
                accumulated.append(chunk)
                _currentStreamingContent.value = accumulated.toString()
            }

            val rawContent = if (accumulated.isNotEmpty()) {
                accumulated.toString()
            } else {
                val err = result.exceptionOrNull()
                val errMsg = err?.message ?: "AI provider did not return any content."
                "❌ **API Error:** $errMsg\n\n*Please verify your API key, model selection, or internet connection in Settings (⚙️).*"
            }

            val isAutonomous = com.example.util.AutonomousScreenAgent.isAutonomousDeviceTask(trimmed)
            val parsed = com.example.util.DeviceActionParser.parse(rawContent, trimmed)
            val finalContent = parsed.cleanSpokenText.ifBlank {
                if (isAutonomous || parsed.actions.isNotEmpty()) "Theek hai, sabhi tasks execute kar diye gaye hain." else rawContent
            }

            if (isAutonomous) {
                viewModelScope.launch {
                    val plan = com.example.util.AutonomousScreenAgent.planTask(context, trimmed)
                    if (plan.steps.isNotEmpty()) {
                        com.example.util.AutonomousScreenAgent.executeTaskPlan(context, plan)
                    }
                }
            } else if (parsed.actions.isNotEmpty()) {
                viewModelScope.launch {
                    com.example.util.DeviceActionManager.executeActionsSequentially(context, parsed.actions)
                }
            }

            // Save AI message to database
            val aiMsg = ChatMessageEntity(
                sessionId = sessionId,
                role = "model",
                content = finalContent
            )
            repository.insertMessage(aiMsg)

            // Learn and retain user facts into long-term memory
            try {
                com.example.util.SmartConversationMemory.extractAndSaveFacts(effectivePrompt, finalContent)
            } catch (ignored: Exception) {}
            
            // Trigger TTS if Auto Speak is enabled
            val prefs = context.getSharedPreferences("SettingsPrefs", android.content.Context.MODE_PRIVATE)
            val autoSpeak = prefs.getBoolean("autoSpeak", false)
            if (autoSpeak) {
                com.example.utils.TTSManager.speak(finalContent, context)
            }

            } finally {
                _currentStreamingContent.value = ""
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        val partial = _currentStreamingContent.value
        val sessionId = _activeSessionId.value
        if (partial.isNotBlank() && sessionId != null) {
            viewModelScope.launch {
                val partialMsg = ChatMessageEntity(
                    sessionId = sessionId,
                    role = "model",
                    content = "$partial [Stopped]"
                )
                repository.insertMessage(partialMsg)
            }
        }
        _currentStreamingContent.value = ""
        _isGenerating.value = false
    }

    fun regenerateLastResponse(context: android.content.Context) {
        if (_isGenerating.value) return
        val messages = activeMessages.value
        val lastUserMsg = messages.lastOrNull { it.role == "user" }
        if (lastUserMsg != null) {
            sendMessage(context, lastUserMsg.content)

        }
    }

    fun toggleBookmark(message: ChatMessageEntity) {
        viewModelScope.launch {
            repository.toggleBookmark(message.id, message.isBookmarked)
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun deleteSession(sessionId: String, context: android.content.Context? = null) {
        viewModelScope.launch {
            val target = allSessions.value.firstOrNull { it.id == sessionId }
            if (target != null && context != null && (target.mode == "project" || com.example.util.ProjectDataManager.hasProject(context, target.title))) {
                com.example.util.ProjectDataManager.deleteProject(context, target.title)
                com.example.util.ProjectChatHistoryManager.clearMessages(context, target.title)
            }
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                val remaining = allSessions.value.filter { it.id != sessionId }
                _activeSessionId.value = remaining.firstOrNull()?.id
            }
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.deleteAllSessions()
            _activeSessionId.value = null
        }
    }

    fun clearCurrentSessionMessages() {
        val curId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.clearSessionMessages(curId)
        }
    }

    fun updateSettings(temp: Float, prompt: String) {
        _temperature.value = temp
        _customSystemPrompt.value = prompt
    }

    class Factory(private val repository: ChatRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
