package com.example.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.ui.components.VOICE_PROFILES
import com.example.ui.components.VoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

object TTSManager : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var queuedText: String? = null
    private var queuedContext: Context? = null

    private val _currentlySpeakingText = MutableStateFlow<String?>(null)
    val currentlySpeakingText: StateFlow<String?> = _currentlySpeakingText

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    fun isSpeakingText(text: String): Boolean {
        return _currentlySpeakingText.value == text
    }

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    private var onStartCallback: (() -> Unit)? = null
    private var onDoneCallback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    onStartCallback?.invoke()
                }
                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpeakingText.value = null
                    onDoneCallback?.invoke()
                }
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpeakingText.value = null
                    onDoneCallback?.invoke()
                }
            })
            queuedText?.let { text ->
                queuedContext?.let { ctx ->
                    speak(text, ctx, onStartCallback, onDoneCallback)
                }
                queuedText = null
                queuedContext = null
            }
        } else {
            Log.e("TTSManager", "Initialization failed")
        }
    }

    fun applyProfile(profile: VoiceProfile) {
        tts?.stop()
        
        // Ensure language is set before setting voice as some devices reset voice when language changes
        tts?.language = Locale.Builder().setLanguage("en").setRegion("IN").build()

        val voices = tts?.voices
        if (voices != null) {
            var selectedVoice: android.speech.tts.Voice? = null
            // Check targets in order of preference
            for (target in profile.targetEngineNameMatches) {
                val match = voices.find { it.name.equals(target, ignoreCase = true) }
                if (match != null) {
                    selectedVoice = match
                    break
                }
            }
            // Fallback: try partial matching if exact matches fail
            if (selectedVoice == null) {
                val fallbackTarget = profile.targetEngineNameMatches.firstOrNull() ?: ""
                selectedVoice = voices.find { it.name.contains(fallbackTarget, ignoreCase = true) }
            }
            
            if (selectedVoice != null) {
                tts?.voice = selectedVoice
                Log.d("TTSManager", "Applied voice: ${selectedVoice.name}")
            } else {
                Log.d("TTSManager", "No exact voice matched for profile: ${profile.name}. Using default with adjusted pitch.")
            }
        }
        
        tts?.setPitch(profile.pitch)
        tts?.setSpeechRate(profile.speechRate)
    }

    fun previewVoice(context: Context, text: String, voiceId: String) {
        if (!isReady) {
            init(context)
            return
        }
        val profile = VOICE_PROFILES.find { it.id == voiceId } ?: VOICE_PROFILES.first()
        applyProfile(profile)
        val params = android.os.Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "preview_id") }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "preview_id")
    }

    fun speak(text: String, context: Context, onStart: (() -> Unit)? = null, onDone: (() -> Unit)? = null) {
        if (onStart != null) onStartCallback = onStart
        if (onDone != null) onDoneCallback = onDone

        val cleanText = cleanTextForSpeech(text)
        if (cleanText.isBlank()) {
            _currentlySpeakingText.value = null
            val cb = onDoneCallback
            onDoneCallback = null
            cb?.invoke()
            return
        }

        _currentlySpeakingText.value = text

        if (!isReady) {
            queuedText = cleanText
            queuedContext = context
            if (tts == null) {
                init(context)
            }
            return
        }

        val prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE)
        val selectedVoiceId = prefs.getString("selected_voice_id", "Zephyr") ?: "Zephyr"

        val profile = VOICE_PROFILES.find { it.id == selectedVoiceId } ?: VOICE_PROFILES.first()
        applyProfile(profile)

        val params = android.os.Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "LIVE_SPEECH_ID") }
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "LIVE_SPEECH_ID")
    }

    /**
     * Sanitizes text specifically for natural voice speech output:
     * - Strips all emojis and pictographs so the TTS engine never reads emoji names (e.g. "smiling face with smiling eyes").
     * - Strips markdown image tags and raw URLs so image generation links aren't read out as code.
     * - Strips code blocks and programming code syntax so TTS doesn't vocalize brackets and symbols.
     * - Cleans up markdown formatting (bold, italics, headers, bullets).
     */
    fun cleanTextForSpeech(rawText: String): String {
        if (rawText.isBlank()) return ""

        val hasImageMarkdown = rawText.contains("![") ||
                rawText.contains("image.pollinations.ai", ignoreCase = true) ||
                rawText.contains("<img", ignoreCase = true)

        var text = rawText

        // Guard: If raw text is a source code file (starts with package, import, class, etc.), do not read code
        val trimmed = text.trimStart()
        if (trimmed.startsWith("package ") || trimmed.startsWith("import ") ||
            trimmed.startsWith("class ") || trimmed.startsWith("fun ") ||
            trimmed.startsWith("public class ") || trimmed.startsWith("<!DOCTYPE") ||
            trimmed.startsWith("<html")) {
            return "Aapka code generate kar diya hai sir."
        }

        // 1. Remove markdown images: ![alt](url)
        text = text.replace(Regex("""!\[.*?\]\([^\)]*\)"""), " ")

        // 2. Remove HTML image tags: <img ...>
        text = text.replace(Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE), " ")

        // 3. Handle code blocks: ```lang ... ```
        text = text.replace(Regex("""```[\s\S]*?```""")) { matchResult ->
            val block = matchResult.value
            // If code block contains an image link or pollinations url, completely strip it
            if (block.contains("image.pollinations.ai", ignoreCase = true) || block.contains("![")) {
                ""
            } else {
                // Programming code block: omit from voice so TTS doesn't read brackets and symbols
                " "
            }
        }

        // 4. Remove inline code backticks: `code` -> code
        text = text.replace(Regex("""`([^`]+)`"""), "$1")

        // 5. Convert markdown links to just their label: [text](url) -> text
        text = text.replace(Regex("""\[([^\]]+)\]\([^\)]*\)"""), "$1")

        // 6. Remove raw URLs: http://..., https://..., www....
        text = text.replace(Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE), " ")

        // 7. Strip markdown headers, lists, quotes, rules
        text = text.replace(Regex("""(?m)^#{1,6}\s+"""), "")
        text = text.replace(Regex("""(?m)^[\s]*[-*+]\s+"""), "")
        text = text.replace(Regex("""(?m)^[\s]*\d+\.\s+"""), "")
        text = text.replace(Regex("""(?m)^>\s*"""), "")
        text = text.replace(Regex("""(?m)^[-*_]{3,}\s*$"""), "")

        // 8. Strip bold, italic, strikethrough markers
        text = text.replace(Regex("""\*{1,3}([^*]+)\*{1,3}"""), "$1")
        text = text.replace(Regex("""_{1,3}([^_]+)_{1,3}"""), "$1")
        text = text.replace(Regex("""~~([^~]+)~~"""), "$1")

        // 9. Strip leftover markdown/code symbols: * # ` ~ _ > | { } [ ] \ < >
        text = text.replace(Regex("""[*#`~_>|{}\[\]\\<>]"""), " ")

        // 10. Strip ALL emojis and pictographs so TTS never pronounces emoji names
        text = removeEmojis(text)

        // 11. Normalize spaces and fix spacing before punctuation marks
        text = text.replace(Regex("""\s+"""), " ")
        text = text.replace(Regex("""\s+([,.:;!?])"""), "$1").trim()

        // 12. If text became empty because it was only an image link, provide friendly confirmation
        if (text.isBlank() || text.matches(Regex("""^[\s.,!?:;-]*$"""))) {
            return if (hasImageMarkdown) {
                "Maine aapke liye image generate kar di hai."
            } else {
                ""
            }
        }

        return text
    }

    private fun removeEmojis(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        val len = input.length
        while (i < len) {
            val codePoint = input.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            if (!isEmojiCodePoint(codePoint)) {
                sb.appendCodePoint(codePoint)
            }
            i += charCount
        }
        return sb.toString()
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean {
        return when {
            // Emoticons (1F600 - 1F64F)
            codePoint in 0x1F600..0x1F64F -> true
            // Miscellaneous Symbols and Pictographs (1F300 - 1F5FF)
            codePoint in 0x1F300..0x1F5FF -> true
            // Transport and Map Symbols (1F680 - 1F6FF)
            codePoint in 0x1F680..0x1F6FF -> true
            // Supplemental Symbols and Pictographs (1F900 - 1F9FF)
            codePoint in 0x1F900..0x1F9FF -> true
            // Symbols and Pictographs Extended-A (1FA70 - 1FAFF)
            codePoint in 0x1FA70..0x1FAFF -> true
            // Other Plane 1 Emoji / Pictograph blocks (1F000 - 1FA6F)
            codePoint in 0x1F000..0x1FA6F -> true
            // Regional Indicator Symbols (Flags: 1F1E6 - 1F1FF)
            codePoint in 0x1F1E6..0x1F1FF -> true
            // Miscellaneous Symbols (2600 - 26FF, e.g. ☀️, ☁️, ☂️, ☕, ⚡, ⚽)
            codePoint in 0x2600..0x26FF -> true
            // Dingbats (2700 - 27BF, e.g. ✂️, ✈️, ✉️, ✌️, ✨, ❄️, ❤️)
            codePoint in 0x2700..0x27BF -> true
            // Miscellaneous Technical (2300 - 23FF, e.g. ⌚, ⌛, ⏰, ⏱️)
            codePoint in 0x2300..0x23FF -> true
            // Geometric Shapes & Arrows often used as emojis (2B00 - 2BFF, e.g. ⭐, ⭕)
            codePoint in 0x2B00..0x2BFF -> true
            // Arrows (2190 - 21FF) and Supplemental Arrows (2900 - 297F)
            codePoint in 0x2190..0x21FF -> true
            codePoint in 0x2900..0x297F -> true
            // Variation Selectors (FE00 - FE0F, e.g. \uFE0F)
            codePoint in 0xFE00..0xFE0F -> true
            // Zero Width Joiner (200D)
            codePoint == 0x200D -> true
            // Combining Enclosing Keycap (20E3)
            codePoint == 0x20E3 -> true
            // Enclosed Ideographic Supplement
            codePoint in 0x1F200..0x1F2FF -> true
            // Supplemental Punctuation / Waves
            codePoint in 0x3030..0x303D -> true
            else -> false
        }
    }

    fun stop() {
        _isSpeaking.value = false
        _currentlySpeakingText.value = null
        onStartCallback = null
        onDoneCallback = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w("TTSManager", "Error stopping TTS: ${e.message}")
        }
    }
    
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
