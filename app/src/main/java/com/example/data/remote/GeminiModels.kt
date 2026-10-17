package com.example.data.remote

/**
 * Modern Gemini Model identifiers (Active 2026).
 * Aligned with Gemini API system skill guidance.
 */
object GeminiModels {
    const val AGENT = "gemini-2.0-flash"
    const val FAST = "gemini-2.0-flash-lite"
    const val ROUTER = "gemini-2.0-flash-lite"
    const val VERIFIER = "gemini-2.0-flash-lite"

    val CHAT_FALLBACKS = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-pro"
    )

    val TRANSCRIPTION_FALLBACKS = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    )

    const val LIVE = "gemini-2.0-flash"
    const val LIVE_LEGACY = "gemini-2.0-flash"
    const val TTS = "gemini-2.0-flash"
    const val TTS_LITE = "gemini-2.0-flash"
    const val TRANSCRIBE = "gemini-2.0-flash"
    const val CHAT = "gemini-2.0-flash"
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
