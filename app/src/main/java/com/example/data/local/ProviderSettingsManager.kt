package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ProviderState(
    var providerName: String = "Gemini",
    var apiKeys: List<String> = emptyList(),
    var activeModel: String = "gemini-2.0-flash",
    var buildModel: String = "gemini-2.5-pro",
    var currentKeyIndex: Int = 0
)

object ProviderSettingsManager {
    private const val PREFS_NAME = "ProviderSettingsPrefs"
    private const val KEY_STATE = "provider_state_v6"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun sanitizeModelName(model: String): String {
        val trimmed = model.trim()
        if (trimmed.isBlank()) return "gemini-2.0-flash"
        return trimmed
    }

    private fun sanitizeBuildModelName(model: String): String {
        val trimmed = model.trim()
        if (trimmed.isBlank()) return "gemini-2.5-pro"
        return trimmed
    }

    fun hasCustomUserKey(): Boolean {
        val state = loadState()
        return state.apiKeys.any { it.isNotBlank() && !it.contains("MY_") }
    }

    fun loadState(): ProviderState {
        val jsonStr = prefs?.getString(KEY_STATE, null)
        if (jsonStr == null) {
            return ProviderState(
                providerName = "Gemini",
                activeModel = "gemini-2.0-flash",
                buildModel = "gemini-2.5-pro",
                apiKeys = emptyList()
            )
        }
        return try {
            val json = JSONObject(jsonStr)
            val parsedKeys = jsonArrayToList(json.optJSONArray("apiKeys")).filter { it.isNotBlank() }
            val rawModel = json.optString("activeModel", "gemini-2.0-flash")
            val rawBuildModel = json.optString("buildModel", "gemini-2.5-pro")
            var savedProvider = json.optString("providerName", "Gemini")

            // Clean only template dummy placeholders, preserve all real user API keys
            val cleanedKeys = parsedKeys.filter { k ->
                val trimmed = k.trim()
                !trimmed.contains("MY_")
            }

            // Normalization
            if (savedProvider.equals("OpenCode Zen", ignoreCase = true) || savedProvider.equals("OpenCode", ignoreCase = true)) {
                savedProvider = "OpenCode.ai"
            }

            ProviderState(
                providerName = savedProvider,
                apiKeys = cleanedKeys,
                activeModel = sanitizeModelName(rawModel),
                buildModel = sanitizeBuildModelName(rawBuildModel),
                currentKeyIndex = json.optInt("currentKeyIndex", 0)
            )
        } catch (e: Exception) {
            ProviderState(
                providerName = "Gemini",
                activeModel = "gemini-2.0-flash",
                buildModel = "gemini-2.5-pro",
                apiKeys = emptyList()
            )
        }
    }

    fun saveState(state: ProviderState) {
        val json = JSONObject().apply {
            put("providerName", state.providerName)
            put("apiKeys", listToJsonArray(state.apiKeys))
            put("activeModel", state.activeModel)
            put("buildModel", state.buildModel)
            put("currentKeyIndex", state.currentKeyIndex)
        }
        prefs?.edit()?.putString(KEY_STATE, json.toString())?.apply()
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        val list = mutableListOf<String>()
        if (array != null) {
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        }
        return list
    }

    private fun listToJsonArray(list: List<String>): JSONArray {
        val array = JSONArray()
        for (item in list) {
            array.put(item)
        }
        return array
    }
}
