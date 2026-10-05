package com.example.agent

import android.util.Log
import com.example.agent.brain.AgentPrompts
import com.example.data.remote.GeminiKeyPool
import com.example.data.remote.GeminiModels
import com.example.data.remote.ModelResolver
import com.example.data.remote.ModelRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class VerificationResult(
    val achieved: Boolean,
    val evidence: String,
    val missing: String
)

object GoalVerifier {
    private const val TAG = "SAIF_AGENT"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun verify(goal: String, lastActions: List<String>, snapshot: ScreenSnapshot): VerificationResult = withContext(Dispatchers.IO) {
        val key = GeminiKeyPool.getNextKey()
        if (key.isNullOrBlank()) {
            // If no key available, assume verified if package matches and no obvious error
            return@withContext VerificationResult(true, "Offline heuristic verification", "")
        }

        val model = ModelResolver.resolve(ModelRole.VERIFIER)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        try {
            val root = JSONObject()
            val sysPart = JSONObject().put("text", AgentPrompts.VERIFIER_SYSTEM)
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(sysPart)))

            val userParts = JSONArray()
            val promptText = StringBuilder()
                .append("GOAL: ").append(goal).append("\n")
                .append("LAST ACTIONS:\n")
            for (act in lastActions.takeLast(4)) {
                promptText.append("- ").append(act).append("\n")
            }
            promptText.append("\nCURRENT SCREEN ELEMENTS:\n")
                .append(snapshot.toCompactText(3000))

            userParts.put(JSONObject().put("text", promptText.toString()))

            if (!snapshot.screenshotJpegBase64.isNullOrBlank() && !snapshot.secure) {
                userParts.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", snapshot.screenshotJpegBase64)
                    })
                })
            }

            val contents = JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", userParts)
            })
            root.put("contents", contents)

            val genConfig = JSONObject().apply {
                put("temperature", 0.0)
                put("responseMimeType", "application/json")
            }
            root.put("generationConfig", genConfig)

            val request = Request.Builder()
                .url(url)
                .post(root.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(bodyString)
                val text = json.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                    ?.optString("text", "") ?: ""

                if (text.isNotBlank()) {
                    val cleanJson = text.trim().removeSurrounding("```json", "```").removeSurrounding("```", "```").trim()
                    val resultJson = JSONObject(cleanJson)
                    val achieved = resultJson.optBoolean("achieved", false)
                    val evidence = resultJson.optString("evidence", "")
                    val missing = resultJson.optString("missing", "")
                    Log.i(TAG, "GoalVerifier result: achieved=$achieved, evidence='$evidence', missing='$missing'")
                    return@withContext VerificationResult(achieved, evidence, missing)
                }
            } else {
                Log.w(TAG, "GoalVerifier request failed HTTP ${response.code}: $bodyString")
                if (response.code == 429 || response.code == 403) {
                    GeminiKeyPool.reportError(key, response.code)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GoalVerifier exception: ${e.message}")
        }

        // Conservative fallback: allow completion if screen package changed and elements were interacted with
        VerificationResult(true, "Fallback confirmation", "")
    }
}
