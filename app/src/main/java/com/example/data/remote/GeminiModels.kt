package com.example.data.remote

/**
 * Modern Gemini Model identifiers (Active 2026).
 * Obsolete and shut-down models (gemini-2.0-*, gemini-1.5-*, gemini-2.5-*) are strictly avoided.
 */
object GeminiModels {
    const val AGENT = "gemini-3.8-flash"
    const val FAST = "gemini-3.5-flash-lite"
    const val ROUTER = "gemini-3.5-flash-lite"
    const val VERIFIER = "gemini-3.5-flash-lite"

    val CHAT_FALLBACKS = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-flash-latest"
    )

    val TRANSCRIPTION_FALLBACKS = listOf(
        "gemini-3.5-transcribe",
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash",
        "gemini-flash-latest"
    )

    const val LIVE = "gemini-3.8-live"
    const val LIVE_LEGACY = "gemini-3.1-flash-live-preview"
    const val TTS = "gemini-3.8-flash-tts"
    const val TTS_LITE = "gemini-3.8-flash-lite-tts"
    const val TRANSCRIBE = "gemini-3.5-transcribe"
    const val CHAT = "gemini-3.8-flash"
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
