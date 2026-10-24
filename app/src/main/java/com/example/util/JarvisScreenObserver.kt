package com.example.util

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

data class JarvisScreenSignature(
    val packageName: String,
    val windowTitle: String,
    val elementCount: Int,
    val clickableCount: Int,
    val editableCount: Int,
    val contentHash: Long,
    val visibleTexts: List<String>,
    val humanReadableLocation: String
)

/**
 * JarvisScreenObserver:
 * Real-time screen context engine. Inspects active window hierarchy through SaifAccessibilityService
 * so the assistant always knows its current location ("abhi wo kaha hai screen par")
 * and what content is visible to the user across any app.
 */
object JarvisScreenObserver {
    private const val TAG = "JarvisScreenObserver"

    /**
     * Inspects active screen and calculates signature and human-readable context.
     */
    fun inspectCurrentScreen(): JarvisScreenSignature {
        val service = SaifAccessibilityService.instance
        if (service == null) {
            return JarvisScreenSignature(
                packageName = "",
                windowTitle = "Service Inactive",
                elementCount = 0,
                clickableCount = 0,
                editableCount = 0,
                contentHash = 0L,
                visibleTexts = emptyList(),
                humanReadableLocation = "Screen observation unavailable (Accessibility Service not enabled)"
            )
        }

        val root: AccessibilityNodeInfo? = try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Error getting rootInActiveWindow: ${e.message}")
            null
        }

        if (root == null) {
            return JarvisScreenSignature(
                packageName = "",
                windowTitle = "No Active Window",
                elementCount = 0,
                clickableCount = 0,
                editableCount = 0,
                contentHash = 0L,
                visibleTexts = emptyList(),
                humanReadableLocation = "Home Screen or System Transition"
            )
        }

        val pkg = root.packageName?.toString() ?: ""
        val texts = mutableListOf<String>()
        var elementCount = 0
        var clickables = 0
        var editables = 0
        var hash = 1125899906842597L

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val tempRect = Rect()

        while (queue.isNotEmpty() && elementCount < 350) {
            val node = queue.removeFirst()
            elementCount++

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val isClickable = node.isClickable
            val isEditable = node.isEditable

            if (isClickable) clickables++
            if (isEditable) editables++

            node.getBoundsInScreen(tempRect)

            if (text.isNotEmpty() && texts.size < 25 && !texts.contains(text)) {
                texts.add(text)
            } else if (desc.isNotEmpty() && texts.size < 25 && !texts.contains(desc)) {
                texts.add(desc)
            }

            hash = 31 * hash + text.hashCode()
            hash = 31 * hash + desc.hashCode()
            hash = 31 * hash + tempRect.centerX()
            hash = 31 * hash + tempRect.centerY()

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }

        val location = resolveAppFriendlyName(pkg, texts)

        return JarvisScreenSignature(
            packageName = pkg,
            windowTitle = location,
            elementCount = elementCount,
            clickableCount = clickables,
            editableCount = editables,
            contentHash = hash,
            visibleTexts = texts,
            humanReadableLocation = location
        )
    }

    /**
     * Resolves human-friendly app and activity context name.
     */
    private fun resolveAppFriendlyName(pkg: String, texts: List<String>): String {
        return when {
            pkg.contains("youtube", ignoreCase = true) -> "YouTube (${texts.take(2).joinToString(" • ")})"
            pkg.contains("whatsapp", ignoreCase = true) -> "WhatsApp (${texts.take(2).joinToString(" • ")})"
            pkg.contains("instagram", ignoreCase = true) -> "Instagram"
            pkg.contains("chrome", ignoreCase = true) -> "Google Chrome (${texts.firstOrNull() ?: "Browsing"})"
            pkg.contains("camera", ignoreCase = true) -> "Camera Viewfinder"
            pkg.contains("settings", ignoreCase = true) -> "Device Settings"
            pkg.contains("dialer", ignoreCase = true) || pkg.contains("telecom", ignoreCase = true) -> "Phone Dialer"
            pkg.contains("spotify", ignoreCase = true) -> "Spotify Player"
            pkg.contains("saifai", ignoreCase = true) || pkg.contains("example", ignoreCase = true) -> "SAIF AI Assistant"
            pkg.isBlank() -> "Home Screen"
            else -> {
                val simpleName = pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                if (texts.isNotEmpty()) "$simpleName (${texts.first()})" else simpleName
            }
        }
    }

    /**
     * Generates a context block suitable for prompt injection into any AI model.
     */
    fun getCurrentScreenContextSummary(): String {
        val sig = inspectCurrentScreen()
        if (sig.packageName.isBlank()) {
            return "Current Screen: Home Screen / Idle"
        }
        val textSnippet = if (sig.visibleTexts.isNotEmpty()) {
            "Visible on screen: \"${sig.visibleTexts.take(8).joinToString("\", \"")}\""
        } else {
            "No textual labels visible"
        }
        return """
            [CURRENT DEVICE SCREEN CONTEXT]
            Active App: ${sig.packageName}
            Context: ${sig.humanReadableLocation}
            Interactive Elements: ${sig.elementCount} elements (${sig.clickableCount} clickable, ${sig.editableCount} inputs)
            $textSnippet
        """.trimIndent()
    }
}
