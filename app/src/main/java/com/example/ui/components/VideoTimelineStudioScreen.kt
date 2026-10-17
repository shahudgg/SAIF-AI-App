package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class VideoStudioSubView {
    NONE,
    AI_DIRECTOR_CHAT,
    EXPORT_STUDIO
}

/**
 * Full Multi-Track Video Timeline Studio Screen.
 * Fully interactive with:
 * 1. Top Workspace Header (Back, Title, Resolution & FPS dialog, Undo, Redo, Export)
 * 2. Preview Viewport Area (Aspect ratio, filters, overlays, timecode, fullscreen toggle)
 * 3. Transport Controls Row (Replay -5s, Play/Pause live ticker, Forward +5s, Split clip)
 * 4. Signature SAIF AI EDITING Pill (56px) with Chat Director button
 * 5. Interactive Multi-Track Timeline (Video with add +, Audio, Text, Effects tracks)
 * 6. Bottom Workspace Toolbar with rich sheets for all 9 toolkits:
 *    - Edit, Audio, Text, Overlay, Effects (25+), Filters (10), Adjust, Ratio, Pro Tools (22)
 * 7. Navigation into AI Chat Director and Export Studio
 */
@Composable
fun VideoTimelineStudioScreen(
    initialProject: VideoProjectData,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentProject by remember { mutableStateOf(initialProject) }
    var activeSubView by remember { mutableStateOf(VideoStudioSubView.NONE) }
    var activeToolTab by remember { mutableStateOf(VideoStudioToolTab.NONE) }
    var isPlaying by remember { mutableStateOf(false) }
    var showSaifAiEditingModal by remember { mutableStateOf(false) }

    // Undo & Redo History Stacks
    var undoStack by remember { mutableStateOf(listOf<VideoProjectData>()) }
    var redoStack by remember { mutableStateOf(listOf<VideoProjectData>()) }

    // Timecode helper
    fun formatSeconds(sec: Float): String {
        val totalSecs = sec.toInt()
        val minutes = totalSecs / 60
        val remainingSecs = totalSecs % 60
        val millis = ((sec - totalSecs) * 100).toInt()
        return "%02d:%02d:%02d".format(minutes, remainingSecs, millis)
    }

    fun pushState(newProject: VideoProjectData) {
        undoStack = undoStack + currentProject
        redoStack = emptyList()
        currentProject = newProject
    }

    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.last()
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + currentProject
            currentProject = prev
            Toast.makeText(context, "Undo", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.last()
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + currentProject
            currentProject = next
            Toast.makeText(context, "Redo", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nothing to redo", Toast.LENGTH_SHORT).show()
        }
    }

    fun applySmartSplit() {
        if (currentProject.clips.isEmpty()) {
            Toast.makeText(context, "No clips to split", Toast.LENGTH_SHORT).show()
            return
        }

        // Find which clip contains the current playhead
        var foundIdx = -1
        var accum = 0f
        var offsetAtPlayhead = 0f
        for (i in currentProject.clips.indices) {
            val clip = currentProject.clips[i]
            val clipStart = accum
            val clipEnd = accum + clip.durationSeconds
            if (currentProject.currentPositionSeconds >= clipStart && currentProject.currentPositionSeconds <= clipEnd + 0.05f) {
                foundIdx = i
                offsetAtPlayhead = (currentProject.currentPositionSeconds - clipStart).coerceIn(0f, clip.durationSeconds)
                break
            }
            accum = clipEnd
        }

        val selectedIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.size - 1)
        val targetIdx = if (foundIdx != -1) foundIdx else selectedIdx
        val target = currentProject.clips[targetIdx]

        if (target.durationSeconds < 0.3f) {
            Toast.makeText(context, "Clip too short to split (<0.3s)", Toast.LENGTH_SHORT).show()
            return
        }

        val splitOffset = if (foundIdx == targetIdx && offsetAtPlayhead in 0.15f..(target.durationSeconds - 0.15f)) {
            offsetAtPlayhead
        } else {
            target.durationSeconds / 2f
        }

        val durA = splitOffset
        val durB = target.durationSeconds - splitOffset
        val baseName = target.name.replace(Regex("_part\\d+|_cut\\d+"), "")

        val partA = target.copy(
            id = UUID.randomUUID().toString(),
            name = "${baseName}_part1",
            durationSeconds = durA,
            inSeconds = target.inSeconds,
            outSeconds = target.inSeconds + durA,
            origin = "manual",
            lastModified = System.currentTimeMillis()
        )
        val partB = target.copy(
            id = UUID.randomUUID().toString(),
            name = "${baseName}_part2",
            durationSeconds = durB,
            inSeconds = target.inSeconds + durA,
            outSeconds = target.outSeconds,
            origin = "manual",
            lastModified = System.currentTimeMillis()
        )

        val newClips = currentProject.clips.toMutableList()
        newClips.removeAt(targetIdx)
        newClips.add(targetIdx, partA)
        newClips.add(targetIdx + 1, partB)

        val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()

        pushState(
            currentProject.copy(
                clips = newClips,
                selectedClipIndex = targetIdx + 1,
                totalDurationSeconds = newTotal
            )
        )
        Toast.makeText(context, "✂️ Split into 2 parts at ${formatSeconds(currentProject.currentPositionSeconds)}!", Toast.LENGTH_SHORT).show()
    }

    fun applyCutDelete() {
        if (currentProject.clips.isEmpty()) {
            Toast.makeText(context, "No clips to cut", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentProject.clips.size <= 1) {
            Toast.makeText(context, "Cannot delete only remaining clip", Toast.LENGTH_SHORT).show()
            return
        }
        val targetIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.size - 1)
        val target = currentProject.clips[targetIdx]
        val newClips = currentProject.clips.toMutableList()
        newClips.removeAt(targetIdx)
        val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
        val newSelected = (targetIdx - 1).coerceAtLeast(0)
        pushState(
            currentProject.copy(
                clips = newClips,
                selectedClipIndex = newSelected,
                totalDurationSeconds = newTotal
            )
        )
        Toast.makeText(context, "🗑️ Cut / Removed '${target.name}'!", Toast.LENGTH_SHORT).show()
    }

    fun applyCutHead() {
        if (currentProject.clips.isEmpty()) return
        val targetIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.size - 1)
        val target = currentProject.clips[targetIdx]

        var accum = 0f
        for (i in 0 until targetIdx) accum += currentProject.clips[i].durationSeconds
        val offset = (currentProject.currentPositionSeconds - accum).coerceIn(0f, target.durationSeconds)

        val trimAmount = if (offset in 0.2f..(target.durationSeconds - 0.2f)) offset else 0.5f.coerceAtMost(target.durationSeconds - 0.3f)
        if (target.durationSeconds - trimAmount < 0.2f) {
            Toast.makeText(context, "Cannot trim: piece would be too small", Toast.LENGTH_SHORT).show()
            return
        }

        val trimmed = target.copy(
            durationSeconds = target.durationSeconds - trimAmount,
            inSeconds = target.inSeconds + trimAmount,
            origin = "manual",
            lastModified = System.currentTimeMillis()
        )
        val newClips = currentProject.clips.toMutableList()
        newClips[targetIdx] = trimmed
        val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
        pushState(currentProject.copy(clips = newClips, totalDurationSeconds = newTotal))
        Toast.makeText(context, "✂️◀ Trimmed start by ${"%.1f".format(trimAmount)}s", Toast.LENGTH_SHORT).show()
    }

    fun applyCutTail() {
        if (currentProject.clips.isEmpty()) return
        val targetIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.size - 1)
        val target = currentProject.clips[targetIdx]

        var accum = 0f
        for (i in 0 until targetIdx) accum += currentProject.clips[i].durationSeconds
        val offset = (currentProject.currentPositionSeconds - accum).coerceIn(0f, target.durationSeconds)

        val trimAmount = if (offset in 0.2f..(target.durationSeconds - 0.2f)) (target.durationSeconds - offset) else 0.5f.coerceAtMost(target.durationSeconds - 0.3f)
        if (target.durationSeconds - trimAmount < 0.2f) {
            Toast.makeText(context, "Cannot trim: piece would be too small", Toast.LENGTH_SHORT).show()
            return
        }

        val trimmed = target.copy(
            durationSeconds = target.durationSeconds - trimAmount,
            outSeconds = target.outSeconds - trimAmount,
            origin = "manual",
            lastModified = System.currentTimeMillis()
        )
        val newClips = currentProject.clips.toMutableList()
        newClips[targetIdx] = trimmed
        val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
        pushState(currentProject.copy(clips = newClips, totalDurationSeconds = newTotal))
        Toast.makeText(context, "▶✂️ Trimmed end by ${"%.1f".format(trimAmount)}s", Toast.LENGTH_SHORT).show()
    }

    fun applyDuplicateClip() {
        if (currentProject.clips.isEmpty()) return
        val targetIdx = currentProject.selectedClipIndex.coerceIn(0, currentProject.clips.size - 1)
        val target = currentProject.clips[targetIdx]
        val dup = target.copy(
            id = UUID.randomUUID().toString(),
            name = "${target.name}_Copy",
            origin = "manual",
            lastModified = System.currentTimeMillis()
        )
        val newClips = currentProject.clips.toMutableList()
        newClips.add(targetIdx + 1, dup)
        val newTotal = newClips.sumOf { it.durationSeconds.toDouble() }.toFloat()
        pushState(currentProject.copy(clips = newClips, selectedClipIndex = targetIdx + 1, totalDurationSeconds = newTotal))
        Toast.makeText(context, "📋 Clip duplicated!", Toast.LENGTH_SHORT).show()
    }

    // Modal dialogs
    var showFullscreenPlayer by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var viewportMode by remember { mutableStateOf("VIDEO") }
    var isSimulatingRender by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableStateOf(0f) }
    var renderLogText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    var isGeneratingFromPrompt by remember { mutableStateOf(false) }
    var promptGenerationStatus by remember { mutableStateOf("") }
    var showPromptDirectorDialog by remember { mutableStateOf(false) }

    fun handleGenerateFromPrompt(userPrompt: String) {
        if (userPrompt.isBlank() || isGeneratingFromPrompt) return
        coroutineScope.launch {
            isGeneratingFromPrompt = true
            promptGenerationStatus = "Analyzing topic & researching..."
            val result = com.example.ui.components.SaifAiEditingEngine.generateAutonomousDirectorEdl(
                context = context,
                prompt = userPrompt,
                rawVideoUrl = null,
                onStepUpdate = { step, _ -> promptGenerationStatus = step }
            )
            result.onSuccess { (_, proj) ->
                pushState(proj)
                currentProject = proj
                isPlaying = true
                Toast.makeText(context, "✅ Assembled video from prompt!", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Generation error: ${err.message}", Toast.LENGTH_LONG).show()
            }
            isGeneratingFromPrompt = false
            promptGenerationStatus = ""
        }
    }

    // Persist changes back to local storage whenever project updates
    LaunchedEffect(currentProject) {
        com.example.util.ProjectDataManager.saveVideoProject(context, currentProject)
    }

    // Live playback ticker with smooth 20fps interpolation and auto-looping
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val safeTotal = if (currentProject.totalDurationSeconds > 0.5f) {
                currentProject.totalDurationSeconds
            } else {
                val clipSum = currentProject.clips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                if (clipSum > 0.5f) clipSum else 15.0f
            }
            if (currentProject.totalDurationSeconds != safeTotal) {
                currentProject = currentProject.copy(totalDurationSeconds = safeTotal)
            }
            if (currentProject.currentPositionSeconds >= safeTotal - 0.2f) {
                currentProject = currentProject.copy(currentPositionSeconds = 0f)
            }
            while (isPlaying) {
                delay(50) // smooth 20 fps tick
                val cur = currentProject.currentPositionSeconds
                val tot = if (currentProject.totalDurationSeconds > 0.5f) currentProject.totalDurationSeconds else 15.0f
                val next = cur + 0.05f
                if (next >= tot) {
                    currentProject = currentProject.copy(currentPositionSeconds = 0f)
                    isPlaying = false
                    com.example.utils.TTSManager.stop()
                } else {
                    currentProject = currentProject.copy(currentPositionSeconds = next)
                }
            }
        } else {
            com.example.utils.TTSManager.stop()
        }
    }

    // Launcher to add more clips from gallery (50+ items supported)
    val addMoreMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newClips = uris.mapIndexed { idx, uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/')?.take(18) ?: "Clip_${currentProject.clips.size + idx + 1}"
                TimelineMediaClip(
                    name = name,
                    uri = uri,
                    durationSeconds = 8.0f,
                    isAiEnhanced = false,
                    isPhoto = false
                )
            }
            pushState(
                currentProject.copy(
                    clips = currentProject.clips + newClips
                )
            )
            Toast.makeText(context, "Added ${uris.size} items to Timeline!", Toast.LENGTH_SHORT).show()
        }
    }



    // Back handling
    BackHandler {
        isPlaying = false
        com.example.utils.TTSManager.stop()
        if (activeSubView != VideoStudioSubView.NONE) {
            activeSubView = VideoStudioSubView.NONE
        } else if (activeToolTab != VideoStudioToolTab.NONE) {
            activeToolTab = VideoStudioToolTab.NONE
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131313))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF131313))
        ) {
            // --- 1. Top Bar (Workspace Header) ---
            Surface(
                color = Color(0xFF0E0E0E).copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = {
                                isPlaying = false
                                com.example.utils.TTSManager.stop()
                                onClose()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1C1B1B))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = currentProject.projectName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showResolutionDialog = true }
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00DAF3)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${currentProject.resolution} · ${currentProject.fps} · ${currentProject.aspectRatio}",
                                    color = Color(0xFFCCC3D9),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { handleUndo() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (undoStack.isNotEmpty()) Color.White else Color(0xFF6B6675),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { handleRedo() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (redoStack.isNotEmpty()) Color.White else Color(0xFF6B6675),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // AI DIRECTOR BUTTON
                        IconButton(
                            onClick = { showPromptDirectorDialog = true },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF201F2F))
                                .border(1.dp, Color(0xFF00DAF3).copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Prompt Director",
                                tint = Color(0xFF00DAF3),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // EXPORT BUTTON (Opens Export Studio Interface)
                        Button(
                            onClick = { activeSubView = VideoStudioSubView.EXPORT_STUDIO },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Export", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }

            // --- 2. Preview Viewport Area with Multi-Mode Selector ---
            VideoStudioViewportModeBar(
                currentMode = viewportMode,
                onSelectMode = { viewportMode = it }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Determine preview width based on aspect ratio or full width for code/script
                val viewportWidth = when {
                    viewportMode != "VIDEO" -> 360.dp
                    currentProject.aspectRatio == "16:9" -> 280.dp
                    currentProject.aspectRatio == "1:1" -> 220.dp
                    currentProject.aspectRatio == "4:5" -> 200.dp
                    currentProject.aspectRatio == "21:9" -> 300.dp
                    else -> 180.dp
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black,
                    border = BorderStroke(1.dp, Color(0x3500DAF3)),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .width(viewportWidth)
                        .fillMaxHeight(0.92f)
                ) {
                    when (viewportMode) {
                        "CODE" -> {
                            LivePythonCodeRenderer(
                                project = currentProject,
                                isSimulatingRender = isSimulatingRender,
                                renderProgress = renderProgress,
                                renderLogText = renderLogText,
                                onStartRender = {
                                    if (!isSimulatingRender) {
                                        coroutineScope.launch {
                                            isSimulatingRender = true
                                            renderProgress = 0.15f
                                            renderLogText = "[1/5] 📦 Scanning assets_catalog.json for Telegram assets..."
                                            delay(350)
                                            renderProgress = 0.40f
                                            renderLogText += "\n[2/5] ⚡ Compiling Ken Burns Zoom (1.0x -> 1.15x) on Scene 1..."
                                            delay(350)
                                            renderProgress = 0.65f
                                            renderLogText += "\n[3/5] 🎯 Compositing 3D Checkmark pop-in bounce overlay on Scene 3..."
                                            delay(350)
                                            renderProgress = 0.85f
                                            renderLogText += "\n[4/5] 🎵 Mixing audio with dynamic ducking (BGM 0.35x during narration)..."
                                            delay(350)
                                            renderProgress = 1.0f
                                            renderLogText += "\n[5/5] ✅ Live Render Complete! Output: 1080x1920 MP4 ready on timeline."
                                            delay(300)
                                            isSimulatingRender = false
                                            viewportMode = "VIDEO"
                                            isPlaying = true
                                            Toast.makeText(context, "✅ Rendered video from Python code! Playing now.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        "SCRIPT" -> {
                            LiveScriptView(script = currentProject.script)
                        }
                        "CATALOG" -> {
                            LiveTelegramCatalogView(edlWhy = currentProject.activeEditPlan?.why, clips = currentProject.clips)
                        }
                        else -> {
                            val activeClip = currentProject.clips.getOrNull(currentProject.selectedClipIndex)
                                ?: currentProject.clips.firstOrNull()

                            Box(modifier = Modifier.fillMaxSize()) {
                                if (activeClip?.uri != null) {
                                    AsyncImage(
                                        model = activeClip.uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clickable { isPlaying = !isPlaying }
                                    )
                                } else {
                                    LiveAutonomousVideoViewport(
                                        project = currentProject,
                                        isPlaying = isPlaying,
                                        onTogglePlay = { isPlaying = !isPlaying },
                                        onGenerateFromPrompt = { handleGenerateFromPrompt(it) },
                                        isGenerating = isGeneratingFromPrompt,
                                        generatingStatus = promptGenerationStatus
                                    )
                                }

                                // Picture in Picture (PIP) overlay preview if active
                                if (currentProject.overlayMediaUri != null && currentProject.activePipPosition != "None") {
                                    val pipAlignment = when (currentProject.activePipPosition) {
                                        "Bottom-Right" -> Alignment.BottomEnd
                                        "Split-Screen" -> Alignment.CenterEnd
                                        else -> Alignment.TopEnd
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF00DAF3)),
                                        color = Color.Black.copy(alpha = currentProject.overlayOpacity),
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(width = 65.dp, height = 90.dp)
                                            .align(pipAlignment)
                                    ) {
                                        AsyncImage(
                                            model = Uri.parse(currentProject.overlayMediaUri),
                                            contentDescription = "PIP Overlay",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                // Top right fullscreen toggle
                                IconButton(
                                    onClick = { showFullscreenPlayer = true },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(16.dp))
                                }

                                // Bottom left timecode pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(formatSeconds(currentProject.currentPositionSeconds), color = Color(0xFF00DAF3), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Text(" / ${formatSeconds(currentProject.totalDurationSeconds)}", color = Color.White.copy(alpha = 0.6f), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. Transport Controls Row (44px) ---
            Surface(
                color = Color(0xFF0E0E0E).copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatSeconds(currentProject.currentPositionSeconds), color = Color(0xFF00DAF3), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(" / ${formatSeconds(currentProject.totalDurationSeconds)}", color = Color(0xFFCCC3D9), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    // Transport Play/Pause Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val next = (currentProject.currentPositionSeconds - 5f).coerceAtLeast(0f)
                                currentProject = currentProject.copy(currentPositionSeconds = next)
                                Toast.makeText(context, "-5s", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Replay5, contentDescription = "-5s", tint = Color(0xFFCCC3D9), modifier = Modifier.size(20.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF7B2BFF), Color(0xFF006CEB))
                                    )
                                )
                                .clickable {
                                    val safeTotal = if (currentProject.totalDurationSeconds > 0.5f) {
                                        currentProject.totalDurationSeconds
                                    } else {
                                        val clipSum = currentProject.clips.sumOf { it.durationSeconds.toDouble() }.toFloat()
                                        if (clipSum > 0.5f) clipSum else 15.0f
                                    }
                                    if (!isPlaying) {
                                        val startPos = if (currentProject.currentPositionSeconds >= safeTotal - 0.25f) 0f else currentProject.currentPositionSeconds
                                        currentProject = currentProject.copy(currentPositionSeconds = startPos, totalDurationSeconds = safeTotal)
                                        isPlaying = true
                                    } else {
                                        isPlaying = false
                                        com.example.utils.TTSManager.stop()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val next = (currentProject.currentPositionSeconds + 5f).coerceAtMost(currentProject.totalDurationSeconds)
                                currentProject = currentProject.copy(currentPositionSeconds = next)
                                Toast.makeText(context, "+5s", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Forward5, contentDescription = "+5s", tint = Color(0xFFCCC3D9), modifier = Modifier.size(20.dp))
                        }
                    }

                    // Transport Actions: Split & Live Co-Director "Yahan se badlo" (Level 22) + Cut Delete
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                isPlaying = false
                                activeSubView = VideoStudioSubView.AI_DIRECTOR_CHAT
                                Toast.makeText(context, "📍 Co-Director pinned at ${formatSeconds(currentProject.currentPositionSeconds)}", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1B1528),
                            border = BorderStroke(1.dp, Color(0x6000DAF3))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PinDrop, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Yahan se badlo", color = Color(0xFF00DAF3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Split Button (Intelligent Playhead Split)
                        Surface(
                            onClick = { applySmartSplit() },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF262530),
                            border = BorderStroke(1.dp, Color(0x8000DAF3))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Split ✂️", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Cut / Delete Button (Ripple Delete)
                        Surface(
                            onClick = { applyCutDelete() },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF38151E),
                            border = BorderStroke(1.dp, Color(0x60FF4D6D))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Cut 🗑️", color = Color(0xFFFF4D6D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // --- 4. Signature SAIF AI EDITING Pill Row (56px) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main SAIF AI EDITING Button
                Surface(
                    onClick = { showSaifAiEditingModal = true },
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF7B2BFF),
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, Color(0x6000DAF3)),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF7B2BFF),
                                        Color(0xFF006CEB),
                                        Color(0xFF7B2BFF)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SAIF AI EDITING",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = "v2.4",
                                    color = Color(0xFF00DAF3),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Chat Bubble Director Shortcut
                IconButton(
                    onClick = { activeSubView = VideoStudioSubView.AI_DIRECTOR_CHAT },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = "AI Director Chat",
                        tint = Color(0xFFD1BCFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // --- 5. Multi-Track Timeline (220px) ---
            Surface(
                color = Color(0xFF0A0A0A),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Timeline Track Info Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0E0E0E))
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ViewTimeline, contentDescription = null, tint = Color(0xFFCCC3D9), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("5 TRACKS", color = Color(0xFFCCC3D9), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(" · ", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                            Text("SNAP ON", color = Color(0xFFCCC3D9), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("100%", color = Color(0xFF00DAF3), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF2A2A2A)) {
                                Text("FIT", color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }

                    // Ticker / Ruler
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0E0E0E))
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("00:00", "00:05", "00:10", "00:15", "00:20").forEach { mark ->
                            Text(mark, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Quick Action Editing Toolbar for Selected Clip
                    if (currentProject.clips.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF14131A))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Selected: ${currentProject.clips.getOrNull(currentProject.selectedClipIndex)?.name ?: "Clip"}",
                                color = Color(0xFFCCC3D9),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { applySmartSplit() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF262530),
                                    border = BorderStroke(1.dp, Color(0x6000DAF3))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Split", color = Color(0xFF00DAF3), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Surface(
                                    onClick = { applyCutHead() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF262530)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("✂️◀ Head", color = Color.White, fontSize = 8.5.sp)
                                    }
                                }
                                Surface(
                                    onClick = { applyCutTail() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF262530)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Tail ▶✂️", color = Color.White, fontSize = 8.5.sp)
                                    }
                                }
                                Surface(
                                    onClick = { applyDuplicateClip() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF262530)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("+ Dup", color = Color.White, fontSize = 8.5.sp)
                                    }
                                }
                                Surface(
                                    onClick = { applyCutDelete() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF4A151E),
                                    border = BorderStroke(1.dp, Color(0x60FF4D6D))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Del", color = Color(0xFFFF4D6D), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Multi-Track Container with Red/Cyan Playhead
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0D0D0D))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Track 1: Video Track
                            Row(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF201F1F).copy(alpha = 0.6f))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentProject.clips.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF7B2BFF).copy(alpha = 0.25f),
                                        modifier = Modifier
                                            .width(180.dp)
                                            .fillMaxHeight()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("Blank Timeline Canvas", color = Color(0xFF00DAF3), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    currentProject.clips.forEachIndexed { index, clip ->
                                        val isSelected = index == currentProject.selectedClipIndex
                                        val clipWidth = (clip.durationSeconds * 22).dp.coerceIn(95.dp, 300.dp)
                                        Surface(
                                            onClick = {
                                                var startOfClip = 0f
                                                for (k in 0 until index) {
                                                    startOfClip += currentProject.clips[k].durationSeconds
                                                }
                                                val endOfClip = startOfClip + clip.durationSeconds
                                                val newPos = if (currentProject.currentPositionSeconds in startOfClip..endOfClip) {
                                                    currentProject.currentPositionSeconds
                                                } else {
                                                    startOfClip
                                                }
                                                currentProject = currentProject.copy(selectedClipIndex = index, currentPositionSeconds = newPos)
                                                activeToolTab = VideoStudioToolTab.EDIT
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) Color(0xFF7B2BFF).copy(alpha = 0.5f) else Color(0xFF7B2BFF).copy(alpha = 0.2f),
                                            border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF00DAF3)) else null,
                                            modifier = Modifier
                                                .width(clipWidth)
                                                .fillMaxHeight()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(4.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(clip.name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                                                    if (clip.name.contains("_cut") || clip.name.contains("_part")) {
                                                        Surface(
                                                            shape = RoundedCornerShape(2.dp),
                                                            color = Color(0xFF00DAF3).copy(alpha = 0.25f),
                                                            modifier = Modifier.padding(end = 2.dp)
                                                        ) {
                                                            Text("✂️ CUT", color = Color(0xFF00DAF3), fontSize = 6.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 2.dp))
                                                        }
                                                    }
                                                    if (clip.isAiEnhanced || clip.origin == "ai") {
                                                        Surface(
                                                            onClick = {
                                                                val reason = clip.aiWhy.ifBlank { "Optimized by SAIF AI neural engine for dynamic pacing & visual impact." }
                                                                Toast.makeText(context, "✦ AI Rationale: $reason", Toast.LENGTH_LONG).show()
                                                            },
                                                            shape = RoundedCornerShape(2.dp),
                                                            color = Color(0xFFD1BCFF)
                                                        ) {
                                                            Text("AI ✦", color = Color(0xFF3D008F), fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 2.dp))
                                                        }
                                                    }
                                                }
                                                Text("Speed ${clip.speed}x · ${"%.1f".format(clip.durationSeconds)}s", color = Color(0xFF9CF0FF), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }

                                // + Button to add 50+ media from gallery
                                Surface(
                                    onClick = {
                                        addMoreMediaLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2A2A2A),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .fillMaxHeight()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Media", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Track 2: Audio Track (Waveform visualization, clickable to open Audio tab)
                            Surface(
                                onClick = { activeToolTab = VideoStudioToolTab.AUDIO },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF201F1F).copy(alpha = 0.6f),
                                modifier = Modifier
                                    .height(34.dp)
                                    .width(360.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF35E6A0).copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF35E6A0), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Decorative waveform ticks
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(10, 16, 8, 20, 12, 18, 6, 22, 14, 18, 10, 16, 24, 12, 19, 8, 14, 20, 10, 15).forEach { h ->
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(h.dp)
                                                    .clip(RoundedCornerShape(1.dp))
                                                    .background(Color(0xFF35E6A0).copy(alpha = 0.8f))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(currentProject.audioTrackName, color = Color(0xFF35E6A0), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                }
                            }

                            // Track 2B: SFX & Sound Effects Track (Telegram Catalog SFX)
                            val sfxItems = currentProject.timelineMarkers.filter { it.label.startsWith("🔊") }
                            Surface(
                                onClick = { activeToolTab = VideoStudioToolTab.AUDIO },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF201F1F).copy(alpha = 0.6f),
                                modifier = Modifier
                                    .height(30.dp)
                                    .width(360.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFFF7AD9).copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFFF7AD9), modifier = Modifier.size(13.dp))
                                    Text("SFX", color = Color(0xFFFF7AD9), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    if (sfxItems.isEmpty()) {
                                        listOf("🔊 Whoosh (0s)", "🔊 Riser (3.5s)", "🔊 Ding (10.5s)", "🔊 Pop (14.5s)").forEach { sfx ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFF7AD9).copy(alpha = 0.25f),
                                                border = BorderStroke(0.5.dp, Color(0xFFFF7AD9))
                                            ) {
                                                Text(sfx, color = Color.White, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    } else {
                                        sfxItems.forEach { sfx ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFF7AD9).copy(alpha = 0.25f),
                                                border = BorderStroke(0.5.dp, Color(0xFFFF7AD9))
                                            ) {
                                                Text("${sfx.label} (${"%.1f".format(sfx.timestampSeconds)}s)", color = Color.White, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Track 3: Text Track (Clickable to open Text tool)
                            Surface(
                                onClick = { activeToolTab = VideoStudioToolTab.TEXT },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF201F1F).copy(alpha = 0.6f),
                                modifier = Modifier
                                    .height(34.dp)
                                    .width(360.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 70.dp, end = 100.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFFC44D).copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Title, contentDescription = null, tint = Color(0xFFFFC44D), modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(currentProject.textOverlay, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                    Text(currentProject.textAnimation, color = Color(0xFFFFC44D).copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Track 4: Effects / Filter Track (Clickable to open Effects sheet)
                            Surface(
                                onClick = { activeToolTab = VideoStudioToolTab.EFFECTS },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF201F1F).copy(alpha = 0.6f),
                                modifier = Modifier
                                    .height(34.dp)
                                    .width(360.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 35.dp, end = 140.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFF7AD9).copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Flare, contentDescription = null, tint = Color(0xFFFF7AD9), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(currentProject.effectOverlay, color = Color(0xFFFF7AD9), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                }
                            }

                            // Track 5: AI Auto-Captions (When active)
                            if (currentProject.autoCaptions.isNotEmpty()) {
                                Surface(
                                    onClick = { Toast.makeText(context, "AI Subtitles synced with karaoke highlights", Toast.LENGTH_SHORT).show() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF201F1F).copy(alpha = 0.6f),
                                    modifier = Modifier.height(30.dp).width(360.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF00DAF3).copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color(0xFF00DAF3), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI CAPTIONS • ${currentProject.autoCaptions.size} Lines", color = Color(0xFF00DAF3), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            // Track 6: AI B-Roll Visuals (When active)
                            if (currentProject.bRollItems.isNotEmpty()) {
                                Surface(
                                    onClick = { Toast.makeText(context, "AI B-Roll cutaway sequence active", Toast.LENGTH_SHORT).show() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF201F1F).copy(alpha = 0.6f),
                                    modifier = Modifier.height(30.dp).width(360.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF7B2BFF).copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFFD1BCFF), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI B-ROLL • ${currentProject.bRollItems.size} Cutaways", color = Color(0xFFD1BCFF), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        // Playhead Vertical Bar positioned dynamically based on time
                        val playheadOffset = ((currentProject.currentPositionSeconds / currentProject.totalDurationSeconds.coerceAtLeast(1f)) * 260.dp.value).dp.coerceIn(10.dp, 300.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .align(Alignment.CenterStart)
                                .offset(x = playheadOffset)
                                .background(Color(0xFF00DAF3))
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00DAF3))
                                    .align(Alignment.TopCenter)
                            )
                        }
                    }
                }
            }

            // --- Tool Drawer / Sub-panel (When active) ---
            AnimatedVisibility(
                visible = activeToolTab != VideoStudioToolTab.NONE,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                VideoStudioToolContainer(
                    activeTab = activeToolTab,
                    project = currentProject,
                    onUpdateProject = { updated -> pushState(updated) },
                    onCloseTab = { activeToolTab = VideoStudioToolTab.NONE }
                )
            }

            // --- 6. Bottom Workspace Toolbar (64px) ---
            Surface(
                color = Color(0xFF121212).copy(alpha = 0.98f),
                shadowElevation = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val toolbarItems = listOf(
                        Pair(VideoStudioToolTab.EDIT, Pair("Edit", Icons.Default.ContentCut)),
                        Pair(VideoStudioToolTab.AUDIO, Pair("Audio", Icons.Default.MusicNote)),
                        Pair(VideoStudioToolTab.TEXT, Pair("Text", Icons.Default.Title)),
                        Pair(VideoStudioToolTab.OVERLAY, Pair("Overlay", Icons.Default.Layers)),
                        Pair(VideoStudioToolTab.EFFECTS, Pair("Effects", Icons.Default.AutoAwesome)),
                        Pair(VideoStudioToolTab.FILTERS, Pair("Filters", Icons.Default.Palette)),
                        Pair(VideoStudioToolTab.ADJUST, Pair("Adjust", Icons.Default.Tune)),
                        Pair(VideoStudioToolTab.RATIO, Pair("Ratio", Icons.Default.AspectRatio)),
                        Pair(VideoStudioToolTab.PRO_TOOLS, Pair("Pro AI", Icons.Default.ElectricBolt))
                    )

                    toolbarItems.forEach { (tab, item) ->
                        val (label, icon) = item
                        val isActive = activeToolTab == tab
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    activeToolTab = if (activeToolTab == tab) VideoStudioToolTab.NONE else tab
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isActive) Color(0xFF00DAF3) else Color(0xFFCCC3D9),
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = if (isActive) Color.White else Color(0xFFCCC3D9),
                                fontSize = 10.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00DAF3))
                                )
                            }
                        }
                    }
                }
            }
        }

        // SubScreen Overlays:
        // 1. AI Director Chatting interface
        AnimatedVisibility(
            visible = activeSubView == VideoStudioSubView.AI_DIRECTOR_CHAT,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            VideoAiDirectorChatScreen(
                project = currentProject,
                onBack = { activeSubView = VideoStudioSubView.NONE },
                onApplyChanges = { updated ->
                    pushState(updated)
                }
            )
        }

        // 2. Export Studio interface
        AnimatedVisibility(
            visible = activeSubView == VideoStudioSubView.EXPORT_STUDIO,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            VideoExportStudioScreen(
                project = currentProject,
                onBack = { activeSubView = VideoStudioSubView.NONE }
            )
        }

        // 3. Fullscreen Player Dialog
        if (showFullscreenPlayer) {
            VideoFullscreenPlayerDialog(
                project = currentProject,
                isPlaying = isPlaying,
                onTogglePlay = { isPlaying = !isPlaying },
                onSeek = { currentProject = currentProject.copy(currentPositionSeconds = it) },
                onReplay5s = {
                    currentProject = currentProject.copy(currentPositionSeconds = (currentProject.currentPositionSeconds - 5f).coerceAtLeast(0f))
                },
                onForward5s = {
                    currentProject = currentProject.copy(currentPositionSeconds = (currentProject.currentPositionSeconds + 5f).coerceAtMost(currentProject.totalDurationSeconds))
                },
                onClose = { showFullscreenPlayer = false }
            )
        }

        // 4. Resolution Quality Dialog
        if (showResolutionDialog) {
            VideoResolutionQualityDialog(
                project = currentProject,
                onDismiss = { showResolutionDialog = false },
                onConfirm = { res, fps ->
                    pushState(currentProject.copy(resolution = res, fps = fps))
                }
            )
        }

        // 5. SAIF AI EDITING Master Modal (Levels 5, 8, 10, 15, 18, 6)
        if (showSaifAiEditingModal) {
            SaifAiEditingModal(
                project = currentProject,
                onDismiss = { showSaifAiEditingModal = false },
                onApplyEdit = { updated ->
                    pushState(updated)
                    showSaifAiEditingModal = false
                }
            )
        }

        // 6. Floating AI Prompt Video Director Dialog
        if (showPromptDirectorDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { if (!isGeneratingFromPrompt) showPromptDirectorDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { if (!isGeneratingFromPrompt) showPromptDirectorDialog = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF181528),
                        border = BorderStroke(1.5.dp, Color(0xFF00DAF3)),
                        shadowElevation = 24.dp
                    ) {
                        var dialogPromptInput by remember { mutableStateOf("") }
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF7B2BFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("AI Prompt Video Director", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("Research topic & synthesize video", color = Color(0xFF00DAF3), fontSize = 11.sp)
                                    }
                                }
                                IconButton(
                                    onClick = { if (!isGeneratingFromPrompt) showPromptDirectorDialog = false },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x25FFFFFF))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = dialogPromptInput,
                                onValueChange = { dialogPromptInput = it },
                                placeholder = { Text("e.g. 5 AI tools jo 2026 me sab badal denge / YouTube growth secrets", color = Color.Gray, fontSize = 12.sp) },
                                enabled = !isGeneratingFromPrompt,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00DAF3),
                                    unfocusedBorderColor = Color(0x40FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF100D1E),
                                    unfocusedContainerColor = Color(0xFF100D1E)
                                ),
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "🔥 5 AI Tools in 2026",
                                    "📈 YouTube Growth Secret",
                                    "🧠 Quantum Computing",
                                    "💰 Crypto vs Stocks"
                                ).forEach { suggestion ->
                                    Surface(
                                        onClick = { dialogPromptInput = suggestion },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF261F3B),
                                        border = BorderStroke(0.5.dp, Color(0x5000DAF3))
                                    ) {
                                        Text(suggestion, color = Color(0xFFE2E8F0), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isGeneratingFromPrompt) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF231C38), RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00DAF3), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(promptGenerationStatus.ifBlank { "Analyzing prompt & researching..." }, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val p = dialogPromptInput.trim()
                                        if (p.isNotBlank()) {
                                            showPromptDirectorDialog = false
                                            handleGenerateFromPrompt(p)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2BFF)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Research & Generate Video", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
