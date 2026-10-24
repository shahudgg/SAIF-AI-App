package com.example.studio.model

import java.util.UUID

enum class SaifAiWorkspaceMode {
    AI_WORKFLOWS,
    CLASSIC_TOOLS
}

enum class AiWorkflowType(
    val title: String,
    val subtitle: String,
    val badge: String,
    val stepLabel: String
) {
    ZERO_TO_VIDEO(
        title = "ZERO TO VIDEO",
        subtitle = "Autonomous text-to-video pipeline with research, scenes, motion graphics & voiceover",
        badge = "FULL AUTONOMOUS",
        stepLabel = "Zero-to-Video Autonomous Generator"
    ),
    VIDEO_TO_EDITING(
        title = "VIDEO TO EDITING",
        subtitle = "Intelligent video pacing, color grading, jump-cuts & overlays without altering narration",
        badge = "SMART CUTS",
        stepLabel = "Intelligent Video Timeline Editor"
    ),
    VIDEO_EDIT_WITH_AI_VOICE(
        title = "VIDEO EDITING WITH AI VOICE",
        subtitle = "Scene-aware AI narration generated from footage, auto-synced with music ducking",
        badge = "VOICE & VIDEO",
        stepLabel = "Neural Voiceover Video Synchronization"
    ),
    VOICEOVER_TO_VIDEO(
        title = "VOICE OVER TO VIDEO",
        subtitle = "Authoritative voiceover preservation with AI synthesized matching scenes & kinetic subtitles",
        badge = "SPEECH TO SCENES",
        stepLabel = "Voiceover to Video Scene Synthesis"
    )
}

data class QuestionOption(
    val id: String,
    val label: String,
    val subtitle: String? = null
)

data class QuestionCardData(
    val id: String, // e.g. "duration", "language", "style", "aspect", "audience", "goal"
    val question: String,
    val explanation: String = "",
    val presets: List<QuestionOption>,
    val allowCustom: Boolean = true,
    val customPlaceholder: String = "Type custom requirement...",
    var selectedOptionId: String? = null,
    var customText: String = "",
    var isConfirmed: Boolean = false,
    val isOptional: Boolean = false
) {
    fun getEffectiveAnswer(): String {
        if (selectedOptionId == "custom") {
            return customText.ifBlank { "Custom" }
        }
        val match = presets.firstOrNull { it.id == selectedOptionId }
        return match?.label ?: customText.ifBlank { "Default" }
    }
}

data class DirectorChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "director", "user", "system"
    val messageText: String,
    val timestamp: String = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date()),
    val questionCard: QuestionCardData? = null,
    val isProgressIndicator: Boolean = false,
    val progressPercent: Float = 0f,
    val progressStatus: String = "",
    val sceneBreakdownPreview: List<SceneManifestItem>? = null,
    val isActionable: Boolean = false
)

data class SceneManifestItem(
    val sceneId: Int,
    val title: String,
    val narrationSegment: String,
    val durationSeconds: Float,
    val visualObjective: String,
    val motionGraphicsType: String,
    val animationCurve: String = "EaseInOutCubic",
    val cameraMovement: String = "Slow Push In",
    val transition: String = "Cross Dissolve",
    val telegramKeyword: String = "",
    val audioMood: String = "Cinematic Ambient"
)

data class WorkflowProjectBrief(
    val workflowType: AiWorkflowType,
    var topicOrScript: String = "",
    var isUserSuppliedCompleteScript: Boolean = false,
    var durationSeconds: Float = 30f,
    var language: String = "Hindi & English (Hinglish)",
    var visualStyle: String = "Cinematic 35mm",
    var aspectRatio: String = "9:16",
    var targetAudience: String = "YouTube Shorts / Reels",
    var editingGoal: String = "High Energy & Dynamic Cuts",
    var voiceCharacter: String = "Fenrir (Cinematic Deep)",
    var speechPace: Float = 1.0f,
    var enableAudioDucking: Boolean = true,
    var userFootageUri: String? = null,
    var userAudioUri: String? = null,
    var sceneManifest: List<SceneManifestItem> = emptyList()
) {
    fun estimateSpokenWordCount(): Int {
        // Words per minute is approximately 140 WPM for normal pace
        val wpm = 140f * speechPace
        return ((durationSeconds / 60f) * wpm).toInt().coerceAtLeast(10)
    }
}
