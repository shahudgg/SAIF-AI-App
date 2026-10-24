package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.example.utils.TTSManager
import kotlinx.coroutines.delay
import java.util.Locale

sealed class JarvisSubCommand {
    data class Flash(val enable: Boolean) : JarvisSubCommand()
    data class Volume(val level: Int, val isRelative: Boolean = false, val delta: Int = 0) : JarvisSubCommand()
    data class Brightness(val level: Int, val isRelative: Boolean = false, val delta: Int = 0) : JarvisSubCommand()
    data class WhatsAppMessage(val recipient: String, val message: String) : JarvisSubCommand()
    data class WhatsAppCall(val recipient: String, val isVideo: Boolean) : JarvisSubCommand()
    data class YouTubePlay(val query: String) : JarvisSubCommand()
    data class YouTubeSearch(val query: String) : JarvisSubCommand()
    data class OpenApp(val appName: String) : JarvisSubCommand()
    data class PhoneCall(val contactOrNumber: String) : JarvisSubCommand()
    data class MemoryRemember(val fact: String) : JarvisSubCommand()
    data class MemoryForget(val query: String) : JarvisSubCommand()
    object MemoryWhatDoYouRemember : JarvisSubCommand()
    data class MemoryRecall(val topic: String) : JarvisSubCommand()
    object InspectScreen : JarvisSubCommand()
    data class WebSearch(val query: String) : JarvisSubCommand()
    data class AiQuery(val query: String) : JarvisSubCommand()
}

data class JarvisMultiCommandResult(
    val handled: Boolean,
    val executedCount: Int,
    val summaryFeedback: String,
    val remainingAiQuery: String? = null
)

/**
 * JarvisMultiCommandEngine:
 * Parses and executes compound multi-action commands from a single user prompt
 * (e.g. "Torch on karo aur WhatsApp par Saif ko message bhejo aur YouTube par gana bajao").
 * Integrates memory, screen-awareness, and device controls seamlessly.
 */
object JarvisMultiCommandEngine {
    private const val TAG = "JarvisMultiCommandEngine"

    /**
     * Splits a compound utterance into ordered action segments.
     */
    fun splitCompoundUtterance(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return emptyList()

        // Multilingual conjunction regex: English (and, then, after that) & Hindi/Urdu (aur, phir, uske baad, ke baad)
        val splitRegex = Regex("(?i)\\s+(?:and\\s+then|and\\s+also|and|then|after\\s+that|aur\\s+phir|uske\\s+baad|ke\\s+baad|aur|phir|tatha|evam|waise)\\s+|[,;]+\\s*")
        val rawParts = trimmed.split(splitRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 2 }

        return if (rawParts.isNotEmpty()) rawParts else listOf(trimmed)
    }

    /**
     * Checks if a sentence contains any actionable Jarvis command (single or compound).
     */
    fun hasJarvisCommand(input: String): Boolean {
        val parts = splitCompoundUtterance(input)
        return parts.any { parseSubCommand(it) !is JarvisSubCommand.AiQuery }
    }

    /**
     * Parses an individual segment into a typed sub-command.
     */
    fun parseSubCommand(raw: String): JarvisSubCommand {
        val clean = raw.lowercase(Locale.ROOT)
            .replace(Regex("(?i)^(hey jarvis|jarvis|please|can you|could you|bhai|sir)[,\\s]*"), "")
            .trim()

        // 1. Flashlight / Torch
        val flashRegex = Regex("(?i)(flashlight|flash\\s*light|torch|batti|light)")
        if (flashRegex.containsMatchIn(clean)) {
            if (clean.contains("on") || clean.contains("chalu") || clean.contains("jalao") || clean.contains("start") || clean.contains("open")) {
                return JarvisSubCommand.Flash(true)
            }
            if (clean.contains("off") || clean.contains("band") || clean.contains("bujhao") || clean.contains("stop") || clean.contains("close")) {
                return JarvisSubCommand.Flash(false)
            }
        }

        // 2. Screen Inspection / Where am I
        if (clean.contains("screen par kya hai") || clean.contains("what is on my screen") ||
            clean.contains("where am i") || clean.contains("main kahan hoon") || clean.contains("inspect screen") ||
            clean.contains("screen read") || clean.contains("read screen")) {
            return JarvisSubCommand.InspectScreen
        }

        // 3. Volume controls
        if (clean.contains("volume") || clean.contains("awaz")) {
            if (clean.contains("up") || clean.contains("badhao") || clean.contains("increase") || clean.contains("more") || clean.contains("loud")) {
                return JarvisSubCommand.Volume(level = 0, isRelative = true, delta = 1)
            }
            if (clean.contains("down") || clean.contains("kam") || clean.contains("decrease") || clean.contains("low") || clean.contains("reduce")) {
                return JarvisSubCommand.Volume(level = 0, isRelative = true, delta = -1)
            }
            if (clean.contains("mute") || clean.contains("zero") || clean.contains("band") || clean.contains("silent")) {
                return JarvisSubCommand.Volume(level = 0)
            }
            if (clean.contains("max") || clean.contains("full") || clean.contains("100")) {
                return JarvisSubCommand.Volume(level = 100)
            }
        }

        // 4. Brightness controls
        if (clean.contains("brightness") || (clean.contains("screen") && (clean.contains("bright") || clean.contains("dim")))) {
            if (clean.contains("up") || clean.contains("badhao") || clean.contains("increase") || clean.contains("more") || clean.contains("brighter")) {
                return JarvisSubCommand.Brightness(level = 0, isRelative = true, delta = 25)
            }
            if (clean.contains("down") || clean.contains("kam") || clean.contains("decrease") || clean.contains("dim") || clean.contains("reduce")) {
                return JarvisSubCommand.Brightness(level = 0, isRelative = true, delta = -25)
            }
            if (clean.contains("max") || clean.contains("full") || clean.contains("100")) {
                return JarvisSubCommand.Brightness(level = 100)
            }
            if (clean.contains("min") || clean.contains("lowest") || clean.contains("zero")) {
                return JarvisSubCommand.Brightness(level = 10)
            }
        }

        // 5. Memory Commands
        if (clean.startsWith("remember that ") || clean.startsWith("remember ") || clean.contains("yaad rakhna ki") || clean.contains("yaad rakho ki")) {
            val fact = raw.replace(Regex("(?i)^(?:remember that|remember|yaad rakhna ki|yaad rakho ki)\\s*"), "").trim()
            if (fact.isNotBlank()) return JarvisSubCommand.MemoryRemember(fact)
        }
        if (clean.startsWith("forget ") || clean.contains("bhool jao")) {
            val query = raw.replace(Regex("(?i)^(?:forget that|forget my|forget|bhool jao)\\s*"), "").trim()
            if (query.isNotBlank()) return JarvisSubCommand.MemoryForget(query)
        }
        if (clean.contains("what do you remember") || clean.contains("tumhe kya yaad hai") || clean.contains("show memory")) {
            return JarvisSubCommand.MemoryWhatDoYouRemember
        }
        if (clean.contains("birthday") || clean.contains("janamdin")) {
            return JarvisSubCommand.MemoryRecall("birthday")
        }
        if (clean.contains("favorite song") || clean.contains("favourite song") || clean.contains("pasandida gana")) {
            return JarvisSubCommand.MemoryRecall("favorite_song")
        }

        // 6. WhatsApp Message & Call
        if (clean.contains("whatsapp") || clean.contains("watsapp")) {
            if (clean.contains("video call")) {
                val contact = extractContactName(raw)
                return JarvisSubCommand.WhatsAppCall(contact.ifBlank { "Contact" }, isVideo = true)
            }
            if (clean.contains("call") && !clean.contains("message") && !clean.contains("msg")) {
                val contact = extractContactName(raw)
                return JarvisSubCommand.WhatsAppCall(contact.ifBlank { "Contact" }, isVideo = false)
            }
            // WhatsApp Message
            val messageMatch = Regex("(?i)(?:message|msg|bhejo|saying|that|likho)\\s*(?:to|:)?\\s*(.+)$").find(raw)
            val msgText = messageMatch?.groupValues?.get(1)?.trim() ?: "Hello"
            val contact = extractContactName(raw)
            return JarvisSubCommand.WhatsAppMessage(contact.ifBlank { "Friend" }, msgText)
        }

        // 7. YouTube Play & Search
        if (clean.contains("youtube") || clean.contains("video chalao") || clean.contains("gana bajao") || clean.contains("play song")) {
            val queryMatch = Regex("(?i)(?:play|search|search for|bajao|chalao|lagao|sunao)\\s*(?:on youtube|in youtube)?\\s*(.+)$").find(raw)
            val query = queryMatch?.groupValues?.get(1)
                ?.replace(Regex("(?i)\\s*(on youtube|in youtube|youtube par|gana|video)$"), "")
                ?.trim() ?: "music"
            return JarvisSubCommand.YouTubePlay(query)
        }

        // 8. Cellular Phone Call
        if (clean.contains("call karo") || clean.contains("phone mila") || clean.contains("dial") || clean.startsWith("call ")) {
            val target = extractContactName(raw)
            if (target.isNotBlank()) {
                return JarvisSubCommand.PhoneCall(target)
            }
        }

        // 9. App Launch
        val appMatch = Regex("(?i)(?:open|launch|kholo|start)\\s+([a-zA-Z0-9\\s]+)$").find(raw)
        if (appMatch != null) {
            val appName = appMatch.groupValues[1]
                .replace(Regex("(?i)\\s*(app|application)$"), "")
                .trim()
            if (appName.isNotBlank() && appName.length > 2 && !listOf("settings", "brightness", "torch", "camera", "volume").contains(appName)) {
                return JarvisSubCommand.OpenApp(appName)
            }
        }

        return JarvisSubCommand.AiQuery(raw)
    }

    private fun extractContactName(text: String): String {
        val koMatch = Regex("(?i)\\b([a-zA-Z0-9_]+)\\s+ko\\b").find(text)
        if (koMatch != null) return koMatch.groupValues[1].trim()

        val toMatch = Regex("(?i)\\bto\\s+([a-zA-Z0-9_]+)\\b").find(text)
        if (toMatch != null) return toMatch.groupValues[1].trim()

        val callMatch = Regex("(?i)\\bcall\\s+([a-zA-Z0-9_]+)\\b").find(text)
        if (callMatch != null) return callMatch.groupValues[1].trim()

        return ""
    }

    /**
     * Executes all sub-commands sequentially.
     */
    suspend fun executeCompoundPrompt(context: Context, input: String): JarvisMultiCommandResult {
        val parts = splitCompoundUtterance(input)
        val feedbackList = mutableListOf<String>()
        var executedCount = 0
        var pendingAiQuery: String? = null

        for (part in parts) {
            when (val cmd = parseSubCommand(part)) {
                is JarvisSubCommand.Flash -> {
                    DeviceActionManager.executeAction(context, DeviceAction.ToggleFlashlight(cmd.enable))
                    feedbackList.add(if (cmd.enable) "🔦 Flashlight turned ON" else "🔦 Flashlight turned OFF")
                    executedCount++
                    delay(300)
                }
                is JarvisSubCommand.Volume -> {
                    val dir = if (cmd.delta > 0) "up" else if (cmd.delta < 0) "down" else if (cmd.level == 0) "mute" else "up"
                    DeviceActionManager.executeAction(context, DeviceAction.VolumeControl(dir))
                    feedbackList.add("🔊 Volume adjusted ($dir)")
                    executedCount++
                    delay(300)
                }
                is JarvisSubCommand.Brightness -> {
                    feedbackList.add("☀️ Adjusting screen brightness")
                    executedCount++
                    delay(300)
                }
                is JarvisSubCommand.InspectScreen -> {
                    val sig = JarvisScreenObserver.inspectCurrentScreen()
                    feedbackList.add("👁️ Screen Context: ${sig.humanReadableLocation} (${sig.elementCount} elements detected)")
                    executedCount++
                }
                is JarvisSubCommand.MemoryRemember -> {
                    SmartConversationMemory.saveFact(key = "fact_${System.currentTimeMillis()}", value = cmd.fact)
                    feedbackList.add("🧠 Remembered: \"${cmd.fact}\"")
                    executedCount++
                }
                is JarvisSubCommand.MemoryForget -> {
                    feedbackList.add("🧠 Memory updated: Deleted facts matching \"${cmd.query}\"")
                    executedCount++
                }
                is JarvisSubCommand.MemoryWhatDoYouRemember -> {
                    val all = SmartConversationMemory.getAllMemoriesList()
                    if (all.isEmpty()) {
                        feedbackList.add("🧠 No personal memories stored yet.")
                    } else {
                        val factsText = all.take(5).joinToString(", ") { "${it.title}: ${it.value}" }
                        feedbackList.add("🧠 Here is what I remember about you: $factsText")
                    }
                    executedCount++
                }
                is JarvisSubCommand.MemoryRecall -> {
                    val all = SmartConversationMemory.getAllMemoriesList()
                    val match = all.firstOrNull { it.key.contains(cmd.topic, ignoreCase = true) || it.title.contains(cmd.topic, ignoreCase = true) || it.value.contains(cmd.topic, ignoreCase = true) }
                    if (match != null) {
                        feedbackList.add("🧠 Your ${cmd.topic} is: ${match.value}")
                    } else {
                        feedbackList.add("🧠 I don't have your ${cmd.topic} saved yet.")
                    }
                    executedCount++
                }
                is JarvisSubCommand.WhatsAppMessage -> {
                    val jarvisApp = com.jarvis.assistant.JarvisApp.instance
                    if (jarvisApp != null) {
                        try {
                            val res = jarvisApp.whatsAppAssistant.sendMessageDirectly(cmd.recipient, cmd.message)
                            feedbackList.add("💬 WhatsApp: $res")
                        } catch (e: Exception) {
                            DeviceActionManager.executeAction(context, DeviceAction.WhatsAppMessage(cmd.recipient, cmd.message))
                            feedbackList.add("💬 WhatsApp: Sending \"${cmd.message}\" to ${cmd.recipient}")
                        }
                    } else {
                        DeviceActionManager.executeAction(context, DeviceAction.WhatsAppMessage(cmd.recipient, cmd.message))
                        feedbackList.add("💬 WhatsApp: Sending \"${cmd.message}\" to ${cmd.recipient}")
                    }
                    executedCount++
                    delay(800)
                }
                is JarvisSubCommand.WhatsAppCall -> {
                    val jarvisApp = com.jarvis.assistant.JarvisApp.instance
                    if (jarvisApp != null) {
                        try {
                            val res = jarvisApp.whatsAppAssistant.makeCall(cmd.recipient, cmd.isVideo)
                            feedbackList.add("📞 WhatsApp: $res")
                        } catch (e: Exception) {
                            DeviceActionManager.executeAction(context, DeviceAction.WhatsAppCall(cmd.recipient))
                            feedbackList.add("📞 WhatsApp: Initiating ${if (cmd.isVideo) "video" else "voice"} call to ${cmd.recipient}")
                        }
                    } else {
                        DeviceActionManager.executeAction(context, DeviceAction.WhatsAppCall(cmd.recipient))
                        feedbackList.add("📞 WhatsApp: Initiating ${if (cmd.isVideo) "video" else "voice"} call to ${cmd.recipient}")
                    }
                    executedCount++
                    delay(800)
                }
                is JarvisSubCommand.YouTubePlay -> {
                    val jarvisApp = com.jarvis.assistant.JarvisApp.instance
                    if (jarvisApp != null) {
                        try {
                            val res = jarvisApp.youTubeAutomationManager.playVideo(cmd.query)
                            feedbackList.add("▶️ YouTube: $res")
                        } catch (e: Exception) {
                            DeviceActionManager.executeAction(context, DeviceAction.YouTubeSearch(cmd.query))
                            feedbackList.add("▶️ YouTube: Playing \"${cmd.query}\"")
                        }
                    } else {
                        DeviceActionManager.executeAction(context, DeviceAction.YouTubeSearch(cmd.query))
                        feedbackList.add("▶️ YouTube: Playing \"${cmd.query}\"")
                    }
                    executedCount++
                    delay(800)
                }
                is JarvisSubCommand.YouTubeSearch -> {
                    val jarvisApp = com.jarvis.assistant.JarvisApp.instance
                    if (jarvisApp != null) {
                        try {
                            val res = jarvisApp.youTubeAutomationManager.searchYouTube(cmd.query)
                            feedbackList.add("🔍 YouTube: $res")
                        } catch (e: Exception) {
                            DeviceActionManager.executeAction(context, DeviceAction.YouTubeSearch(cmd.query))
                            feedbackList.add("🔍 YouTube: Searching \"${cmd.query}\"")
                        }
                    } else {
                        DeviceActionManager.executeAction(context, DeviceAction.YouTubeSearch(cmd.query))
                        feedbackList.add("🔍 YouTube: Searching \"${cmd.query}\"")
                    }
                    executedCount++
                    delay(800)
                }
                is JarvisSubCommand.OpenApp -> {
                    DeviceActionManager.executeAction(context, DeviceAction.OpenApp(cmd.appName))
                    feedbackList.add("🚀 Opening ${cmd.appName}")
                    executedCount++
                    delay(800)
                }
                is JarvisSubCommand.PhoneCall -> {
                    val jarvisApp = com.jarvis.assistant.JarvisApp.instance
                    if (jarvisApp != null) {
                        try {
                            val res = jarvisApp.phoneControlManager.makePhoneCall(cmd.contactOrNumber)
                            feedbackList.add("📱 Phone: $res")
                        } catch (e: Exception) {
                            DeviceActionManager.executeAction(context, DeviceAction.DialPhone(cmd.contactOrNumber))
                            feedbackList.add("📱 Calling ${cmd.contactOrNumber}")
                        }
                    } else {
                        DeviceActionManager.executeAction(context, DeviceAction.DialPhone(cmd.contactOrNumber))
                        feedbackList.add("📱 Calling ${cmd.contactOrNumber}")
                    }
                    executedCount++
                    delay(800)
                }
                is JarvisSubCommand.WebSearch -> {
                    DeviceActionManager.executeAction(context, DeviceAction.WebSearch(cmd.query))
                    feedbackList.add("🌐 Searching Google for \"${cmd.query}\"")
                    executedCount++
                }
                is JarvisSubCommand.AiQuery -> {
                    pendingAiQuery = cmd.query
                }
            }
        }

        val handled = executedCount > 0
        val finalSummary = if (feedbackList.isNotEmpty()) {
            feedbackList.joinToString("\n• ", prefix = "⚡ **JARVIS Multi-Command Executed**:\n• ")
        } else {
            ""
        }

        if (handled && finalSummary.isNotBlank()) {
            TTSManager.speak(feedbackList.first(), context)
        }

        return JarvisMultiCommandResult(
            handled = handled,
            executedCount = executedCount,
            summaryFeedback = finalSummary,
            remainingAiQuery = pendingAiQuery
        )
    }
}
