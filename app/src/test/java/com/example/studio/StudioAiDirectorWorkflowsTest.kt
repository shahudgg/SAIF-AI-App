package com.example.studio

import com.example.studio.ai.StudioAiDirectorEngine
import com.example.studio.model.AiWorkflowType
import com.example.studio.model.SaifAiWorkspaceMode
import com.example.studio.model.StudioProject
import com.example.studio.model.TrackType
import com.example.studio.model.WorkflowProjectBrief
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioAiDirectorWorkflowsTest {

    @Test
    fun testModeSwitchingAndWorkflowTypes() {
        val modes = SaifAiWorkspaceMode.values()
        assertEquals(2, modes.size)
        assertTrue(modes.contains(SaifAiWorkspaceMode.AI_WORKFLOWS))
        assertTrue(modes.contains(SaifAiWorkspaceMode.CLASSIC_TOOLS))

        val workflows = AiWorkflowType.values()
        assertEquals(4, workflows.size)
        assertTrue(workflows.contains(AiWorkflowType.ZERO_TO_VIDEO))
        assertTrue(workflows.contains(AiWorkflowType.VIDEO_TO_EDITING))
        assertTrue(workflows.contains(AiWorkflowType.VIDEO_EDIT_WITH_AI_VOICE))
        assertTrue(workflows.contains(AiWorkflowType.VOICEOVER_TO_VIDEO))
    }

    @Test
    fun testInitialQuestionsGenerationForAllWorkflows() {
        for (wf in AiWorkflowType.values()) {
            val questions = StudioAiDirectorEngine.generateInitialQuestions(wf)
            assertTrue("Questions for $wf should not be empty", questions.isNotEmpty())
            assertTrue("Questions for $wf should contain between 2 and 5 cards", questions.size in 2..5)

            for (q in questions) {
                assertNotNull(q.id)
                assertTrue(q.question.isNotBlank())
                assertTrue(q.presets.isNotEmpty())
            }
        }
    }

    @Test
    fun testSpokenWordCountBudgetCalculation() {
        val briefShort = WorkflowProjectBrief(
            workflowType = AiWorkflowType.ZERO_TO_VIDEO,
            durationSeconds = 30f,
            speechPace = 1.0f
        )
        // 30 seconds at 140 WPM: (30 / 60) * 140 = 70 words
        assertEquals(70, briefShort.estimateSpokenWordCount())

        val briefLong = WorkflowProjectBrief(
            workflowType = AiWorkflowType.VIDEO_EDIT_WITH_AI_VOICE,
            durationSeconds = 60f,
            speechPace = 1.0f
        )
        // 60 seconds at 140 WPM: (60 / 60) * 140 = 140 words
        assertEquals(140, briefLong.estimateSpokenWordCount())
    }

    @Test
    fun testZeroToVideoWorkflowAssembly() {
        val project = StudioProject(id = "test_p1", name = "Test Project")
        val brief = WorkflowProjectBrief(
            workflowType = AiWorkflowType.ZERO_TO_VIDEO,
            topicOrScript = "Artificial General Intelligence",
            durationSeconds = 30f,
            visualStyle = "Cinematic 35mm"
        )

        val updated = StudioAiDirectorEngine.synthesizeAlgorithmicProject(
            baseProject = project,
            brief = brief
        )

        // Verify project updated
        assertTrue(updated.items.isNotEmpty())
        val videoLane = updated.lanes.firstOrNull { it.trackType == TrackType.VIDEO }
        val textLane = updated.lanes.firstOrNull { it.trackType == TrackType.TEXT }
        val voiceLane = updated.lanes.firstOrNull { it.trackType == TrackType.VOICE }
        val audioLane = updated.lanes.firstOrNull { it.trackType == TrackType.AUDIO }

        val hasVisual = updated.items.any { it.laneId == (videoLane?.id ?: "lane_video_1") }
        val hasText = updated.items.any { it.laneId == (textLane?.id ?: "lane_text_1") }
        val hasVoice = updated.items.any { it.laneId == (voiceLane?.id ?: "lane_voice_1") }
        val hasBgm = updated.items.any { it.laneId == (audioLane?.id ?: "lane_audio_1") }

        assertTrue("Zero to Video must generate visuals", hasVisual)
        assertTrue("Zero to Video must generate text/titles", hasText)
        assertTrue("Zero to Video must generate voiceover items", hasVoice)
        assertTrue("Zero to Video must generate bgm item", hasBgm)
    }

    @Test
    fun testVideoToEditingPreservesNarrationAndAudio() {
        val project = StudioProject(id = "test_p2", name = "Raw Footage Project")
        val brief = WorkflowProjectBrief(
            workflowType = AiWorkflowType.VIDEO_TO_EDITING,
            topicOrScript = "Fast paced action vlog cut",
            durationSeconds = 30f,
            visualStyle = "Cyberpunk Neon"
        )

        val updated = StudioAiDirectorEngine.synthesizeAlgorithmicProject(
            baseProject = project,
            brief = brief
        )

        val voiceLane = updated.lanes.firstOrNull { it.trackType == TrackType.VOICE }
        val textLane = updated.lanes.firstOrNull { it.trackType == TrackType.TEXT }

        // In VIDEO_TO_EDITING: MUST NOT generate replacement voiceover in voiceLane
        val hasReplacementVoice = updated.items.any { it.laneId == (voiceLane?.id ?: "lane_voice_1") }
        assertFalse("Video to Editing must not generate replacement voiceover", hasReplacementVoice)

        // But must generate kinetic titles and cuts
        val hasTitles = updated.items.any { it.laneId == (textLane?.id ?: "lane_text_1") }
        assertTrue("Video to Editing must generate kinetic text overlays", hasTitles)
    }

    @Test
    fun testVoiceoverToVideoWorkflowPreservesVoice() {
        val project = StudioProject(id = "test_p3", name = "Existing Voice Project")
        val brief = WorkflowProjectBrief(
            workflowType = AiWorkflowType.VOICEOVER_TO_VIDEO,
            topicOrScript = "Generate visuals and subtitles for uploaded podcast audio",
            durationSeconds = 30f,
            userAudioUri = "file:///storage/audio/podcast.mp3"
        )

        val updated = StudioAiDirectorEngine.synthesizeAlgorithmicProject(
            baseProject = project,
            brief = brief
        )

        val videoLane = updated.lanes.firstOrNull { it.trackType == TrackType.VIDEO }
        val textLane = updated.lanes.firstOrNull { it.trackType == TrackType.TEXT }
        val voiceLane = updated.lanes.firstOrNull { it.trackType == TrackType.VOICE }

        // Visuals and captions must be created to match the voice
        val hasVisual = updated.items.any { it.laneId == (videoLane?.id ?: "lane_video_1") }
        val hasSubtitles = updated.items.any { it.laneId == (textLane?.id ?: "lane_text_1") }
        val hasAuthoritativeVoice = updated.items.any {
            it.laneId == (voiceLane?.id ?: "lane_voice_1") && it.origin == "authoritative_user_voice"
        }

        assertTrue("Voiceover to Video must generate matching scenes", hasVisual)
        assertTrue("Voiceover to Video must generate subtitles", hasSubtitles)
        assertTrue("Voiceover to Video must preserve authoritative voice", hasAuthoritativeVoice)
    }
}
