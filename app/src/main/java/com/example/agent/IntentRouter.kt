package com.example.agent

import android.util.Log
import com.example.agent.brain.AgentPrompts
import com.example.data.remote.GeminiKeyPool
import com.example.data.remote.ModelResolver
import com.example.data.remote.ModelRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class RouteDestination {
    TASK,
    CHAT,
    CONTROL
}

data class RouteDecision(
    val route: RouteDestination,
    val goal: String,
    val controlCmd: String? = null
)

object IntentRouter {
    private const val TAG = "SAIF_AGENT"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val STOP_KEYWORDS = listOf("stop", "ruko", "chup", "cancel", "bas", "rok do", "khatam karo", "band karo")

    suspend fun route(utterance: String, isTaskRunning: Boolean): RouteDecision {
        val clean = utterance.trim().lowercase()

        // 0. If UserDialogBridge is actively waiting for an answer to a question (e.g. contact selection)
        if (UserDialogBridge.isAwaitingAnswer()) {
            val handled = UserDialogBridge.provideAnswer(utterance)
            if (handled) {
                return RouteDecision(RouteDestination.CHAT, utterance)
            }
        }

        // 1. Zero-latency STOP check
        for (stopWord in STOP_KEYWORDS) {
            if (clean == stopWord || clean.startsWith("$stopWord ") || clean.endsWith(" $stopWord")) {
                return RouteDecision(RouteDestination.CONTROL, utterance, controlCmd = "stop")
            }
        }

        if (clean.contains("kya kar rahe ho") || clean.contains("status")) {
            return RouteDecision(RouteDestination.CONTROL, utterance, controlCmd = "status")
        }

        // 2. Fast LLM Classification
        val llmResult = withTimeoutOrNull(2000L) {
            classifyWithFastModel(utterance, isTaskRunning)
        }

        if (llmResult != null) {
            return llmResult
        }

        // 3. Heuristic fallback
        return heuristicClassify(utterance)
    }

    private suspend fun classifyWithFastModel(utterance: String, isTaskRunning: Boolean): RouteDecision? = withContext(Dispatchers.IO) {
        val key = GeminiKeyPool.getNextKey() ?: return@withContext null
        val model = ModelResolver.resolve(ModelRole.ROUTER)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        try {
            val root = JSONObject()
            val sysText = AgentPrompts.ROUTER_SYSTEM.replace("{{RUNNING_TASK_OR_NONE}}", if (isTaskRunning) "YES" else "NONE")
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysText))))

            val contents = JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", utterance)))
            })
            root.put("contents", contents)

            val genConfig = JSONObject().apply {
                put("temperature", 0.0)
                put("maxOutputTokens", 256)
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
                    val resJson = JSONObject(cleanJson)
                    val routeStr = resJson.optString("route", "chat").lowercase()
                    val goal = resJson.optString("goal", utterance)
                    val control = resJson.optString("control", null)

                    val destination = when (routeStr) {
                        "task" -> RouteDestination.TASK
                        "control" -> RouteDestination.CONTROL
                        else -> RouteDestination.CHAT
                    }
                    return@withContext RouteDecision(destination, goal, control)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fast router failed, using heuristic: ${e.message}")
        }
        null
    }

    private fun heuristicClassify(utterance: String): RouteDecision {
        val lower = utterance.lowercase()
        val taskVerbs = listOf(
            "kholo", "open", "chalao", "play", "bhejo", "send", "call", "dial",
            "search", "khojo", "like", "subscribe", "scroll", "update", "torch",
            "alarm", "timer", "volume", "batao", "screen", "instagram", "whatsapp",
            "youtube", "capcut", "location"
        )

        for (verb in taskVerbs) {
            if (lower.contains(verb)) {
                return RouteDecision(RouteDestination.TASK, utterance)
            }
        }

        return RouteDecision(RouteDestination.CHAT, utterance)
    }
}
