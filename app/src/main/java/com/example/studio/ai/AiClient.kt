package com.example.studio.ai

import com.example.llm.LlmGateway
import com.example.llm.LlmRequest
import com.example.llm.LlmRole

data class AiCapabilities(
    val hasText: Boolean = true,
    val hasJson: Boolean = true,
    val hasVision: Boolean = false,
    val hasAudioIn: Boolean = false,
    val hasVideoIn: Boolean = false,
    val hasTts: Boolean = false,
    val hasStt: Boolean = false
)

/**
 * Clean AI client skeleton for Video Studio that proxies requests to [LlmGateway]
 * using the user's active [AiProfile]. Never falls back to hardcoded models or developer keys.
 */
class AiClient(private val profile: AiProfile) {

    suspend fun call(
        role: LlmRole,
        prompt: String,
        systemPrompt: String? = null
    ): Result<String> {
        if (!profile.isConfigured) {
            return Result.failure(IllegalStateException("AI Provider is not configured in Settings. Please add your API key in Settings -> AI Provider."))
        }

        return try {
            val req = LlmRequest(
                prompt = prompt,
                systemPrompt = systemPrompt ?: "",
                mode = "chat"
            )
            val res = LlmGateway.call(
                role = role,
                provider = profile.provider,
                apiKey = profile.apiKey,
                model = profile.activeModel,
                request = req
            )
            if (res.isSuccess) {
                Result.success(res.getOrThrow().text)
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("Unknown LLM error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
