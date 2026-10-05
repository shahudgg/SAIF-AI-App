package com.example.agent.brain

import android.util.Log
import com.example.agent.ActionExecutor
import com.example.agent.AgentCall
import com.example.agent.AgentOutcome
import com.example.agent.PlaybookStore
import com.example.agent.RateGovernor
import com.example.agent.ScreenSnapshot
import com.example.agent.StepRecord
import com.example.data.remote.GeminiKeyPool
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

object FunctionCallingBrain : AgentBrain {
    private const val TAG = "SAIF_AGENT"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val toolDeclarationsJson = JSONArray().apply {
        put(JSONObject().apply {
            put("name", "tap_element")
            put("description", "Tap a UI element by its index from the latest screen element list (preferred when target is in the list).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("index", JSONObject().put("type", "integer").put("description", "Element index from the list"))
                    put("intent", JSONObject().put("type", "string").put("description", "Why you are tapping this element"))
                })
                put("required", JSONArray().put("index"))
            })
        })
        put(JSONObject().apply {
            put("name", "click")
            put("description", "Click at normalized (x, y) coordinates (0-999) on the screenshot (for unlabeled icons, thumbnails, canvas).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("x", JSONObject().put("type", "integer").put("description", "X coordinate 0-999"))
                    put("y", JSONObject().put("type", "integer").put("description", "Y coordinate 0-999"))
                    put("intent", JSONObject().put("type", "string").put("description", "Purpose of the click"))
                })
                put("required", JSONArray().put("x").put("y"))
            })
        })
        put(JSONObject().apply {
            put("name", "type_into")
            put("description", "Type text into a focused or targeted input field.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("index", JSONObject().put("type", "integer").put("description", "Optional input field element index"))
                    put("text", JSONObject().put("type", "string").put("description", "Text to type"))
                    put("clear_first", JSONObject().put("type", "boolean").put("description", "Clear existing text first"))
                    put("press_enter", JSONObject().put("type", "boolean").put("description", "Press search/enter key on keyboard after typing"))
                    put("intent", JSONObject().put("type", "string").put("description", "Purpose of typing"))
                })
                put("required", JSONArray().put("text"))
            })
        })
        put(JSONObject().apply {
            put("name", "swipe")
            put("description", "Perform a human-like finger swipe across the screen (up = next Reel/Short or scroll further down).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("direction", JSONObject().put("type", "string").put("enum", JSONArray().put("up").put("down").put("left").put("right")))
                    put("distance", JSONObject().put("type", "string").put("enum", JSONArray().put("short").put("medium").put("long")))
                    put("intent", JSONObject().put("type", "string").put("description", "Reason for swipe"))
                })
                put("required", JSONArray().put("direction"))
            })
        })
        put(JSONObject().apply {
            put("name", "scroll_until")
            put("description", "Scroll in a direction until text appears on screen.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().put("type", "string").put("description", "Text to find"))
                    put("direction", JSONObject().put("type", "string").put("enum", JSONArray().put("down").put("up")))
                    put("max_swipes", JSONObject().put("type", "integer").put("description", "Max swipes to attempt (default 8)"))
                })
                put("required", JSONArray().put("text"))
            })
        })
        put(JSONObject().apply {
            put("name", "open_app")
            put("description", "Open an application by its common name or package.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("app_name", JSONObject().put("type", "string").put("description", "Name of the app (e.g. YouTube, WhatsApp)"))
                })
                put("required", JSONArray().put("app_name"))
            })
        })
        put(JSONObject().apply {
            put("name", "run_skill")
            put("description", "Run a deterministic app skill (whatsapp_send, youtube_search, system, instagram, etc.).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("name", JSONObject().put("type", "string").put("description", "Name of skill to run"))
                    put("args", JSONObject().put("type", "object").put("description", "Arguments for the skill"))
                })
                put("required", JSONArray().put("name"))
            })
        })
        put(JSONObject().apply {
            put("name", "press_system")
            put("description", "Execute system navigation (back, home, recents, notifications, quick_settings).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("button", JSONObject().put("type", "string").put("enum", JSONArray().put("back").put("home").put("recents").put("notifications").put("quick_settings")))
                })
                put("required", JSONArray().put("button"))
            })
        })
        put(JSONObject().apply {
            put("name", "speak")
            put("description", "Speak a short milestone update to the owner (<=12 words).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().put("type", "string").put("description", "Short update in natural Hinglish"))
                })
                put("required", JSONArray().put("text"))
            })
        })
        put(JSONObject().apply {
            put("name", "ask_user")
            put("description", "Ask the owner a question by voice when ambiguous and wait for their spoken answer.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("question", JSONObject().put("type", "string").put("description", "Question to ask the owner"))
                    put("options", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")))
                })
                put("required", JSONArray().put("question"))
            })
        })
        put(JSONObject().apply {
            put("name", "note")
            put("description", "Save a short memory note across steps.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().put("type", "string").put("description", "Note content"))
                })
                put("required", JSONArray().put("text"))
            })
        })
        put(JSONObject().apply {
            put("name", "finish")
            put("description", "Finish the task when the goal has been VERIFIED on screen.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("success", JSONObject().put("type", "boolean").put("description", "Whether goal was successfully achieved"))
                    put("summary", JSONObject().put("type", "string").put("description", "Spoken final result in Hinglish (<=2 sentences)"))
                    put("data", JSONObject().put("type", "object").put("description", "Optional extracted data (counts, names)"))
                })
                put("required", JSONArray().put("success").put("summary"))
            })
        })
        put(JSONObject().apply {
            put("name", "yield_to_user")
            put("description", "Hand control back to owner for safety (payment, UPI PIN, credentials, secure screen).")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("reason", JSONObject().put("type", "string").put("description", "Why manual user intervention is needed"))
                })
                put("required", JSONArray().put("reason"))
            })
        })
    }

    override suspend fun decide(
        task: String,
        sessionCtx: String,
        notes: String,
        history: List<StepRecord>,
        snapshot: ScreenSnapshot,
        thinkingLevel: String
    ): BrainDecision = withContext(Dispatchers.IO) {
        RateGovernor.acquire()

        val key = GeminiKeyPool.getNextKey()
        if (key.isNullOrBlank()) {
            return@withContext decideWithUniversalApi(task, sessionCtx, notes, history, snapshot)
        }

        val model = ModelResolver.resolve(ModelRole.AGENT)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        try {
            val root = JSONObject()

            // 1. System Prompt
            val sysPart = JSONObject().put("text", AgentPrompts.BRAIN_SYSTEM)
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(sysPart)))

            // 2. Tools
            val toolsArray = JSONArray().put(JSONObject().put("functionDeclarations", toolDeclarationsJson))
            root.put("tools", toolsArray)

            // 3. User Message Parts
            val parts = JSONArray()
            val textBuilder = StringBuilder()
            textBuilder.append("GOAL: ").append(task).append("\n")
            if (sessionCtx.isNotBlank()) textBuilder.append("SESSION: ").append(sessionCtx).append("\n")
            if (notes.isNotBlank()) textBuilder.append("NOTES: ").append(notes).append("\n")

            // Playbook hint for foreground app
            val playbook = PlaybookStore.getHint(snapshot.packageName)
            if (!playbook.isNullOrBlank()) {
                textBuilder.append("APP PLAYBOOK: ").append(playbook).append("\n")
            }

            textBuilder.append("HISTORY (last ").append(history.takeLast(6).size).append(" steps):\n")
            for (step in history.takeLast(6)) {
                textBuilder.append("- Step ${step.step}: ${step.call.name} -> ${if (step.result.ok) "OK" else "FAIL"}: ${step.result.detail}\n")
            }

            textBuilder.append("\nCURRENT SCREEN:\n")
            textBuilder.append(snapshot.toCompactText(4000))
            parts.put(JSONObject().put("text", textBuilder.toString()))

            // Add screenshot image
            if (!snapshot.screenshotJpegBase64.isNullOrBlank() && !snapshot.secure) {
                parts.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", snapshot.screenshotJpegBase64)
                    })
                })
            }

            val contents = JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", parts)
            })
            root.put("contents", contents)

            val genConfig = JSONObject().apply {
                put("temperature", if (thinkingLevel == "high") 0.4 else 0.1)
                put("maxOutputTokens", 2048)
            }
            root.put("generationConfig", genConfig)

            val request = Request.Builder()
                .url(url)
                .post(root.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                RateGovernor.reportStatus(key, response.code, response.header("Retry-After"))
                Log.w(TAG, "FunctionCallingBrain Gemini returned ${response.code}, falling back to universal API")
                return@withContext decideWithUniversalApi(task, sessionCtx, notes, history, snapshot)
            }

            val json = JSONObject(bodyString)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            val contentObj = candidate?.optJSONObject("content")
            val candidateParts = contentObj?.optJSONArray("parts")

            val calls = mutableListOf<AgentCall>()
            var thought = ""

            if (candidateParts != null) {
                for (i in 0 until candidateParts.length()) {
                    val part = candidateParts.getJSONObject(i)
                    if (part.has("text")) {
                        thought += part.getString("text") + " "
                    }
                    if (part.has("functionCall")) {
                        val fc = part.getJSONObject("functionCall")
                        val callName = fc.getString("name")
                        val callArgs = fc.optJSONObject("args") ?: JSONObject()
                        val callId = "call_${System.currentTimeMillis()}_$i"
                        val intent = callArgs.optString("intent", "")
                        calls.add(AgentCall(callId, callName, callArgs, intent))
                    }
                }
            }

            // Check if finish or yield
            val finishCall = calls.firstOrNull { it.name == "finish" || it.name == "yield_to_user" }
            if (finishCall != null) {
                val success = finishCall.name == "finish" && finishCall.arguments.optBoolean("success", true)
                val summary = finishCall.arguments.optString("summary", finishCall.arguments.optString("reason", "Task finished"))
                return@withContext BrainDecision(
                    calls = calls,
                    thought = thought.trim(),
                    finished = true,
                    outcome = AgentOutcome(success, summary, history.size + 1, 0, data = finishCall.arguments.opt("data"))
                )
            }

            BrainDecision(calls = calls, thought = thought.trim())
        } catch (e: Exception) {
            Log.w(TAG, "FunctionCallingBrain exception: ${e.message}, falling back to universal API")
            try {
                decideWithUniversalApi(task, sessionCtx, notes, history, snapshot)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Universal API fallback failed: ${fallbackEx.message}")
                BrainDecision(
                    calls = listOf(AgentCall("err", "finish", JSONObject().put("success", false).put("summary", "Agent error: ${e.message}"))),
                    finished = true
                )
            }
        }
    }

    suspend fun decideWithUniversalApi(
        task: String,
        sessionCtx: String,
        notes: String,
        history: List<StepRecord>,
        snapshot: ScreenSnapshot
    ): BrainDecision = withContext(Dispatchers.IO) {
        val prompt = StringBuilder()
        prompt.append("You are the autonomous Android phone agent brain.\n")
        prompt.append("GOAL: ").append(task).append("\n")
        if (sessionCtx.isNotBlank()) prompt.append("SESSION: ").append(sessionCtx).append("\n")
        if (notes.isNotBlank()) prompt.append("NOTES: ").append(notes).append("\n")
        val playbook = PlaybookStore.getHint(snapshot.packageName)
        if (!playbook.isNullOrBlank()) prompt.append("APP PLAYBOOK: ").append(playbook).append("\n")

        prompt.append("\nHISTORY:\n")
        for (step in history.takeLast(4)) {
            prompt.append("- Step ${step.step}: ${step.call.name} -> ${if (step.result.ok) "OK" else "FAIL"}: ${step.result.detail}\n")
        }

        prompt.append("\nCURRENT SCREEN ELEMENTS:\n")
        prompt.append(snapshot.toCompactText(2500))

        prompt.append("""

AVAILABLE ACTIONS:
1. tap_element: {"index": <int>, "intent": "<why>"}
2. click: {"x": <0-999>, "y": <0-999>, "intent": "<why>"}
3. type_into: {"text": "<text>", "index": <int optional>, "clear_first": <bool optional>, "press_enter": <bool optional>, "intent": "<why>"}
4. swipe: {"direction": "up"|"down"|"left"|"right", "distance": "short"|"medium"|"long"}
5. scroll_until: {"text": "<query>", "direction": "down"|"up"}
6. open_app: {"app_name_or_package": "<name or package>"}
7. key_event: {"key": "back"|"home"|"recents"|"notifications"}
8. wait_for: {"condition": "idle"|"package"|"text", "target": "<str>", "timeout_ms": <int>}
9. finish: {"success": true|false, "summary": "<spoken result in Hinglish/Urdu/English, max 2 sentences>"}
10. yield_to_user: {"reason": "<why user must take over>"}

CRITICAL: Return ONLY a valid JSON object matching this schema:
{
  "name": "<action_name>",
  "parameters": { ... }
}
Do not include any conversational text or markdown code fence outside the JSON.
        """.trimIndent())

        val apiResult = com.example.data.remote.AIApiUtility.executeWithFallback(
            prompt = prompt.toString(),
            mode = "general"
        )

        val rawText = apiResult.getOrNull() ?: ""
        if (rawText.isBlank()) {
            return@withContext BrainDecision(
                calls = listOf(AgentCall("call_1", "finish", JSONObject().put("success", false).put("summary", "AI service returned no response"))),
                finished = true,
                outcome = AgentOutcome(false, "No response from AI service", history.size + 1, 0)
            )
        }

        try {
            val jsonClean = if (rawText.contains("{") && rawText.contains("}")) {
                val start = rawText.indexOf('{')
                val end = rawText.lastIndexOf('}') + 1
                rawText.substring(start, end)
            } else {
                rawText.trim()
            }

            val actionObj = JSONObject(jsonClean)
            val callName = actionObj.optString("name", "finish")
            val callArgs = actionObj.optJSONObject("parameters") ?: JSONObject()
            val callId = "call_${System.currentTimeMillis()}"
            val intent = callArgs.optString("intent", "")

            val call = AgentCall(callId, callName, callArgs, intent)

            if (callName == "finish" || callName == "yield_to_user") {
                val success = callName == "finish" && callArgs.optBoolean("success", true)
                val summary = callArgs.optString("summary", callArgs.optString("reason", "Task finished"))
                return@withContext BrainDecision(
                    calls = listOf(call),
                    finished = true,
                    outcome = AgentOutcome(success, summary, history.size + 1, 0, data = callArgs.opt("data"))
                )
            }

            BrainDecision(calls = listOf(call))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse universal AI tool call JSON: ${e.message}, raw: $rawText")
            BrainDecision(
                calls = listOf(AgentCall("call_1", "finish", JSONObject().put("success", true).put("summary", rawText.take(150)))),
                finished = true,
                outcome = AgentOutcome(true, rawText.take(150), history.size + 1, 0)
            )
        }
    }
}
