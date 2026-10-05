package com.example.util

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object AudioTranscriptionEngine {
    private const val TAG = "AudioTranscriptionEngine"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun resolveActiveApiKey(): String {
        val providerState = com.example.data.local.ProviderSettingsManager.loadState()
        val keys = providerState.apiKeys
        val configuredKey = if (keys.isNotEmpty()) keys[providerState.currentKeyIndex % keys.size] else ""
        val geminiBuildConfigKey = try { com.example.BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        val openRouterKey = try { com.example.BuildConfig.OPENROUTER_API_KEY } catch (e: Exception) { "" }

        return when {
            configuredKey.isNotBlank() && !configuredKey.contains("MY_") -> configuredKey.trim()
            geminiBuildConfigKey.isNotBlank() && !geminiBuildConfigKey.contains("MY_") -> geminiBuildConfigKey.trim()
            openRouterKey.isNotBlank() && !openRouterKey.contains("MY_") -> openRouterKey.trim()
            else -> ""
        }
    }

    fun wrapPcmToWav(pcmData: ByteArray, sampleRate: Int = 16000, channels: Int = 1, bitDepth: Int = 16): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitDepth / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitDepth / 8).toByte()
        header[33] = 0
        header[34] = bitDepth.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        val output = ByteArrayOutputStream()
        output.write(header)
        output.write(pcmData)
        return output.toByteArray()
    }

    suspend fun transcribeAudioFile(file: java.io.File, apiKey: String = ""): Result<String> {
        return withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(Exception("Audio file is empty or missing."))
            }
            val rawBytes = file.readBytes()
            val base64Audio = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            val ext = file.extension.lowercase()
            val mimeType = when (ext) {
                "wav" -> "audio/wav"
                "m4a", "mp4" -> "audio/mp4"
                "aac" -> "audio/aac"
                "mp3" -> "audio/mpeg"
                "ogg" -> "audio/ogg"
                else -> "audio/mp4"
            }

            val candidateKeys = mutableListOf<String>()
            if (apiKey.isNotBlank() && !apiKey.contains("MY_")) {
                candidateKeys.add(apiKey.trim())
            }

            try {
                val state = com.example.data.local.ProviderSettingsManager.loadState()
                for (k in state.apiKeys) {
                    val t = k.trim()
                    if (t.isNotBlank() && !t.contains("MY_") && !candidateKeys.contains(t)) {
                        candidateKeys.add(t)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading provider settings keys: ${e.message}")
            }

            val geminiBuildConfigKey = try { com.example.BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }.trim()
            if (geminiBuildConfigKey.isNotBlank() && !geminiBuildConfigKey.contains("MY_") && !candidateKeys.contains(geminiBuildConfigKey)) {
                candidateKeys.add(geminiBuildConfigKey)
            }

            val openRouterKey = try { com.example.BuildConfig.OPENROUTER_API_KEY } catch (e: Exception) { "" }.trim()
            if (openRouterKey.isNotBlank() && !openRouterKey.contains("MY_") && !candidateKeys.contains(openRouterKey)) {
                candidateKeys.add(openRouterKey)
            }

            if (candidateKeys.isEmpty()) {
                return@withContext Result.failure(Exception("Missing API key for transcription."))
            }

            // Phase 1: Try keys based on their detected provider prefix
            for (candidate in candidateKeys) {
                val trimmedKey = candidate.trim()

                // 1. Groq Whisper (ultrafast)
                if (trimmedKey.startsWith("gsk_")) {
                    val res = callWhisperApi("https://api.groq.com/openai/v1/audio/transcriptions", "whisper-large-v3", trimmedKey, rawBytes, file.name, mimeType)
                    if (res.isSuccess && !res.getOrNull().isNullOrBlank()) {
                        return@withContext res
                    }
                }

                // 2. OpenAI Whisper
                if (trimmedKey.startsWith("sk-") && !trimmedKey.startsWith("sk-or-") && !trimmedKey.startsWith("sk-ant-")) {
                    val res = callWhisperApi("https://api.openai.com/v1/audio/transcriptions", "whisper-1", trimmedKey, rawBytes, file.name, mimeType)
                    if (res.isSuccess && !res.getOrNull().isNullOrBlank()) {
                        return@withContext res
                    }
                }

                // 3. Gemini API
                if (trimmedKey.startsWith("AIza") || (!trimmedKey.startsWith("gsk_") && !trimmedKey.startsWith("sk-"))) {
                    val res = callGeminiTranscription(trimmedKey, base64Audio, mimeType)
                    if (res.isSuccess && !res.getOrNull().isNullOrBlank()) {
                        return@withContext res
                    }
                }

                // 4. OpenRouter
                if (trimmedKey.startsWith("sk-or-")) {
                    val res = callOpenRouterAudio(trimmedKey, base64Audio, mimeType)
                    if (res.isSuccess && !res.getOrNull().isNullOrBlank()) {
                        return@withContext res
                    }
                }
            }

            // Phase 2: If no prefix matched or prefix-based attempt failed, try all keys against Groq, OpenAI, and Gemini
            for (candidate in candidateKeys) {
                val trimmedKey = candidate.trim()

                val groqRes = callWhisperApi("https://api.groq.com/openai/v1/audio/transcriptions", "whisper-large-v3", trimmedKey, rawBytes, file.name, mimeType)
                if (groqRes.isSuccess && !groqRes.getOrNull().isNullOrBlank()) return@withContext groqRes

                val openaiRes = callWhisperApi("https://api.openai.com/v1/audio/transcriptions", "whisper-1", trimmedKey, rawBytes, file.name, mimeType)
                if (openaiRes.isSuccess && !openaiRes.getOrNull().isNullOrBlank()) return@withContext openaiRes

                val geminiRes = callGeminiTranscription(trimmedKey, base64Audio, mimeType)
                if (geminiRes.isSuccess && !geminiRes.getOrNull().isNullOrBlank()) return@withContext geminiRes
            }

            Result.failure(Exception("Audio transcription failed across all available keys."))
        }
    }

    private fun callWhisperApi(endpoint: String, model: String, key: String, rawBytes: ByteArray, fileName: String, mimeType: String): Result<String> {
        return try {
            val reqBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("file", fileName, rawBytes.toRequestBody(mimeType.toMediaType()))
                .build()

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $key")
                .post(reqBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(bodyString)
                val text = json.optString("text", "")
                if (text.isNotBlank()) {
                    Result.success(text.trim())
                } else {
                    Result.failure(Exception("Empty transcription result"))
                }
            } else {
                Log.w(TAG, "Whisper ($model) failed HTTP ${response.code}: $bodyString")
                Result.failure(Exception("HTTP ${response.code}: $bodyString"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Whisper transcription error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun callGeminiTranscription(key: String, base64Audio: String, mimeType: String): Result<String> {
        val resolvedPrimary = com.example.data.remote.ModelResolver.resolve(com.example.data.remote.ModelRole.TRANSCRIBE)
        val models = listOf(resolvedPrimary, "gemini-3.5-transcribe", "gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-flash-latest").distinct()
        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                val jsonBody = JSONObject()
                val partsArray = JSONArray()

                partsArray.put(JSONObject().put("text", "Transcribe only the spoken human voice verbatim in the original spoken language (Hindi, English, or Hinglish). Accurately transcribe phonetic words, names, brand names (e.g. POCO, Samsung), and technical terms. Ignore background sounds, fan hum, keyboard clicks, breaths, coughs, or room noise. Return ONLY the transcribed text. Do NOT add conversational replies, greetings, explanations, or quotes."))

                val inlineData = JSONObject().apply {
                    put("mimeType", mimeType)
                    put("data", base64Audio)
                }
                partsArray.put(JSONObject().put("inlineData", inlineData))

                val contentObj = JSONObject().apply {
                    put("parts", partsArray)
                }
                jsonBody.put("contents", JSONArray().put(contentObj))

                val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(requestBody).build()

                val response = httpClient.newCall(request).execute()
                val bodyString = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(bodyString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val text = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.getJSONObject(0)?.optString("text", "") ?: ""
                        if (text.isNotBlank()) {
                            return Result.success(text.trim())
                        }
                    }
                } else {
                    if (response.code == 404 || response.code == 400) {
                        com.example.data.remote.ModelResolver.markDead(model)
                    } else if (response.code == 429 || response.code == 403) {
                        com.example.data.remote.GeminiKeyPool.reportError(key, response.code)
                    }
                    Log.w(TAG, "Gemini $model failed HTTP ${response.code}: $bodyString")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini transcription attempt ($model) error: ${e.message}")
            }
        }
        return Result.failure(Exception("Gemini transcription failed"))
    }

    private fun callOpenRouterAudio(key: String, base64Audio: String, mimeType: String): Result<String> {
        val orModels = listOf("google/gemini-2.0-flash-001", "openai/whisper-large-v3", "meta-llama/llama-3.2-11b-vision-instruct")
        for (orModel in orModels) {
            try {
                val url = "https://openrouter.ai/api/v1/chat/completions"
                val jsonBody = JSONObject().apply {
                    put("model", orModel)
                    val messagesArray = JSONArray()
                    val userObj = JSONObject().apply {
                        put("role", "user")
                        val contentParts = JSONArray()
                        contentParts.put(JSONObject().apply {
                            put("type", "text")
                            put("text", "Transcribe only the spoken human voice verbatim in the original spoken language (Hindi, English, or Hinglish). Accurately transcribe phonetic words, names, brand names (e.g. POCO, Samsung), and technical terms. Ignore background sounds, fan hum, keyboard clicks, breaths, coughs, or room noise. Return ONLY the transcribed text. Do NOT add conversational replies, greetings, explanations, or quotes.")
                        })
                        contentParts.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:$mimeType;base64,$base64Audio")
                            })
                        })
                        put("content", contentParts)
                    }
                    messagesArray.put(userObj)
                    put("messages", messagesArray)
                }

                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("HTTP-Referer", "https://github.com/google/ai-studio")
                    .addHeader("X-Title", "AI Studio App")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(req).execute()
                val bodyString = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyString)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val text = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                        if (text.isNotBlank()) {
                            return Result.success(text.trim())
                        }
                    }
                } else {
                    Log.w(TAG, "OpenRouter transcription ($orModel) failed HTTP ${response.code}: $bodyString")
                }
            } catch (e: Exception) {
                Log.w(TAG, "OpenRouter transcription error: ${e.message}")
            }
        }
        return Result.failure(Exception("OpenRouter audio transcription failed"))
    }

    suspend fun transcribeWav(apiKey: String, base64Audio: String, rawWavBytes: ByteArray): Result<String> {
        return withContext(Dispatchers.IO) {
            val candidateKeys = mutableListOf<String>()
            if (apiKey.isNotBlank() && !apiKey.contains("MY_")) {
                candidateKeys.add(apiKey.trim())
            }

            // Also collect user configured keys and build config keys as fallbacks
            try {
                val state = com.example.data.local.ProviderSettingsManager.loadState()
                for (k in state.apiKeys) {
                    val t = k.trim()
                    if (t.isNotBlank() && !t.contains("MY_") && !candidateKeys.contains(t)) {
                        candidateKeys.add(t)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading provider settings keys: ${e.message}")
            }

            val geminiBuildConfigKey = try { com.example.BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }.trim()
            if (geminiBuildConfigKey.isNotBlank() && !geminiBuildConfigKey.contains("MY_") && !candidateKeys.contains(geminiBuildConfigKey)) {
                candidateKeys.add(geminiBuildConfigKey)
            }

            val openRouterKey = try { com.example.BuildConfig.OPENROUTER_API_KEY } catch (e: Exception) { "" }.trim()
            if (openRouterKey.isNotBlank() && !openRouterKey.contains("MY_") && !candidateKeys.contains(openRouterKey)) {
                candidateKeys.add(openRouterKey)
            }

            if (candidateKeys.isEmpty()) {
                return@withContext Result.failure(Exception("Missing API key for transcription."))
            }

            for (candidate in candidateKeys) {
                val trimmedKey = candidate.trim()

                // 1. Try Groq Whisper (for gsk_ keys)
                if (trimmedKey.startsWith("gsk_")) {
                    val res = callWhisperApi("https://api.groq.com/openai/v1/audio/transcriptions", "whisper-large-v3", trimmedKey, rawWavBytes, "audio.wav", "audio/wav")
                    if (res.isSuccess && !res.getOrNull().isNullOrBlank()) return@withContext res
                }

                // 2. Try OpenAI Whisper (for standard sk- keys, excluding OpenRouter sk-or-)
                if (trimmedKey.startsWith("sk-") && !trimmedKey.startsWith("sk-or-") && !trimmedKey.startsWith("sk-ant-")) {
                    val res = callWhisperApi("https://api.openai.com/v1/audio/transcriptions", "whisper-1", trimmedKey, rawWavBytes, "audio.wav", "audio/wav")
                    if (res.isSuccess && !res.getOrNull().isNullOrBlank()) return@withContext res
                }

                // 3. Try Gemini API if key looks like Gemini or general API key
                if (trimmedKey.startsWith("AIza") || (!trimmedKey.startsWith("gsk_") && !trimmedKey.startsWith("sk-"))) {
                    val res = callGeminiTranscription(trimmedKey, base64Audio, "audio/wav")
                    if (res.isSuccess && !res.getOrNull().isNullOrBlank()) return@withContext res
                }

                // 4. Try OpenRouter multimodal completion for sk-or- keys
                if (trimmedKey.startsWith("sk-or-")) {
                    val res = callOpenRouterAudio(trimmedKey, base64Audio, "audio/wav")
                    if (res.isSuccess && !res.getOrNull().isNullOrBlank()) return@withContext res
                }
            }

            Result.failure(Exception("Audio transcription failed across all available keys."))
        }
    }
}
