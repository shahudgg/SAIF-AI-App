package com.example.data.remote

import android.util.Log
import com.example.data.local.ProviderSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object AIApiUtility {

    fun sanitizeKey(key: String): String {
        return key.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
    }

    private fun buildMasterSystemPrompt(mode: String, customPrompt: String): String {
        val modeInstruction = when (mode) {
            "coding" -> "You are SAIF AI, an elite autonomous Senior Android Architect and Full-Stack Native Mobile Engineer integrated into a mobile IDE. You provide production-grade, highly optimized native Android (Java/Kotlin + XML) applications without placeholders, fake logic, or incomplete snippets that compile and run on Android API 24 to 34."
            "summary" -> "You are SAIF AI in 'Quick Summary' mode. You synthesize complex information into concise bullet points, executive takeaways, and high-impact conclusions."
            "creative" -> "You are SAIF AI in 'Creative Studio' mode. You are an imaginative writer, brainstorm partner, and storyteller craftsperson with vivid phrasing and engaging ideas."
            "research" -> "You are SAIF AI in 'Deep Research' mode. You provide thorough, structured analytical reports with pros/cons, architectural tradeoffs, and nuanced technical breakdowns."
            else -> "You are SAIF AI, an advanced native Android AI companion."
        }

        val coreDirective = """
CORE OPERATING DIRECTIVES:
1. PROFESSIONAL TONE & CONTEXTUAL EMOJIS: Maintain a polished and respectful tone. Format your text cleanly (bolding, lists, markdown styling) and use emojis dynamically based on the situation and user context, acting exactly like ChatGPT or Gemini.
2. ADAPTIVE LENGTH (STRICTLY PROPORTIONAL RESPONSES):
   - FOR SHORT QUESTIONS OR GREETINGS (e.g., "hi", "hello", "kya haal hai", simple calculations, quick queries): Provide a crisp, direct, concise answer in 1 to 2 short sentences. Do NOT write unnecessary essays, giant lists, or filler for simple 1-word or short questions.
   - FOR COMPLEX, DEEP, OR DETAILED QUESTIONS: Provide a comprehensive, well-structured, in-depth answer with clear headings, bullet points, or code blocks.
3. CREATOR ATTRIBUTION: Your creator is SAIF. You were programmed and developed by SAIF. If asked who made you or created you, politely and clearly state that you were created by SAIF.
4. NATURAL SPEECH IN HINDI / HINGLISH: When replying in Hindi or Hinglish, use natural, fluid conversational language without robotic stutter, strange syntax, or weird formatting symbols so that voice synthesizers can speak it smoothly and naturally.
5. PHONE APPLICATION LAUNCHING: You have native mobile application launcher capability. If the user asks to open WhatsApp, YouTube, Instagram, Telegram, Spotify, Google Maps, Phone Dialer, or Browser, politely confirm and mention the app name so the app launcher button appears directly in the chat to open their installed app.
6. IMAGE GENERATION: If the user asks you to generate, draw, or create an image, always write a brief, friendly natural introductory sentence first (e.g., 'Maine aapke liye image generate kar di hai:' or 'Here is the image you asked for:'), followed on a new line by the markdown image link: ![Generated Image](https://image.pollinations.ai/prompt/{detailed_prompt_URL_encoded}?nologo=true&enhance=true&seed=[RANDOM_NUMBER]). Replace [RANDOM_NUMBER] with a random 5-digit number to ensure uniqueness. NEVER wrap the image markdown in code blocks or triple backticks.
7. MATHEMATICS & FORMULA FORMATTING (STRICT NO-LATEX DIRECTIVE):
   - When solving any math problems, equations, fractions, algebra, geometry, calculus, or physics problems (whether from photos, images, or text):
   - NEVER output raw LaTeX code (DO NOT write \frac{...}{...}, \left(, \right), \times, \cdot, \sqrt{}, $$, $, \approx, \le, \ge, \pm, etc.).
   - Users find LaTeX syntax confusing and state that it looks like programming code!
   - ALWAYS write all math steps in natural, human-readable plain text using clear standard Unicode symbols:
     * Fractions: write cleanly as (3/2) or 3/2, e.g., (3/2) + 1 = (3 + 2)/2 = 5/2.
     * Multiplication: use × or *.
     * Division: use ÷ or /.
     * Square roots: use √(x) or sqrt(x).
     * Exponents/Powers: use x², x³, or x^2.
     * Plus/Minus: use ±.
     * Equations: write clearly line-by-line, e.g., L.H.S. = 7 + 2(5/2) - 3*(3/2).
   - Always present math solutions with clear, beautiful step-by-step numbering and bold final results so that any student or user can read it instantly on their phone.
8. DIRECT FINAL ANSWER ONLY (CRITICAL):
   - Output ONLY your direct answer to the user.
   - NEVER output internal reasoning, self-dialogue, planning steps, or meta-commentary (e.g. NEVER output 'User says...', 'According to directive...', 'Should I mention...', 'That's 1-2 sentences.', 'Provide professional tone.').
   - Output ONLY the final conversational reply.
""".trimIndent()

        val finalPrompt = StringBuilder()
        finalPrompt.append(modeInstruction).append("\n\n")
        finalPrompt.append(coreDirective).append("\n\n")
        
        // Inject Smart Long-Term Memory & User Profile Facts
        try {
            val memoryBlock = com.example.util.SmartConversationMemory.buildMemoryPromptBlock()
            if (memoryBlock.isNotBlank()) {
                finalPrompt.append(memoryBlock).append("\n\n")
            }
        } catch (ignored: Exception) {}

        if (customPrompt.isNotBlank()) {
            finalPrompt.append("User Custom Settings:\n").append(customPrompt)
        }
        return finalPrompt.toString()
    }

    private const val TAG = "AIApiUtility"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    
    private val fallbackModelsOpenRouter = listOf(
        "google/gemini-2.0-flash-001",
        "meta-llama/llama-3.3-70b-instruct",
        "qwen/qwen-2.5-coder-32b-instruct",
        "deepseek/deepseek-chat",
        "google/gemini-2.0-flash-exp:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "qwen/qwen-2.5-coder-32b-instruct:free",
        "meta-llama/llama-3.1-8b-instruct:free",
        "mistralai/mistral-7b-instruct:free"
    )

    private val visionModelsOpenRouter = listOf(
        "google/gemini-2.0-flash-001",
        "meta-llama/llama-3.2-11b-vision-instruct",
        "qwen/qwen-2.5-vl-72b-instruct:free",
        "meta-llama/llama-3.2-11b-vision-instruct:free",
        "google/gemini-2.0-flash-exp:free"
    )

    private val fallbackModelsGroq = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "mixtral-8x7b-32768"
    )

    private val visionModelsGroq = listOf(
        "llama-3.2-11b-vision-preview",
        "llama-3.2-90b-vision-preview"
    )

    private val fallbackModelsGemini = GeminiModels.CHAT_FALLBACKS

    private data class ProviderAttempt(
        val provider: String,
        val apiKey: String,
        val models: List<String>
    )

    suspend fun executeWithFallback(
        prompt: String,
        mode: String = "general",
        history: List<Pair<String, String>> = emptyList(),
        customSystemPrompt: String = "",
        base64Images: List<String> = emptyList(),
        onChunk: suspend (String) -> Unit = {}
    ): Result<String> {
        val state = ProviderSettingsManager.loadState()
        val isVisionRequest = base64Images.isNotEmpty()
        val effectiveSystemPrompt = if (customSystemPrompt.isNotBlank() && (mode == "coding" || mode == "build")) {
            customSystemPrompt
        } else {
            buildMasterSystemPrompt(mode, customSystemPrompt)
        }

        val geminiBuildConfigKey = try { com.example.BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }.trim()
        val openRouterBuildConfigKey = try { com.example.BuildConfig.OPENROUTER_API_KEY } catch (e: Exception) { "" }.trim()

        val attemptsList = mutableListOf<ProviderAttempt>()
        val isBuildMode = mode == "coding" || mode == "build"

        // Helper to resolve models for a provider with smart provider-model alignment
        fun resolveModels(prov: String, preferredModel: String = ""): List<String> {
            val list = mutableListOf<String>()
            val rawPref = preferredModel.trim()
            if (rawPref.isNotBlank()) {
                list.add(rawPref)
            }

            // In build mode, prioritize user's buildModel if specified
            if (isBuildMode && state.buildModel.isNotBlank() && rawPref.isBlank()) {
                val bModel = state.buildModel.trim()
                val isModelCompatible = when {
                    prov.contains("Gemini", true) -> bModel.contains("gemini", true)
                    prov.contains("Groq", true) -> bModel.contains("llama", true) || bModel.contains("mixtral", true)
                    prov.contains("OpenAI", true) -> bModel.contains("gpt", true) || bModel.contains("o1", true) || bModel.contains("o3", true)
                    prov.contains("Anthropic", true) -> bModel.contains("claude", true)
                    prov.contains("DeepSeek", true) -> bModel.contains("deepseek", true)
                    else -> true
                }
                if (isModelCompatible) {
                    list.add(bModel)
                }
            }

            // Only inject user's activeModel if it actually belongs to this provider
            val userModel = state.activeModel.trim()
            if (userModel.isNotBlank() && preferredModel.isBlank()) {
                val isModelCompatible = when {
                    prov.contains("Gemini", true) -> userModel.contains("gemini", true)
                    prov.contains("Groq", true) -> userModel.contains("llama", true) || userModel.contains("mixtral", true) || userModel.contains("gemma", true)
                    prov.contains("OpenAI", true) -> userModel.contains("gpt", true) || userModel.contains("o1", true) || userModel.contains("o3", true)
                    prov.contains("Anthropic", true) -> userModel.contains("claude", true)
                    prov.contains("DeepSeek", true) -> userModel.contains("deepseek", true)
                    prov.contains("InceptionLabs", true) -> userModel.contains("merlin", true) || userModel.contains("inception", true)
                    prov.contains("Atria", true) -> userModel.contains("atria", true) || userModel.contains("asi", true)
                    prov.contains("OpenRouter", true) -> true
                    prov.contains("OpenCode", true) -> userModel.contains("glm", true) || userModel.contains("kimi", true) || userModel.contains("minimax", true) || userModel.contains("mimo", true)
                    else -> true
                }
                if (isModelCompatible) {
                    list.add(userModel)
                }
            }

            val standardModels = when {
                prov.contains("OpenCode", ignoreCase = true) -> listOf("glm-5.1", "deepseek-v4-flash", "kimi-k2.6", "minimax-m2.7", "ling-3.0-flash-fin-free", "mimo-v2.5-free", "claude-sonnet-4-5", "nemotron-3-ultra-free")
                prov.equals("Gemini", ignoreCase = true) -> if (isBuildMode) {
                    listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash", "gemini-flash-latest")
                } else {
                    GeminiModels.CHAT_FALLBACKS
                }
                prov.equals("OpenRouter", ignoreCase = true) -> if (isVisionRequest) visionModelsOpenRouter else if (isBuildMode) {
                    // Never fall back to 7B/8B models for builds; use strong coding models
                    listOf("google/gemini-2.5-pro", "anthropic/claude-3.5-sonnet", "qwen/qwen-2.5-coder-32b-instruct", "meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-chat", "google/gemini-2.0-flash-001")
                } else {
                    listOf("google/gemini-2.0-flash-001", "meta-llama/llama-3.3-70b-instruct", "qwen/qwen-2.5-coder-32b-instruct", "deepseek/deepseek-chat", "meta-llama/llama-3.1-8b-instruct:free", "mistralai/mistral-7b-instruct:free")
                }
                prov.equals("Groq", ignoreCase = true) -> if (isVisionRequest) visionModelsGroq else if (isBuildMode) {
                    // Never fall back to 7B/8B models for builds
                    listOf("llama-3.3-70b-versatile", "mixtral-8x7b-32768")
                } else {
                    listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
                }
                prov.equals("OpenAI", ignoreCase = true) -> if (isVisionRequest) listOf("gpt-4o", "gpt-4o-mini") else if (isBuildMode) {
                    listOf("gpt-4o", "gpt-4-turbo", "o3-mini", "gpt-4o-mini")
                } else {
                    listOf("gpt-4o-mini", "gpt-4o", "gpt-4-turbo", "o3-mini")
                }
                prov.contains("InceptionLabs", ignoreCase = true) || prov.contains("inceptionlabs", ignoreCase = true) -> listOf("merlin-32k", "merlin", "merlin-code", "inception-3")
                prov.contains("Atria", ignoreCase = true) || prov.contains("atria-asi", ignoreCase = true) -> listOf("atria-1", "asi-chat", "atria-asi-v1", "atria-instruct")
                prov.equals("DeepSeek", ignoreCase = true) -> if (isBuildMode) listOf("deepseek-coder", "deepseek-chat", "deepseek-reasoner") else listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")
                prov.equals("Anthropic", ignoreCase = true) -> listOf("claude-3-5-sonnet-20241022", "claude-3-haiku-20240307")
                prov.equals("Mistral", ignoreCase = true) -> if (isBuildMode) listOf("codestral-latest", "mistral-large-latest", "mistral-small-latest") else listOf("mistral-large-latest", "mistral-small-latest", "codestral-latest")
                prov.equals("xAI", ignoreCase = true) -> listOf("grok-2-latest", "grok-beta")
                prov.equals("Together", ignoreCase = true) -> listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3")
                prov.equals("Perplexity", ignoreCase = true) -> listOf("sonar-pro", "sonar")
                prov.equals("Cerebras", ignoreCase = true) -> if (isBuildMode) listOf("llama-3.3-70b") else listOf("llama-3.3-70b", "llama3.1-8b")
                prov.equals("Fireworks", ignoreCase = true) -> listOf("accounts/fireworks/models/llama-v3p3-70b-instruct", "accounts/fireworks/models/deepseek-v3")
                prov.equals("Sambanova", ignoreCase = true) -> listOf("Qwen2.5-Coder-32B-Instruct", "Meta-Llama-3.3-70B-Instruct")
                prov.equals("SiliconFlow", ignoreCase = true) -> listOf("Qwen/Qwen2.5-72B-Instruct", "deepseek-ai/DeepSeek-V3")
                else -> listOf("gpt-4o", "llama-3.3-70b-versatile")
            }
            list.addAll(standardModels)
            return list.distinct()
        }

        // 1. Primary: User's configured keys with intelligent auto-detection
        val userKeys = state.apiKeys.map { sanitizeKey(it) }.filter { it.isNotBlank() && !it.contains("MY_") }
        val hasCustomUserKey = userKeys.isNotEmpty()

        val usageType = when {
            mode == "image" || prompt.contains("generate an image", ignoreCase = true) || prompt.contains("image generate", ignoreCase = true) -> com.example.data.local.ApiUsageType.IMAGE
            mode == "coding" || mode == "build" -> com.example.data.local.ApiUsageType.APP_BUILD
            else -> com.example.data.local.ApiUsageType.CHAT
        }

        // 0. When user has NO custom API key:
        // Use the Admin's default API key and provider from the Admin Panel, subject to the Admin's quota limit!
        if (!hasCustomUserKey) {
            val adminConfig = com.example.data.local.AdminConfigManager.globalConfig.value
            val adminDefaultKey = sanitizeKey(adminConfig.defaultApiKey)
            
            // Check quota limit first
            val limitCheck = com.example.data.local.AdminConfigManager.canUseApi(usageType, hasCustomUserKey = false)
            if (!limitCheck.isAllowed) {
                return Result.failure(IllegalStateException(limitCheck.message))
            }

            if (adminDefaultKey.isNotBlank()) {
                val adminProvider = adminConfig.defaultProvider.ifBlank { "Gemini" }
                val adminModel = adminConfig.defaultModel.ifBlank { "" }
                attemptsList.add(ProviderAttempt(adminProvider, adminDefaultKey, resolveModels(adminProvider, adminModel)))
            }
        }

        for (k in userKeys) {
            val trimmed = sanitizeKey(k)
            val detectedProvider = when {
                trimmed.startsWith("AIza") || trimmed.startsWith("AQ.") -> "Gemini"
                trimmed.startsWith("gsk_") -> "Groq"
                trimmed.startsWith("sk-or-") -> "OpenRouter"
                trimmed.startsWith("sk-ant-") -> "Anthropic"
                trimmed.startsWith("opencode", ignoreCase = true) || trimmed.startsWith("oc-") -> "OpenCode.ai"
                trimmed.startsWith("sk-proj-") || trimmed.startsWith("sk-admin-") -> "OpenAI"
                trimmed.startsWith("xai-") -> "xAI"
                trimmed.startsWith("mistral-") -> "Mistral"
                trimmed.startsWith("together-") -> "Together"
                trimmed.startsWith("pplx-") -> "Perplexity"
                trimmed.startsWith("csk-") -> "Cerebras"
                trimmed.startsWith("fw_") || trimmed.startsWith("fwi_") -> "Fireworks"
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> "Custom"
                state.providerName.contains("OpenCode", ignoreCase = true) -> "OpenCode.ai"
                trimmed.startsWith("sk-") -> if (state.providerName in listOf("OpenRouter", "Groq", "OpenAI", "DeepSeek", "Anthropic", "OpenCode.ai", "Mistral", "xAI", "Together", "Perplexity", "Cerebras", "Fireworks", "SiliconFlow", "Sambanova")) state.providerName else "OpenAI"
                trimmed.length >= 35 -> if (state.providerName.contains("OpenCode", ignoreCase = true)) "OpenCode.ai" else if (state.providerName.isNotBlank() && state.providerName != "Gemini") state.providerName else "Gemini"
                else -> state.providerName
            }
            attemptsList.add(ProviderAttempt(detectedProvider, trimmed, resolveModels(detectedProvider)))
            // If generic sk- key, add OpenRouter, DeepSeek, and OpenAI fallbacks
            if (trimmed.startsWith("sk-") && !trimmed.startsWith("sk-or-") && !trimmed.startsWith("sk-ant-") && !state.providerName.contains("OpenCode", ignoreCase = true)) {
                if (detectedProvider != "OpenRouter") {
                    attemptsList.add(ProviderAttempt("OpenRouter", trimmed, resolveModels("OpenRouter")))
                }
                if (detectedProvider != "DeepSeek") {
                    attemptsList.add(ProviderAttempt("DeepSeek", trimmed, resolveModels("DeepSeek")))
                }
                if (detectedProvider != "OpenAI") {
                    attemptsList.add(ProviderAttempt("OpenAI", trimmed, resolveModels("OpenAI")))
                }
            }
        }

        // 2. Platform Gemini fallback (from BuildConfig) ONLY as a secondary safety net
        if (geminiBuildConfigKey.isNotBlank() && !geminiBuildConfigKey.contains("MY_")) {
            if (attemptsList.none { it.provider == "Gemini" && it.apiKey == geminiBuildConfigKey }) {
                attemptsList.add(ProviderAttempt("Gemini", geminiBuildConfigKey, fallbackModelsGemini))
            }
        }

        // 3. OpenRouter fallback
        if (openRouterBuildConfigKey.isNotBlank() && !openRouterBuildConfigKey.contains("MY_")) {
            val openRouterModels = if (isVisionRequest) visionModelsOpenRouter else fallbackModelsOpenRouter
            if (attemptsList.none { it.provider == "OpenRouter" && it.apiKey == openRouterBuildConfigKey }) {
                attemptsList.add(ProviderAttempt("OpenRouter", openRouterBuildConfigKey, openRouterModels))
            }
        }

        if (attemptsList.isEmpty()) {
            val errorMsg = "⚠️ API Error: No valid API Key configured! Please open Settings (⚙️) -> 'AI Provider' and enter your Gemini, OpenRouter, Groq, or OpenAI API key to enable live AI code generation."
            Log.e(TAG, errorMsg)
            return Result.failure(IllegalStateException(errorMsg))
        }

        var lastError: Throwable? = null

        for (attempt in attemptsList) {
            for (modelName in attempt.models) {
                Log.d(TAG, "Attempting API call -> Provider: ${attempt.provider}, Model: $modelName, Images: ${base64Images.size}")
                val isBuildTask = mode == "coding" || mode == "build"
                val isComplexTask = isBuildTask ||
                    effectiveSystemPrompt.contains("Android", ignoreCase = true) ||
                    prompt.contains("File", ignoreCase = true) ||
                    prompt.contains("package", ignoreCase = true) ||
                    prompt.contains("banao", ignoreCase = true) ||
                    prompt.contains("app", ignoreCase = true) ||
                    prompt.contains("code", ignoreCase = true)
                val timeoutMs = if (isBuildTask) 300_000L else if (isComplexTask) 120_000L else 45_000L
                val result = try {
                    kotlinx.coroutines.withTimeout(timeoutMs) {
                        streamRemote(
                            provider = attempt.provider,
                            apiKey = attempt.apiKey,
                            modelName = modelName,
                            prompt = prompt,
                            mode = mode,
                            history = history,
                            customSystemPrompt = effectiveSystemPrompt,
                            base64Images = base64Images,
                            onChunk = onChunk
                        )
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.w(TAG, "Attempt timed out after ${timeoutMs}ms for ${attempt.provider} ($modelName): ${e.message}")
                    Result.failure(e)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                    Result.failure(e)
                }

                if (result.isSuccess) {
                    val fullText = result.getOrNull()
                    if (!fullText.isNullOrBlank()) {
                        val sanitized = sanitizeAiReply(fullText)
                        Log.i(TAG, "API success with ${attempt.provider} ($modelName)!")
                        if (!hasCustomUserKey) {
                            com.example.data.local.AdminConfigManager.recordApiUsage(usageType)
                        }
                        return Result.success(sanitized)
                    }
                }

                val err = result.exceptionOrNull()
                lastError = err
                Log.w(TAG, "Attempt with ${attempt.provider} ($modelName) failed: ${err?.message}")
                val errMsg = err?.message ?: ""
                if (errMsg.contains("401") || errMsg.contains("403") || errMsg.contains("Invalid API Key") || errMsg.contains("quota", ignoreCase = true) || errMsg.contains("429") || errMsg.contains("resource_exhausted", ignoreCase = true) || errMsg.contains("exceeded", ignoreCase = true)) {
                    Log.w(TAG, "Fast aborting remaining models for ${attempt.provider} due to key/quota error ($errMsg)")
                    break
                }
                delay(150)
            }
        }

        val errDetail = lastError?.message?.ifBlank { null } ?: "All attempts failed without response"
        val errorMsg = "⚠️ API Error: $errDetail. All configured AI models failed. Please check your API key, model, or network in Settings (⚙️)."
        Log.e(TAG, errorMsg)
        return Result.failure(IllegalStateException(errorMsg))
    }

    private suspend fun streamRemote(
        provider: String,
        apiKey: String,
        modelName: String,
        prompt: String,
        mode: String = "chat",
        history: List<Pair<String, String>>,
        customSystemPrompt: String,
        base64Images: List<String> = emptyList(),
        onChunk: suspend (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val fullResponse = java.lang.StringBuilder()
        val isBuildMode = mode == "coding" || mode == "build"
        
        if (provider.equals("Gemini", ignoreCase = true)) {
            // Gemini uses its own specific format
            return@withContext GeminiService.streamGenerateContentWithKey(
                apiKey = trimmedKey,
                modelName = modelName,
                prompt = prompt,
                mode = mode,
                history = history,
                temperature = if (isBuildMode) 1.0f else 0.7f,
                customSystemPrompt = customSystemPrompt,
                base64Images = base64Images,
                onChunk = onChunk
            )
        }
        
        val isAnthropic = provider.contains("Anthropic", ignoreCase = true)
        val baseUrl = when {
            isAnthropic -> "https://api.anthropic.com/v1/messages"
            provider.startsWith("http://") || provider.startsWith("https://") -> provider
            trimmedKey.startsWith("http://") || trimmedKey.startsWith("https://") -> trimmedKey.substringBefore(" ")
            provider.contains("InceptionLabs", ignoreCase = true) || provider.contains("inceptionlabs", ignoreCase = true) -> "https://api.inceptionlabs.ai/v1/chat/completions"
            provider.contains("Atria", ignoreCase = true) || provider.contains("atria-asi", ignoreCase = true) -> "https://api.atria-asi.ai/v1/chat/completions"
            provider.contains("OpenRouter", ignoreCase = true) -> "https://openrouter.ai/api/v1/chat/completions"
            provider.contains("Groq", ignoreCase = true) -> "https://api.groq.com/openai/v1/chat/completions"
            provider.contains("OpenAI", ignoreCase = true) -> "https://api.openai.com/v1/chat/completions"
            provider.contains("DeepSeek", ignoreCase = true) -> "https://api.deepseek.com/chat/completions"
            provider.contains("OpenCode", ignoreCase = true) -> "https://opencode.ai/inference/openai/v1/chat/completions"
            provider.contains("Mistral", ignoreCase = true) -> "https://api.mistral.ai/v1/chat/completions"
            provider.contains("Together", ignoreCase = true) -> "https://api.together.xyz/v1/chat/completions"
            provider.contains("xAI", ignoreCase = true) -> "https://api.x.ai/v1/chat/completions"
            provider.contains("Perplexity", ignoreCase = true) -> "https://api.perplexity.ai/chat/completions"
            provider.contains("Cerebras", ignoreCase = true) -> "https://api.cerebras.ai/v1/chat/completions"
            provider.contains("Fireworks", ignoreCase = true) -> "https://api.fireworks.ai/inference/v1/chat/completions"
            provider.contains("Sambanova", ignoreCase = true) -> "https://api.sambanova.ai/v1/chat/completions"
            provider.contains("SiliconFlow", ignoreCase = true) -> "https://api.siliconflow.cn/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
        
        val effectivePrompt = if (prompt.isNotBlank()) {
            prompt.trim()
        } else if (base64Images.isNotEmpty()) {
            "Please analyze this image and provide a detailed explanation or answer the questions shown in it."
        } else {
            "Hello"
        }

        val jsonBody = JSONObject().apply {
            put("model", modelName)
            put("stream", true)
            put("temperature", if (isBuildMode) 1.0 else 0.7)
            put("max_tokens", if (isBuildMode) 65536 else 8192)
            if (isAnthropic) {
                if (customSystemPrompt.isNotEmpty()) {
                    put("system", customSystemPrompt)
                }
            }
            
            val messagesArray = JSONArray()
            if (!isAnthropic && customSystemPrompt.isNotEmpty()) {
                val sysObj = JSONObject()
                sysObj.put("role", "system")
                sysObj.put("content", customSystemPrompt)
                messagesArray.put(sysObj)
            }
            
            for (msg in history) {
                val histObj = JSONObject()
                histObj.put("role", if (msg.first == "user") "user" else "assistant")
                histObj.put("content", msg.second)
                messagesArray.put(histObj)
            }

            val userObj = JSONObject()
            userObj.put("role", "user")
            if (base64Images.isEmpty()) {
                userObj.put("content", effectivePrompt)
            } else {
                val contentParts = JSONArray()
                contentParts.put(JSONObject().apply {
                    put("type", "text")
                    put("text", effectivePrompt)
                })
                for (b64 in base64Images) {
                    val cleanB64 = b64.substringAfter("base64,").trim().replace("\n", "").replace("\r", "")
                    if (cleanB64.isNotBlank()) {
                        contentParts.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$cleanB64")
                            })
                        })
                    }
                }
                userObj.put("content", contentParts)
            }
            messagesArray.put(userObj)
            
            put("messages", messagesArray)
        }
        
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            
        if (isAnthropic) {
            requestBuilder.addHeader("x-api-key", trimmedKey)
            requestBuilder.addHeader("anthropic-version", "2023-06-01")
        } else {
            requestBuilder.addHeader("Authorization", "Bearer $trimmedKey")
        }
            
        if (provider.contains("OpenRouter", ignoreCase = true)) {
            requestBuilder.addHeader("HTTP-Referer", "https://github.com/google/ai-studio")
            requestBuilder.addHeader("X-Title", "AI Studio App")
        }
        if (provider.contains("OpenCode", ignoreCase = true)) {
            requestBuilder.addHeader("X-Api-Key", trimmedKey)
        }
        
        val request = requestBuilder.build()
        
        try {
            var response = client.newCall(request).execute()
            if (!response.isSuccessful && provider.contains("OpenCode", ignoreCase = true) && (response.code == 404 || response.code == 502 || response.code == 400)) {
                val zenRequest = requestBuilder.url("https://opencode.ai/zen/v1/chat/completions").build()
                val zenResponse = client.newCall(zenRequest).execute()
                if (zenResponse.isSuccessful || zenResponse.code == 401) {
                    response = zenResponse
                }
            }
            if (!response.isSuccessful && response.code == 400 && customSystemPrompt.isNotBlank()) {
                val errBody = response.body?.string() ?: ""
                if (errBody.contains("system", ignoreCase = true) || errBody.contains("developer", ignoreCase = true) || errBody.contains("role", ignoreCase = true)) {
                    val fallbackJson = JSONObject().apply {
                        put("model", modelName)
                        put("stream", true)
                        put("temperature", 0.7)
                        val fallbackMessages = JSONArray()
                        for (msg in history) {
                            val histObj = JSONObject()
                            histObj.put("role", if (msg.first == "user") "user" else "assistant")
                            histObj.put("content", msg.second)
                            fallbackMessages.put(histObj)
                        }
                        val userObj = JSONObject()
                        userObj.put("role", "user")
                        userObj.put("content", "System Directives:\n$customSystemPrompt\n\nUser Request:\n$effectivePrompt")
                        fallbackMessages.put(userObj)
                        put("messages", fallbackMessages)
                    }
                    val retryRequest = requestBuilder.post(fallbackJson.toString().toRequestBody("application/json".toMediaType())).build()
                    val retryResponse = client.newCall(retryRequest).execute()
                    if (retryResponse.isSuccessful) {
                        response = retryResponse
                    }
                }
            }
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                if (response.code == 401) {
                    throw Exception("Invalid API Key for $provider. Please verify your key from opencode.ai console.")
                }
                if (errBody.contains("FreeTierError", ignoreCase = true) || errBody.contains("free tier can only be used from within OpenCode", ignoreCase = true)) {
                    throw Exception("OpenCode model '$modelName' free-tier restriction. Please choose another model like 'glm-5.1' or 'deepseek-v4-flash' or use an active key.")
                }
                throw Exception("HTTP ${response.code}: $errBody")
            }
            
            val bodyStream = response.body?.byteStream()
            if (bodyStream != null) {
                val reader = BufferedReader(InputStreamReader(bodyStream))
                var line: String?
                var isFirstLine = true
                val nonSseBuffer = StringBuilder()

                while (reader.readLine().also { line = it } != null) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val curLine = line?.trim() ?: continue

                    // Check if response is non-SSE JSON
                    if (isFirstLine && !curLine.startsWith("data:") && curLine.startsWith("{")) {
                        nonSseBuffer.append(curLine)
                        while (reader.readLine().also { line = it } != null) {
                            nonSseBuffer.append(line)
                        }
                        try {
                            val jsonObj = JSONObject(nonSseBuffer.toString())
                            val choices = jsonObj.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val msg = choice.optJSONObject("message")
                                val content = msg?.optString("content", "") ?: choice.optString("text", "")
                                if (content.isNotEmpty()) {
                                    fullResponse.append(content)
                                    onChunk(content)
                                }
                            } else {
                                val contentArr = jsonObj.optJSONArray("content")
                                if (contentArr != null && contentArr.length() > 0) {
                                    val block = contentArr.getJSONObject(0)
                                    val text = block.optString("text", "")
                                    if (text.isNotEmpty()) {
                                        fullResponse.append(text)
                                        onChunk(text)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing non-SSE JSON response: ${e.message}")
                        }
                        break
                    }
                    isFirstLine = false

                    if (curLine.startsWith("data:") && curLine != "data: [DONE]" && curLine != "data:[DONE]") {
                        try {
                            val dataJson = curLine.removePrefix("data:").trim()
                            if (dataJson.isEmpty()) continue
                            val jsonObj = JSONObject(dataJson)
                            val choices = jsonObj.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val content = delta?.optString("content", "") ?: delta?.optString("text", "") ?: ""
                                if (content.isNotEmpty()) {
                                    fullResponse.append(content)
                                    onChunk(content)
                                }
                            } else if (jsonObj.optString("type") == "content_block_delta") {
                                val delta = jsonObj.optJSONObject("delta")
                                val text = delta?.optString("text", "") ?: ""
                                if (text.isNotEmpty()) {
                                    fullResponse.append(text)
                                    onChunk(text)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing SSE JSON: ${e.message}")
                        }
                    }
                }
            }
            val sanitized = sanitizeAiReply(fullResponse.toString())
            Result.success(sanitized)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sanitizeAiReply(raw: String): String {
        var cleaned = raw.replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[THOUGHT\][\s\S]*?\[/THOUGHT\]""", RegexOption.IGNORE_CASE), "")
            .trim()

        // Filter out model self-dialogue or leaked directives (e.g. User says "...", According to directive...)
        if (cleaned.startsWith("User says", ignoreCase = true) || cleaned.startsWith("According to directive", ignoreCase = true)) {
            val lines = cleaned.lines()
            val finalLines = mutableListOf<String>()
            var foundActualReply = false
            for (l in lines) {
                val trimmed = l.trim()
                if (!foundActualReply) {
                    if (trimmed.startsWith("User says", ignoreCase = true) ||
                        trimmed.startsWith("According to directive", ignoreCase = true) ||
                        trimmed.startsWith("Should I ", ignoreCase = true) ||
                        trimmed.startsWith("Probably:", ignoreCase = true) ||
                        trimmed.startsWith("Provide professional tone", ignoreCase = true) ||
                        trimmed.startsWith("That's 1-2 sentences", ignoreCase = true) ||
                        trimmed.startsWith("Good.", ignoreCase = true) ||
                        trimmed.startsWith("Directive:", ignoreCase = true) ||
                        trimmed.startsWith("Also mention", ignoreCase = true)
                    ) {
                        continue
                    } else if (trimmed.isNotBlank()) {
                        foundActualReply = true
                        finalLines.add(l)
                    }
                } else {
                    finalLines.add(l)
                }
            }
            if (finalLines.isNotEmpty()) {
                cleaned = finalLines.joinToString("\n").trim()
            }
        }
        return cleaned
    }
    
    suspend fun fetchModels(provider: String, apiKey: String): List<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        val trimmedKey = sanitizeKey(apiKey)
        if (trimmedKey.isBlank()) return@withContext emptyList()
        
        val effectiveProvider = when {
            trimmedKey.startsWith("AIza") || trimmedKey.startsWith("AQ.") -> "Gemini"
            trimmedKey.startsWith("gsk_") -> "Groq"
            trimmedKey.startsWith("sk-or-") -> "OpenRouter"
            trimmedKey.startsWith("sk-ant-") -> "Anthropic"
            trimmedKey.startsWith("opencode", ignoreCase = true) || trimmedKey.startsWith("oc-") -> "OpenCode.ai"
            trimmedKey.startsWith("sk-proj-") || trimmedKey.startsWith("sk-admin-") -> "OpenAI"
            trimmedKey.startsWith("xai-") -> "xAI"
            trimmedKey.startsWith("mistral-") -> "Mistral"
            trimmedKey.startsWith("together-") -> "Together"
            trimmedKey.startsWith("pplx-") -> "Perplexity"
            trimmedKey.startsWith("csk-") -> "Cerebras"
            trimmedKey.startsWith("fw_") || trimmedKey.startsWith("fwi_") -> "Fireworks"
            else -> provider
        }
        
        try {
            if (effectiveProvider.equals("Gemini", ignoreCase = true)) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmedKey"
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val modelsArray = json.optJSONArray("models") ?: JSONArray()
                    val resultList = mutableListOf<Pair<String, Boolean>>()
                    for (i in 0 until modelsArray.length()) {
                        val modelObj = modelsArray.getJSONObject(i)
                        val name = modelObj.optString("name", "").replace("models/", "")
                        // For Gemini, we might treat pro as paid and flash as free for categorization
                        val isFree = name.contains("flash") || name.contains("gemini-1.5") || name.contains("gemini-2.0") || name.contains("gemini-2.5")
                        if (name.isNotEmpty()) {
                            resultList.add(Pair(name, isFree))
                        }
                    }
                    return@withContext resultList.sortedByDescending { it.second }
                }
                return@withContext emptyList()
            }
            
            if (effectiveProvider.contains("OpenCode", ignoreCase = true)) {
                val opencodeEndpoints = listOf(
                    "https://opencode.ai/inference/v1/models",
                    "https://opencode.ai/zen/v1/models"
                )
                var lastErr: String? = null
                for (url in opencodeEndpoints) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .get()
                            .addHeader("Authorization", "Bearer $trimmedKey")
                            .addHeader("X-Api-Key", trimmedKey)
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                            .build()

                        val response = client.newCall(request).execute()
                        val code = response.code
                        val respBody = response.body?.string() ?: ""

                        if (code == 401 || code == 403) {
                            throw IllegalStateException("Invalid OpenCode.ai API Key ($code). Please check your key from opencode.ai console.")
                        }

                        if (response.isSuccessful && respBody.isNotBlank()) {
                            val json = JSONObject(respBody)
                            val dataArray = json.optJSONArray("data") ?: JSONArray()
                            val resultList = mutableListOf<Pair<String, Boolean>>()
                            for (i in 0 until dataArray.length()) {
                                val modelObj = dataArray.getJSONObject(i)
                                val id = modelObj.optString("id", "")
                                if (id.isNotBlank()) {
                                    val isFree = id.contains("-free") || id == "big-pickle"
                                    resultList.add(Pair(id, isFree))
                                }
                            }

                            val topModels = listOf(
                                "glm-5.1", "deepseek-v4-flash", "kimi-k2.6", "minimax-m2.7",
                                "ling-3.0-flash-fin-free", "mimo-v2.5-free", "mimo-v2.6-flash-free",
                                "nemotron-3-ultra-free", "space-bunny-free", "claude-sonnet-4-5",
                                "gemini-3.7-flash", "gpt-5", "big-pickle"
                            )
                            return@withContext resultList.sortedWith(
                                compareByDescending<Pair<String, Boolean>> { topModels.contains(it.first) }
                                    .thenByDescending { it.second }
                                    .thenBy { it.first }
                            )
                        }
                    } catch (e: Exception) {
                        if (e is IllegalStateException) throw e
                        lastErr = e.message
                    }
                }
                if (lastErr != null) {
                    throw IllegalStateException("Failed to connect to OpenCode.ai: $lastErr")
                }
                return@withContext emptyList()
            }

            if (effectiveProvider.contains("Anthropic", ignoreCase = true)) {
                // Test Anthropic key with lightweight validation
                val testJson = JSONObject().apply {
                    put("model", "claude-3-5-haiku-20241022")
                    put("max_tokens", 1)
                    val msgs = JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                    put("messages", msgs)
                }
                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .post(testJson.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("x-api-key", trimmedKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .build()
                val response = try { client.newCall(request).execute() } catch (e: Exception) { null }
                if (response != null && (response.code == 401 || response.code == 403)) {
                    throw IllegalStateException("Invalid Anthropic API Key (${response.code}). Please check your key in settings.")
                }
                return@withContext listOf(
                    Pair("claude-3-5-sonnet-20241022", false),
                    Pair("claude-3-5-haiku-20241022", false),
                    Pair("claude-3-haiku-20240307", false),
                    Pair("claude-3-opus-20240229", false)
                )
            }

            // Universal /v1/models endpoint for OpenAI-compatible providers
            val baseUrl = when {
                effectiveProvider.startsWith("http://") || effectiveProvider.startsWith("https://") -> if (effectiveProvider.endsWith("/models")) effectiveProvider else "${effectiveProvider.trimEnd('/')}/models"
                effectiveProvider.contains("DeepSeek", true) -> "https://api.deepseek.com/models"
                effectiveProvider.contains("Mistral", true) -> "https://api.mistral.ai/v1/models"
                effectiveProvider.contains("Together", true) -> "https://api.together.xyz/v1/models"
                effectiveProvider.contains("Cerebras", true) -> "https://api.cerebras.ai/v1/models"
                effectiveProvider.contains("Fireworks", true) -> "https://api.fireworks.ai/inference/v1/models"
                effectiveProvider.contains("Sambanova", true) -> "https://api.sambanova.ai/v1/models"
                effectiveProvider.contains("SiliconFlow", true) -> "https://api.siliconflow.cn/v1/models"
                effectiveProvider.contains("xAI", true) -> "https://api.x.ai/v1/models"
                effectiveProvider.contains("Perplexity", true) -> "https://api.perplexity.ai/models"
                effectiveProvider.contains("OpenRouter", true) -> "https://openrouter.ai/api/v1/models"
                effectiveProvider.contains("Groq", true) -> "https://api.groq.com/openai/v1/models"
                effectiveProvider.contains("OpenAI", true) -> "https://api.openai.com/v1/models"
                effectiveProvider.contains("InceptionLabs", true) -> "https://api.inceptionlabs.ai/v1/models"
                effectiveProvider.contains("Atria", true) -> "https://api.atria-asi.ai/v1/models"
                else -> "https://api.openai.com/v1/models"
            }

            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .addHeader("Authorization", "Bearer $trimmedKey")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()

            val response = try { client.newCall(request).execute() } catch (e: Exception) { null }
            if (response != null && (response.code == 401 || response.code == 403)) {
                throw IllegalStateException("Invalid API Key for $effectiveProvider (${response.code}). Please check your key in settings.")
            }
            if (response != null && response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val dataArray = json.optJSONArray("data") ?: JSONArray()
                val resultList = mutableListOf<Pair<String, Boolean>>()
                for (i in 0 until dataArray.length()) {
                    val modelObj = dataArray.getJSONObject(i)
                    val id = modelObj.optString("id", "")

                    // OpenRouter indicates free models with ":free" or their pricing is 0
                    // Groq is free. OpenAI has no free ones technically.
                    var isFree = false
                    if (effectiveProvider == "OpenRouter" && id.endsWith(":free")) {
                        isFree = true
                    } else if (effectiveProvider == "Groq") {
                        isFree = true // Groq is currently free
                    } else if (effectiveProvider == "Gemini") {
                        isFree = true
                    } else if (effectiveProvider == "InceptionLabs" || effectiveProvider == "Atria ASI") {
                        isFree = true
                    }

                    if (id.isNotEmpty()) {
                        resultList.add(Pair(id, isFree))
                    }
                }
                if (resultList.isNotEmpty()) {
                    return@withContext resultList.sortedByDescending { it.second }
                }
            }

            // Fallbacks for providers if models list API endpoint is not implemented or fails
            if (effectiveProvider.contains("DeepSeek", true)) {
                return@withContext listOf(
                    Pair("deepseek-chat", true),
                    Pair("deepseek-coder", true),
                    Pair("deepseek-reasoner", false)
                )
            }
            if (effectiveProvider.contains("Mistral", true)) {
                return@withContext listOf(
                    Pair("mistral-large-latest", false),
                    Pair("mistral-small-latest", true),
                    Pair("codestral-latest", false),
                    Pair("open-mistral-nemo", true)
                )
            }
            if (effectiveProvider.contains("Together", true)) {
                return@withContext listOf(
                    Pair("meta-llama/Llama-3.3-70B-Instruct-Turbo", true),
                    Pair("deepseek-ai/DeepSeek-V3", true),
                    Pair("Qwen/Qwen2.5-Coder-32B-Instruct", true)
                )
            }
            if (effectiveProvider.contains("Cerebras", true)) {
                return@withContext listOf(
                    Pair("llama-3.3-70b", true),
                    Pair("llama3.1-8b", true)
                )
            }
            if (effectiveProvider.contains("xAI", true)) {
                return@withContext listOf(
                    Pair("grok-2-latest", false),
                    Pair("grok-beta", false)
                )
            }
            if (effectiveProvider.contains("Perplexity", true)) {
                return@withContext listOf(
                    Pair("sonar", true),
                    Pair("sonar-pro", false)
                )
            }
            if (effectiveProvider.contains("InceptionLabs", true)) {
                return@withContext listOf(
                    Pair("merlin-32k", true),
                    Pair("merlin", true),
                    Pair("merlin-code", false),
                    Pair("inception-3", false)
                )
            }
            if (effectiveProvider.contains("Atria", true)) {
                return@withContext listOf(
                    Pair("atria-1", true),
                    Pair("asi-chat", true),
                    Pair("atria-asi-v1", false),
                    Pair("atria-instruct", false)
                )
            }
            return@withContext emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching models: ${e.message}")
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Connection failed: ${e.message ?: "Unknown error"}")
        }
    }
}
