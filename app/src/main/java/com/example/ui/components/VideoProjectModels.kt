package com.example.ui.components

import android.net.Uri

/**
 * Represents a media clip item on the timeline (Video or Photo).
 */
data class TimelineMediaClip(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val uri: Uri? = null,
    val durationSeconds: Float = 6.0f,
    val isAiEnhanced: Boolean = false,
    val isPhoto: Boolean = false,
    val trackIndex: Int = 0,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val rotation: Float = 0f,
    val isFlippedHorizontally: Boolean = false,
    val transitionIn: String = "None",
    val origin: String = "manual", // "manual" or "ai" (Level 16 Timeline Memory)
    val aiWhy: String = "",        // Plain-language reason for AI edit (Level 13)
    val inSeconds: Float = 0f,
    val outSeconds: Float = 6.0f,
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val lastModified: Long = System.currentTimeMillis()
)

data class EditOperation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val op: String, // "trim", "speed", "crop", "colorAdjust", "chromaKey", "stabilize", "reverse", "volume", "autoDuck"
    val clipId: String,
    val startSeconds: Float? = null,
    val endSeconds: Float? = null,
    val params: Map<String, String> = emptyMap(),
    val why: String = ""
)

data class EditOverlay(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String, // "text", "sticker", "broll", "effect", "filter", "sfx"
    val startSeconds: Float = 0f,
    val durationSeconds: Float = 4f,
    val props: Map<String, String> = emptyMap(),
    val why: String = ""
)

data class VideoEditPlan(
    val summary: String,
    val targetClips: List<String> = emptyList(),
    val operations: List<EditOperation> = emptyList(),
    val overlays: List<EditOverlay> = emptyList(),
    val musicMood: String? = null,
    val captions: Boolean = false,
    val why: String = ""
)

data class VideoScriptSection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val kind: String = "point", // "hook", "story", "point", "transition", "cta", "outro"
    val narration: String,
    val emotion: String = "excited", // "excited", "calm", "serious", "curious", "warm", "urgent"
    val estSeconds: Float = 6.0f,
    val visualHint: String = ""
)

data class VideoScript(
    val title: String,
    val targetDurationSeconds: Float = 30f,
    val tone: String = "Energetic",
    val language: String = "Hindi & English",
    val sections: List<VideoScriptSection> = emptyList()
)

data class VideoCaptionItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startSeconds: Float,
    val endSeconds: Float,
    val text: String,
    val highlightWordIndex: Int = 0,
    val words: List<String> = emptyList()
)

data class VideoBRollItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startSeconds: Float,
    val durationSeconds: Float,
    val prompt: String,
    val title: String,
    val imageUri: String? = null
)

data class TimelineMarkerItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestampSeconds: Float,
    val label: String,
    val note: String = "",
    val isFeedback: Boolean = false
)

/**
 * Data structure holding the state of a video editing project.
 */
data class VideoProjectData(
    val projectName: String = "Untitled Video Project",
    val resolution: String = "1080P",
    val fps: String = "60FPS",
    val totalDurationSeconds: Float = 0.0f,
    val currentPositionSeconds: Float = 0.0f,
    val clips: List<TimelineMediaClip> = emptyList(),
    val audioTrackName: String = "",
    val textOverlay: String = "",
    val effectOverlay: String = "",
    val selectedClipIndex: Int = 0,
    val aspectRatio: String = "9:16",
    val activeFilter: String = "Original",
    val filterIntensity: Float = 0.8f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val audioVolume: Float = 1.0f,
    val isStabilized: Boolean = false,
    val isChromaKeyEnabled: Boolean = false,
    val activeWatermark: String = "",
    val activeTransition: String = "Dissolve",
    val textColorHex: String = "#00DAF3",
    val textFontFamily: String = "Monospace",
    val textAnimation: String = "Glitch Fade",
    val overlayMediaUri: String? = null,
    val overlayOpacity: Float = 1.0f,
    val activePipPosition: String = "None",
    val script: VideoScript? = null,
    val activeEditPlan: VideoEditPlan? = null,
    val aiEditsOnlyFilter: Boolean = false,
    val audioDuckAmount: Float = 0.25f,
    val autoCaptions: List<VideoCaptionItem> = emptyList(),
    val bRollItems: List<VideoBRollItem> = emptyList(),
    val timelineMarkers: List<TimelineMarkerItem> = emptyList(),
    val selectedLanguage: String = "Hindi & English"
)

/**
 * Toolbar tools tab selector
 */
enum class VideoStudioToolTab {
    NONE,
    EDIT,
    AUDIO,
    TEXT,
    OVERLAY,
    EFFECTS,
    FILTERS,
    ADJUST,
    RATIO,
    PRO_TOOLS
}

data class EffectItem(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val previewColor: Long,
    val isAi: Boolean = false
)

data class FilterItem(
    val id: String,
    val name: String,
    val tone: String,
    val tintColor: Long
)

data class AudioTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val duration: String,
    val bpm: Int
)

data class ProFeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val badge: String
)

object VideoEditorPresets {
    val ALL_EFFECTS = listOf(
        EffectItem("fx_flare", "Anamorphic Flare", "Cinematic Lens", "Cinematic horizontal blue light streak", 0xFF00DAF3, isAi = true),
        EffectItem("fx_neon", "Cyberpunk Neon Glow", "Trending", "Electric neon edge pulse with cyan/magenta", 0xFF7B2BFF, isAi = true),
        EffectItem("fx_vhs", "VHS Retro 1998", "Atmospheric / Retro", "Nostalgic analog tape tracking distortion", 0xFFFFC44D),
        EffectItem("fx_rgb", "RGB Chromatic Split", "Glitch & Sci-Fi", "Red, green and blue channel aberration shift", 0xFFFF4D6D, isAi = true),
        EffectItem("fx_grain", "Cinema 35mm Grain", "Atmospheric / Retro", "Organic film stock silver halide grain", 0xFFCCC3D9),
        EffectItem("fx_matrix", "Matrix Digital Rain", "Glitch & Sci-Fi", "Hacker cyber green falling glyphs", 0xFF35E6A0, isAi = true),
        EffectItem("fx_leak", "Golden Lens Leak", "Cinematic Lens", "Warm solar flare light wash on edges", 0xFFFFB703),
        EffectItem("fx_holo", "Hologram Scanlines", "Glitch & Sci-Fi", "Futuristic sci-fi HUD scanlines & flicker", 0xFF00B4D8, isAi = true),
        EffectItem("fx_zoom", "Dynamic Zoom Blur", "Trending", "Action radial zoom blur during motion", 0xFFE040FB),
        EffectItem("fx_slowmo", "Slow Mo Optical Flow", "AI Distortion", "Super silky frame-blended slow motion", 0xFF7C4DFF, isAi = true),
        EffectItem("fx_strobe", "Strobe Flash Party", "Trending", "Rhythmic white strobe pulse sync", 0xFFFFFFFF),
        EffectItem("fx_bokeh", "Floating Dust Bokeh", "Atmospheric / Retro", "Organic sunlit floating dust motes", 0xFFFFE082),
        EffectItem("fx_edge", "Edge Glow Neon", "Light & Glow", "High contrast luminescence edge highlight", 0xFF00E676),
        EffectItem("fx_thermal", "Thermal Heatmap FX", "AI Distortion", "Infrared predator heat vision false color", 0xFFFF3D00, isAi = true),
        EffectItem("fx_mirror", "Inception Mirror", "Glitch & Sci-Fi", "Kaleidoscopic quad reflection symmetry", 0xFF448AFF),
        EffectItem("fx_pixel", "Pixelate 8-Bit Retro", "Atmospheric / Retro", "Arcade gaming low-fi mosaic block grid", 0xFF69F0AE),
        EffectItem("fx_lightning", "Lightning Sparks", "Light & Glow", "Electric arc discharge bolts over footage", 0xFFFFFF00, isAi = true),
        EffectItem("fx_rain", "Raindrops On Lens", "Atmospheric / Retro", "Dynamic moisture droplets & glass refraction", 0xFF80D8FF),
        EffectItem("fx_comic", "Halftone Pop Comic", "Atmospheric / Retro", "Marvel vintage printed comic dot texture", 0xFFFF5252),
        EffectItem("fx_shake", "Shaky Handheld Cam", "Trending", "Realistic action camera tremor & recoil", 0xFFB0BEC5),
        EffectItem("fx_dream", "Dreamy Soft Glow", "Light & Glow", "Romantic diffused mist bloom highlights", 0xFFF48FB1),
        EffectItem("fx_vignette", "Dark Cinema Vignette", "Cinematic Lens", "Heavy letterbox edge falloff focus", 0xFF37474F),
        EffectItem("fx_prism", "Prism Refraction", "Cinematic Lens", "Split crystal rainbow dispersion", 0xFFB388FF),
        EffectItem("fx_blackhole", "Black Hole Warp", "AI Distortion", "Gravitational center spacetime pull", 0xFFD500F9, isAi = true),
        EffectItem("fx_scanlines", "Glitch Scanlines", "Glitch & Sci-Fi", "Digital corrupted data glitch burst", 0xFF00E5FF, isAi = true)
    )

    val ALL_FILTERS = listOf(
        FilterItem("flt_orig", "Original", "Natural Standard", 0xFFFFFFFF),
        FilterItem("flt_teal_orange", "Teal & Orange", "Hollywood Blockbuster", 0xFFFF8C42),
        FilterItem("flt_cyber", "Cyberpunk Night", "Vibrant Neon Blue & Pink", 0xFF00F5D4),
        FilterItem("flt_kodak", "Kodak 500T", "Vintage 35mm Film", 0xFFFFD166),
        FilterItem("flt_sunset", "Golden Hour", "Warm Sunset Glow", 0xFFFFB703),
        FilterItem("flt_noir", "Moody Noir", "High Contrast B&W", 0xFF8D99AE),
        FilterItem("flt_pastel", "Pastel Dream", "Anime Aesthetic Soft", 0xFFFFC6FF),
        FilterItem("flt_emerald", "Forest Emerald", "Deep Natural Greens", 0xFF06D6A0),
        FilterItem("flt_bleach", "Bleach Bypass", "Desaturated Gritty Look", 0xFFB7B7A4),
        FilterItem("flt_sepia", "Vintage 70s", "Warm Faded Nostalgia", 0xFFDDA15E)
    )

    val ALL_AUDIO_TRACKS = listOf(
        AudioTrackItem("aud_1", "Cyber_Synthwave.wav", "Neon Syndicate", "Synthwave", "02:45", 128),
        AudioTrackItem("aud_2", "Tokyo_Midnight_Drift.mp3", "Kowloon Sound", "Phonk / Drift", "02:18", 140),
        AudioTrackItem("aud_3", "Cinematic_Orchestra_808.wav", "Hans Cyber", "Cinematic Epic", "03:12", 95),
        AudioTrackItem("aud_4", "LoFi_Rainy_Afternoon.mp3", "Study Chills", "Lo-Fi Beats", "01:54", 84),
        AudioTrackItem("aud_5", "Hyperdrive_Bass_Boost.wav", "Overclocked", "EDM Bass", "02:30", 150),
        AudioTrackItem("aud_6", "Retro_80s_Arp.wav", "Analog Dreams", "Retro Wave", "02:05", 120),
        AudioTrackItem("aud_7", "Dark_Cyber_Club.mp3", "Underground", "Industrial", "03:40", 132),
        AudioTrackItem("aud_8", "Acoustic_Sunrise.mp3", "Morning Light", "Acoustic Ambient", "02:22", 100)
    )

    val ALL_PRO_FEATURES = listOf(
        ProFeatureItem("pro_captions", "AI Auto-Captions", "Generates animated synchronized subtitles using automatic speech-to-text with karaoke word highlights.", "AI Intelligence", "AI REALTIME"),
        ProFeatureItem("pro_chroma", "Chroma Key / Green Screen", "Ultra-clean background subtraction with spill suppression and backdrop replacement.", "VFX / Compositing", "PRO STUDIO"),
        ProFeatureItem("pro_stabilize", "AI Video Stabilizer", "Multi-axis gyroscopic stabilization algorithm removing handheld camera jitter.", "AI Intelligence", "GYRO SMOOTH"),
        ProFeatureItem("pro_speedramp", "Speed Ramp Curve Studio", "Dynamic bezier velocity curves (Hero Ramp, Bullet Time, Flash-Cut, Montage).", "Motion & Timing", "CURVE PRO"),
        ProFeatureItem("pro_beatsync", "AI Beat Detection Sync", "Audio transient spectral analyzer that auto-places cut marks on musical beat drops.", "Audio & Music", "AI AUDIO"),
        ProFeatureItem("pro_voicechanger", "AI Voice Changer & De-Noise", "Transforms audio into Studio Mic, Deep Cinema Voice, Cyber Cyborg, or removes background hum.", "Audio & Music", "NEURAL DSP"),
        ProFeatureItem("pro_keyframes", "Keyframe Animation Engine", "Full keyframing curve for Scale, Position, Rotation, and Opacity over time.", "Motion & Timing", "VFX TIMELINE"),
        ProFeatureItem("pro_motiontrack", "Motion Point Tracker", "AI tracks face, object or vehicle to anchor text, arrows or stickers in 3D space.", "AI Intelligence", "3D TRACKER"),
        ProFeatureItem("pro_transitions", "Cinematic Transitions (16+)", "Whip Pan, Zoom Glitch, Cross Dissolve, Light Flash, Spin Warp, Page Curl.", "VFX / Compositing", "CINEMA PACK"),
        ProFeatureItem("pro_opticalflow", "Optical Flow Slow Motion", "AI motion vector frame synthesis for artifact-free 0.1x super slow motion.", "Motion & Timing", "AI FRAMES"),
        ProFeatureItem("pro_rgbcurves", "RGB Color Curves Studio", "Pro-grade Master, Red, Green, and Blue spline curves for Hollywood color grading.", "Color & Grading", "PRO COLOR"),
        ProFeatureItem("pro_scenecut", "AI Scene Cut Detection", "Automated shot boundary detection splitting raw footage into separate takes.", "AI Intelligence", "AUTO CUT"),
        ProFeatureItem("pro_ducking", "Smart Audio Ducking", "Intelligently lowers background soundtrack volume whenever speech or voiceover is detected.", "Audio & Music", "SMART DUCK"),
        ProFeatureItem("pro_luts", "Custom 3D LUT Import", "Loads industry-standard Hollywood .cube LUT color look profiles.", "Color & Grading", "3D LUT"),
        ProFeatureItem("pro_hdr", "HDR10+ / Dolby Color", "Expands color reproduction to 10-bit Rec.2020 wide color gamut.", "Color & Grading", "10-BIT HDR"),
        ProFeatureItem("pro_glowoutline", "AI Silhouette Outline Glow", "Segments actor from background with animated neon electric contour stroke.", "AI Intelligence", "BODY AI"),
        ProFeatureItem("pro_watermark", "Dynamic Watermark Engine", "Custom transparent creator watermark with opacity and position anchors.", "Branding", "SIGNATURE"),
        ProFeatureItem("pro_stickers", "Animated Stickers & HUD", "30+ animated stickers, emojis, cyber arrows, and futuristic HUD graphics.", "Graphics & Overlay", "30+ PACK"),
        ProFeatureItem("pro_eq", "10-Band Audio Equalizer", "Precision frequency parametric EQ with Bass Booster, Vocal Enhance, and Treble presets.", "Audio & Music", "10-BAND EQ"),
        ProFeatureItem("pro_storyboard", "Script to Storyboard AI", "Transforms written ideas into paced scene edits and rhythm guidelines.", "AI Intelligence", "SCRIPT AI"),
        ProFeatureItem("pro_motionblur", "Cinematic Motion Blur", "180-degree shutter angle synthesis giving cinematic smoothness to fast pans.", "VFX / Compositing", "SHUTTER 180°"),
        ProFeatureItem("pro_freeze", "Freeze Frame & Action Climax", "Instant 2-second impact freeze frame with dramatic zoom accent.", "Motion & Timing", "CLIMAX FX")
    )
}
