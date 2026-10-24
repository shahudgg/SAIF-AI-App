package com.example.studio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.studio.model.TextItem
import com.example.studio.model.TrackType
import com.example.studio.state.StudioAction
import com.example.studio.state.StudioViewModel
import java.util.UUID

@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onBack: () -> Unit,
    onExportClick: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val screenWidthPx = configuration.screenWidthDp * density

    var showExportDialog by remember { mutableStateOf(false) }
    var showTuneInspector by remember { mutableStateOf(false) }
    var showAiVideoModal by remember { mutableStateOf(false) }
    var showVoiceoverModal by remember { mutableStateOf(false) }
    var showCodeEngineModal by remember { mutableStateOf(false) }
    var showAiProModal by remember { mutableStateOf(false) }
    var showSaifAiHubModal by remember { mutableStateOf(false) }

    val selectedItem = state.project.items.firstOrNull { it.id == state.selectedItemId }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            viewModel.importMediaUri(uri, isPhoto = false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09080E))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Bar
            StudioTopBar(
                state = state,
                onBack = onBack,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onAspectChange = { viewModel.setAspect(it) },
                onExportClick = { showExportDialog = true }
            )

            // 2. Resizable Pro Preview Panel with On-Canvas Gizmo
            StudioPreviewPanel(
                state = state,
                clock = viewModel.clock,
                onHeightPercentChange = { viewModel.setPreviewHeightPercent(it) },
                onToggleFocus = { viewModel.toggleFocusMode() },
                onToggleFullscreen = { viewModel.toggleFullscreen() },
                onToggleSafeZone = { viewModel.toggleSafeZone() },
                onToggleThirds = { viewModel.toggleThirdsGrid() },
                onTransformChange = { newTransform ->
                    state.selectedItemId?.let { id ->
                        viewModel.commit(StudioAction.UpdateItemTransform(id, newTransform))
                    }
                },
                modifier = Modifier.weight(if (state.isFocusMode) 1f else state.previewHeightPercent)
            )

            if (!state.isFocusMode) {
                // 3. Transport Controls Bar
                StudioTransportBar(
                    state = state,
                    clock = viewModel.clock,
                    onToggleProxyLive = { /* Live / Proxy Mode Toggle */ }
                )

                // 4. Multi-Lane Timeline Panel
                StudioTimelinePanel(
                    state = state,
                    clock = viewModel.clock,
                    onSelectItem = { id ->
                        viewModel.selectItem(id)
                        if (id != null) showTuneInspector = true
                    },
                    onTrimItem = { id, startUs, durUs ->
                        viewModel.commit(StudioAction.TrimItem(id, startUs, durUs))
                    },
                    onMoveItem = { id, startUs, laneId ->
                        viewModel.commit(StudioAction.MoveItem(id, startUs, laneId))
                    },
                    onAddMediaToLane = { trackType ->
                        when (trackType) {
                            TrackType.VIDEO, TrackType.VISUAL -> {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                            TrackType.TEXT -> {
                                val textLaneId = state.project.lanes.firstOrNull { it.trackType == TrackType.TEXT }?.id ?: "lane_text_1"
                                val newItem = TextItem(
                                    id = UUID.randomUUID().toString(),
                                    laneId = textLaneId,
                                    startUs = viewModel.clock.currentTimeUs.value,
                                    durationUs = 3_000_000L,
                                    text = "Sample Subtitle",
                                    label = "Subtitle"
                                )
                                viewModel.commit(StudioAction.AddItem(newItem))
                                viewModel.selectItem(newItem.id)
                            }
                            else -> {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                        }
                    },
                    onZoomChange = { viewModel.setZoom(it) },
                    modifier = Modifier.weight(1f - state.previewHeightPercent)
                )

                // 5. Contextual Bottom Toolbar (≥48dp touch targets)
                StudioToolbar(
                    state = state,
                    onSplit = { viewModel.splitSelectedItemAtPlayhead() },
                    onTrimLeft = {
                        state.selectedItemId?.let { id ->
                            viewModel.commit(StudioAction.TrimLeftToPlayhead(id, viewModel.clock.currentTimeUs.value))
                        }
                    },
                    onTrimRight = {
                        state.selectedItemId?.let { id ->
                            viewModel.commit(StudioAction.TrimRightToPlayhead(id, viewModel.clock.currentTimeUs.value))
                        }
                    },
                    onDuplicate = {
                        state.selectedItemId?.let { id ->
                            viewModel.commit(StudioAction.DuplicateItem(id))
                        }
                    },
                    onBringForward = {
                        state.selectedItemId?.let { id ->
                            viewModel.commit(StudioAction.BringForward(id))
                        }
                    },
                    onSendBackward = {
                        state.selectedItemId?.let { id ->
                            viewModel.commit(StudioAction.SendBackward(id))
                        }
                    },
                    onDelete = {
                        state.selectedItemId?.let { id ->
                            viewModel.commit(StudioAction.RippleDelete(id))
                            viewModel.selectItem(null)
                        }
                    },
                    onToggleTune = { showTuneInspector = !showTuneInspector },
                    onAddMedia = {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onAddText = {
                        val textLaneId = state.project.lanes.firstOrNull { it.trackType == TrackType.TEXT }?.id ?: "lane_text_1"
                        val newItem = TextItem(
                            id = UUID.randomUUID().toString(),
                            laneId = textLaneId,
                            startUs = viewModel.clock.currentTimeUs.value,
                            durationUs = 3_000_000L,
                            text = "New Subtitle",
                            label = "Subtitle"
                        )
                        viewModel.commit(StudioAction.AddItem(newItem))
                        viewModel.selectItem(newItem.id)
                        showTuneInspector = true
                    },
                    onOpenAiPro = { showAiProModal = true },
                    onOpenSaifAi = { showSaifAiHubModal = true },
                    onAddMarker = { viewModel.addMarkerAtPlayhead() },
                    onZoomIn = { viewModel.zoomIn() },
                    onZoomOut = { viewModel.zoomOut() },
                    onFit = { viewModel.fitProject(screenWidthPx, density) }
                )
            }
        }

        // Tune Inspector Peek Bottom Sheet
        if (showTuneInspector && selectedItem != null && !state.isFocusMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                StudioTuneInspector(
                    item = selectedItem,
                    viewModel = viewModel,
                    onClose = { showTuneInspector = false }
                )
            }
        }

        // Real Video Export Dialog
        if (showExportDialog) {
            StudioExportDialog(
                project = state.project,
                currentPlayheadUs = viewModel.clock.currentTimeUs.value,
                onDismiss = { showExportDialog = false }
            )
        }

        // Level 3: AI Prompt-to-Video Scene Modal
        if (showAiVideoModal) {
            StudioAiVideoModal(
                viewModel = viewModel,
                onDismiss = { showAiVideoModal = false }
            )
        }

        // Level 3: AI Neural Voiceover Studio Modal
        if (showVoiceoverModal) {
            StudioVoiceoverModal(
                viewModel = viewModel,
                onDismiss = { showVoiceoverModal = false }
            )
        }

        // Level 5: Studio Code Engine Modal
        if (showCodeEngineModal) {
            StudioCodeEngineModal(
                viewModel = viewModel,
                onDismiss = { showCodeEngineModal = false }
            )
        }

        // Level 6: Studio AI Pro Intelligence Modal (Silence Auto-Cut, Auto-Captions, Ducking, Reframe)
        if (showAiProModal) {
            StudioAiProModal(
                viewModel = viewModel,
                onDismiss = { showAiProModal = false }
            )
        }

        // SAIF AI Master Hub Modal (ZERO TO VIDEO, VIDEO TO EDIT, VIDEO EDIT WITH AI VOICES, VOICE OVER TO VIDEO)
        if (showSaifAiHubModal) {
            StudioSaifAiHubModal(
                viewModel = viewModel,
                onDismiss = { showSaifAiHubModal = false }
            )
        }
    }
}
