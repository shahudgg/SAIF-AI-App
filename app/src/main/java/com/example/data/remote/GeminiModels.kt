package com.example.data.remote

/**
 * Modern Gemini Model identifiers (Active 2026).
 * Aligned with Gemini API system skill guidance.
 */
object GeminiModels {
    const val AGENT = "gemini-2.5-flash"
    const val FAST = "gemini-2.5-flash-lite"
    const val ROUTER = "gemini-2.5-flash-lite"
    const val VERIFIER = "gemini-2.5-flash-lite"

    val CHAT_FALLBACKS = listOf(
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-2.5-flash-lite",
        "gemini-3.1-flash-lite-preview",
        "gemini-3.5-flash",
        "gemini-3.1-pro-preview",
        "gemini-2.5-pro"
    )

    val TRANSCRIPTION_FALLBACKS = listOf(
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-2.5-flash-lite",
        "gemini-3.5-flash"
    )

    const val LIVE = "gemini-2.5-flash-native-audio-preview-12-2025"
    const val LIVE_LEGACY = "gemini-2.5-flash-native-audio-preview-12-2025"
    const val TTS = "gemini-2.5-flash-preview-tts"
    const val TTS_LITE = "gemini-2.5-flash-preview-tts"
    const val TRANSCRIBE = "gemini-2.5-flash"
    const val CHAT = "gemini-2.5-flash"
}

enum class ModelRole {
    AGENT,
    FAST,
    ROUTER,
    VERIFIER,
    CHAT,
    LIVE,
    TRANSCRIBE
}
