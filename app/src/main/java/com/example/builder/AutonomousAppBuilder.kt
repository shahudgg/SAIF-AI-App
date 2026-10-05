package com.example.builder

import android.content.Context
import android.util.Log
import com.example.compiler.CompilerErrorInfo
import com.example.data.remote.AIApiUtility
import com.example.ui.components.CodeEditorViewModel
import com.example.ui.components.ProjectData
import com.example.util.ProjectChatHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Autonomous Full-Stack App Builder Engine for SAIF AI Studio.
 * Generates and edits complete, self-contained, interactive HTML5/CSS3/JS mobile applications
 * directly executing in Android WebView (base_runner container).
 * Strictly operates without hardcoded mock templates or silent fallbacks.
 */
data class AutonomousBuildResult(
    val isSuccess: Boolean,
    val summary: String,
    val updatedFiles: List<String>,
    val rawResponse: String = "",
    val error: String? = null
)

object AutonomousAppBuilder {
    private const val TAG = "AutonomousAppBuilder"

    const val BUILDER_SYSTEM_PROMPT = """You are Saif AI Build, a world-class product designer and senior front-end engineer. The user gives a short or vague idea (English, Hindi or Hinglish). Turn it into a complete, working, beautiful app. Never ask questions; fill the gaps like a senior product team would.

THINK FIRST: work out what the user really wants, then decide the purpose, features, screens and a design direction unique to THIS idea. Never reuse a generic template: a different idea must get a different layout, palette, typography, motion and feature set.

REAL FUNCTIONALITY: working state, interactions, validation, empty states, realistic sample data. Persist with localStorage wrapped in try/catch with an in-memory fallback.

DESIGN: premium modern UI, strong hierarchy, consistent spacing, a curated palette (avoid default blue-purple gradients), 1-2 Google Fonts. It runs inside an Android WebView: mobile-first, touch-friendly (44px targets), 100dvh layouts, safe-area padding, no hover-only interactions, no alert/confirm/prompt (use in-page modals and toasts). Use inline SVG, CSS shapes or emoji instead of external images.

ANIMATION (always high): staggered entrances, scroll reveals, press micro-interactions, animated screen transitions, animated numbers and charts, subtle moving backgrounds. 60fps via transform/opacity; respect prefers-reduced-motion.

OUTPUT: ONLY the raw HTML document starting with <!DOCTYPE html>. Put <!-- saif-ai-build --> right after <head>. All CSS/JS inline; external libraries only as classic <script> tags from a CDN (no ES-module imports, no local fetch), only when useful. No markdown, no commentary, never truncate or write "rest of code here". It must run as-is with zero console errors.

EDITING: if the input has CURRENT_CODE and CHANGE_REQUEST, apply exactly that change, keep everything else intact, and return the FULL updated HTML."""

    /**
     * Executes the autonomous app builder pipeline.
     */
    suspend fun buildOrModifyApp(
        context: Context,
        project: ProjectData,
        prompt: String,
        editorViewModel: CodeEditorViewModel? = null,
        isGlitchFix: Boolean = false,
        errorInfo: CompilerErrorInfo? = null,
        base64Images: List<String> = emptyList(),
        onProgressUpdate: suspend (statusText: String, stage: Int) -> Unit = { _, _ -> }
    ): AutonomousBuildResult = withContext(Dispatchers.IO) {
        val sanitizedAppName = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Project" }
        val projectDir = File(context.filesDir, "projects/$sanitizedAppName").apply { mkdirs() }
        val assetsDir = File(projectDir, "app/src/main/assets").apply { mkdirs() }

        val rootHtmlFile = File(projectDir, "index.html")
        val assetHtmlFile = File(assetsDir, "index.html")

        val currentHtml = when {
            rootHtmlFile.exists() && rootHtmlFile.length() > 50 -> rootHtmlFile.readText()
            assetHtmlFile.exists() && assetHtmlFile.length() > 50 -> assetHtmlFile.readText()
            else -> ""
        }

        val lowerPrompt = prompt.lowercase().trim()
        val isExplicitReset = lowerPrompt.contains("scratch se") || lowerPrompt.contains("purana hatao") ||
                lowerPrompt.contains("delete karke naya") || lowerPrompt.contains("fresh start") ||
                lowerPrompt.contains("start fresh") || lowerPrompt.contains("new app from scratch") ||
                lowerPrompt.contains("reset karo")

        // A file only counts as an existing editable app if it contains <!-- saif-ai-build -->
        val isExistingApp = currentHtml.contains("<!-- saif-ai-build -->") && !isExplicitReset

        // --------------------------------------------------------------------------------
        // Stage 1: Planning
        // --------------------------------------------------------------------------------
        onProgressUpdate(
            if (isExistingApp) "🔍 Analyzing current app & planning requested changes..."
            else "🧠 Understanding vision & designing app architecture...",
            1
        )
        delay(200)

        val userPromptToSend: String
        val systemPrompt = BUILDER_SYSTEM_PROMPT

        if (isExistingApp) {
            // Edit mode: fetch last 6 user prompts for context
            val historyMessages = try {
                ProjectChatHistoryManager.getMessages(context, project.appName)
            } catch (e: Exception) {
                emptyList()
            }
            val userHistory = historyMessages
                .filter { it.sender.equals("user", ignoreCase = true) }
                .takeLast(6)
                .map { it.content }

            val historySection = if (userHistory.isNotEmpty()) {
                "\n\nPROJECT_HISTORY (last user prompts):\n" + userHistory.mapIndexed { idx, p -> "${idx + 1}. $p" }.joinToString("\n")
            } else ""

            val errorDetails = if (errorInfo != null) {
                "\n\nRUNTIME/COMPILER ISSUE TO FIX:\n${errorInfo.errorMessage} (${errorInfo.fileName}:${errorInfo.lineNumber})\n${errorInfo.codeSnippet}"
            } else ""

            userPromptToSend = """
CURRENT_CODE:
$currentHtml

CHANGE_REQUEST:
$prompt$errorDetails$historySection

Please return the FULL updated HTML document starting with <!DOCTYPE html>. Keep all existing functional features and styling intact while surgically applying the change request.
            """.trimIndent()
        } else {
            // Brand new app: user raw prompt + app name. Never wrap, summarize, or shorten!
            userPromptToSend = "${prompt.trim()}\n\nApp Name: ${project.appName}"
        }

        // --------------------------------------------------------------------------------
        // Stage 2: Synthesis & Streaming
        // --------------------------------------------------------------------------------
        onProgressUpdate(
            if (isExistingApp) "⚡ Modifying code & refining UI/UX..."
            else "⚡ Synthesizing application code, interactions & animations...",
            2
        )

        var rawResponse = ""
        var apiError: Throwable? = null

        suspend fun executeCall(p: String): Result<String> {
            return AIApiUtility.executeWithFallback(
                prompt = p,
                mode = "build",
                customSystemPrompt = systemPrompt,
                base64Images = base64Images
            )
        }

        val result = executeCall(userPromptToSend)
        if (result.isSuccess) {
            rawResponse = result.getOrNull() ?: ""
        } else {
            apiError = result.exceptionOrNull()
        }

        var extractedHtml = extractHtmlDocument(rawResponse)

        // If </html> is missing, output was truncated: retry once
        if (extractedHtml == null && rawResponse.isNotBlank() && (rawResponse.contains("<!DOCTYPE", ignoreCase = true) || rawResponse.contains("<html", ignoreCase = true))) {
            Log.w(TAG, "HTML output appeared truncated (missing </html>). Retrying once...")
            onProgressUpdate("🔄 Output was truncated, resuming full code generation...", 2)
            val retryPrompt = "$userPromptToSend\n\nIMPORTANT: The previous output was truncated. Return the complete code and ensure the document ends with </html>."
            val retryResult = executeCall(retryPrompt)
            if (retryResult.isSuccess) {
                rawResponse = retryResult.getOrNull() ?: ""
                extractedHtml = extractHtmlDocument(rawResponse)
            }
        }

        // Strict verification: Fail honestly with a clear error, never fall back to a hardcoded template
        if (extractedHtml == null) {
            val failureDetail = apiError?.message?.ifBlank { null }
                ?: if (rawResponse.isBlank()) {
                    "The AI model returned an empty response. Please verify your API key, model selection, or network connection in Settings (⚙️)."
                } else if (!rawResponse.contains("</html>", ignoreCase = true)) {
                    "The code generation was cut off before completion (missing </html>). Please retry with a stronger model or shorter prompt."
                } else {
                    "Could not extract a valid HTML document from the model output. Response started with: ${rawResponse.take(180)}..."
                }

            Log.e(TAG, "Build failed honestly without template fallback: $failureDetail")
            onProgressUpdate("❌ Build Failed: $failureDetail", 4)
            return@withContext AutonomousBuildResult(
                isSuccess = false,
                summary = "❌ Build Failed: $failureDetail",
                updatedFiles = emptyList(),
                rawResponse = rawResponse,
                error = failureDetail
            )
        }

        // --------------------------------------------------------------------------------
        // Stage 3: Save to Disk & Assets
        // --------------------------------------------------------------------------------
        onProgressUpdate("📁 Saving application & syncing assets...", 3)
        delay(150)

        // Save backup of previous version
        if (currentHtml.isNotBlank()) {
            try {
                File(projectDir, "index.prev.html").writeText(currentHtml)
                File(assetsDir, "index.prev.html").writeText(currentHtml)
            } catch (e: Exception) {
                Log.w(TAG, "Could not write index.prev.html backup", e)
            }
        }

        // Ensure <!-- saif-ai-build --> tag exists in HTML right after <head> or at start of <html
        val finalHtml = ensureSaifBuildTag(extractedHtml)

        // Write to both <projectDir>/index.html and <projectDir>/app/src/main/assets/index.html
        rootHtmlFile.writeText(finalHtml)
        assetHtmlFile.writeText(finalHtml)

        // Ensure minimal Android packaging stubs exist so APK packaging succeeds
        ensureAndroidContainerStubs(projectDir, project)

        // Notify editor view model if present
        try {
            editorViewModel?.refreshTree(context)
        } catch (ignored: Exception) {}

        val updatedFiles = listOf("index.html", "app/src/main/assets/index.html")

        // --------------------------------------------------------------------------------
        // Stage 4: App Ready
        // --------------------------------------------------------------------------------
        val summaryText = if (isExistingApp) {
            "Updated '${project.appName}' successfully based on: $prompt"
        } else {
            "Built '${project.appName}' successfully! Fully functional and interactive."
        }

        onProgressUpdate("✅ App Ready! Preview available.", 4)

        AutonomousBuildResult(
            isSuccess = true,
            summary = summaryText,
            updatedFiles = updatedFiles,
            rawResponse = rawResponse,
            error = null
        )
    }

    /**
     * Extracts a self-contained HTML document starting at <!DOCTYPE or <html and ending at </html>.
     * Returns null if no opening or closing tag is found (indicating truncation or invalid format).
     */
    fun extractHtmlDocument(rawOutput: String): String? {
        if (rawOutput.isBlank()) return null
        val lower = rawOutput.lowercase()
        val docTypeIdx = lower.indexOf("<!doctype html")
        val htmlIdx = lower.indexOf("<html")

        val startIdx = when {
            docTypeIdx != -1 && htmlIdx != -1 -> Math.min(docTypeIdx, htmlIdx)
            docTypeIdx != -1 -> docTypeIdx
            htmlIdx != -1 -> htmlIdx
            else -> -1
        }

        val endClosing = "</html>"
        val endIdx = lower.lastIndexOf(endClosing)

        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
            return null
        }

        return rawOutput.substring(startIdx, endIdx + endClosing.length).trim()
    }

    /**
     * Ensures the marker <!-- saif-ai-build --> is present right after <head> or at the top of <html.
     */
    private fun ensureSaifBuildTag(html: String): String {
        if (html.contains("<!-- saif-ai-build -->")) return html
        val headIdx = html.indexOf("<head>", ignoreCase = true)
        if (headIdx != -1) {
            val insertAt = headIdx + "<head>".length
            return html.substring(0, insertAt) + "\n<!-- saif-ai-build -->" + html.substring(insertAt)
        }
        val headWithAttrIdx = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (headWithAttrIdx != null) {
            val insertAt = headWithAttrIdx.range.last + 1
            return html.substring(0, insertAt) + "\n<!-- saif-ai-build -->" + html.substring(insertAt)
        }
        val htmlTagIdx = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (htmlTagIdx != null) {
            val insertAt = htmlTagIdx.range.last + 1
            return html.substring(0, insertAt) + "\n<head>\n<!-- saif-ai-build -->\n</head>" + html.substring(insertAt)
        }
        return "<!-- saif-ai-build -->\n$html"
    }

    /**
     * Ensures stub MainActivity and AndroidManifest.xml exist in projectDir
     * so that packaging into standalone APK can proceed cleanly.
     */
    private fun ensureAndroidContainerStubs(projectDir: File, project: ProjectData) {
        try {
            val pkgPath = project.packageName.replace('.', '/')
            val javaDir = File(projectDir, "app/src/main/java/$pkgPath").apply { mkdirs() }
            val mainActivityFile = File(javaDir, "MainActivity.java")
            if (!mainActivityFile.exists()) {
                mainActivityFile.writeText(
                    """
                    package ${project.packageName};
                    import android.app.Activity;
                    import android.os.Bundle;

                    public class MainActivity extends Activity {
                        @Override
                        protected void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                        }
                    }
                    """.trimIndent()
                )
            }

            val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
            if (!manifestFile.exists()) {
                manifestFile.parentFile?.mkdirs()
                manifestFile.writeText(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="${project.packageName}">
                        <uses-permission android:name="android.permission.INTERNET" />
                        <application
                            android:allowBackup="true"
                            android:icon="@mipmap/ic_launcher"
                            android:label="${project.appName}"
                            android:theme="@android:style/Theme.DeviceDefault">
                            <activity
                                android:name=".MainActivity"
                                android:exported="true">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                    """.trimIndent()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating container stubs", e)
        }
    }
}
