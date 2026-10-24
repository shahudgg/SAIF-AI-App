MASTER BUILD PROMPT --- SAIF AI AUTONOMOUS VIDEO CREATION & EDITING
STUDIO

You are a senior Android engineer, AI video-generation architect,
multimodal AI engineer, motion-graphics developer, and professional
video-editing systems designer.

Your task is to inspect my uploaded SAIF AI Android project ZIP,
understand its existing architecture, and implement a fully integrated,
practical, production-oriented AI video creation and editing system
inside the existing application.

Do not just write a plan, create mockups, or provide sample code. Make
the actual changes to the project files, integrate the features with the
existing app, build the project, run tests, fix errors, and deliver the
modified project.

You are allowed to improve the architecture, refactor relevant
components, add advanced capabilities, and make necessary design
decisions independently, provided you preserve existing working
features.

1.  NON-NEGOTIABLE RULES

2.  First inspect the complete project structure and understand the
    current Android app architecture.

3.  Identify the existing home screen, purple plus (+) button,
    new-project flow, editor screen, SAIF AI entry point, existing four
    options, AI API configuration, voice system, Telegram asset manager,
    timeline, preview renderer, and export engine.

4.  Reuse existing components whenever appropriate.

5.  Do not replace functioning features with dummy screens or fake
    implementations.

6.  Do not create a separate disconnected demo application.

7.  Do not hardcode one AI provider as the only supported provider.

8.  Do not assume every user-supplied API supports image generation,
    video analysis, web search, speech synthesis, or code generation.

9.  Never display fake progress or claim that an asset, video, voice, or
    export has been generated when it has not.

10. Do not remove unrelated existing features, user data,
    authentication, project history, or settings.

11. Preserve the existing design language, app navigation, project
    format, and compatibility wherever possible.

12. Avoid hardcoded video templates. Every project must be generated
    from the user's topic, footage, narration, research, selected style,
    and creative requirements.

13. All new features must connect to real application state and actual
    processing pipelines.

14. Do not leave TODOs, empty handlers, placeholder buttons, or mock
    data in the core feature paths.

15. If a requested capability cannot be completed with the current
    infrastructure, implement the strongest real fallback possible and
    clearly report the limitation.

16. Never expose, print, log, commit, or transmit API keys, bot tokens,
    passwords, or other secrets unnecessarily.

17. EXISTING PROJECT COMPONENTS TO INVESTIGATE

The ZIP contains relevant components with names including: -
ProjectEditorScreen.kt - ProjectEditorCoordinator.kt -
VideoAiDirectorChatScreen.kt - VideoAiVideoGeneratorDialog.kt -
VideoProjectCreateDialog.kt - VideoProjectModels.kt -
VideoTimelineStudioScreen.kt - StudioTimelinePanel.kt -
VideoStudioLiveRenderer.kt - VideoExportStudioScreen.kt -
VideoResolutionQualityDialog.kt - AndroidVideoExportEngine.kt -
TelegramAssetManager.kt - AIApiUtility.kt - StudioAiVideoModal.kt

These names are starting points, not assumptions about their
implementation. Inspect the actual code, dependencies, call sites, data
models, and existing tests before deciding what to change.

Also investigate the existing voice-generation system, script/code
editor, API provider selection, project persistence, media storage,
Android permissions, background tasks, and build configuration.

3.  NEW PROJECT FLOW AND EDITOR UI

When the user opens the home screen and presses the existing purple plus
(+) button: 1. Preserve the existing new-project creation flow. 2. When
the user creates a project, open the existing editing panel. 3. Add a
SAIF AI feature at the bottom of the editor's existing tool list. 4.
Tapping SAIF AI opens an AI workspace with a clearly visible mode
switch.

The workspace must support: - Existing editing options: retain the
original four options and their existing functionality. - AI mode:
display the new four AI video workflows described below.

Use a clean, premium, dark-glass interface consistent with the current
application. Keep the purple accent, readable typography, smooth
transitions, and a professional mobile layout.

Do not duplicate the editor or create competing navigation systems.
Integrate with the existing screen and navigation architecture.

The four AI workflows must be: 1. ZERO TO VIDEO 2. VIDEO TO EDITING 3.
VIDEO EDITING WITH AI VOICE 4. VOICE OVER TO VIDEO

Each option must open a separate workflow while sharing common AI
services, project models, asset management, timeline infrastructure, and
export functionality.

4.  BUILD A REAL CONVERSATIONAL AI DIRECTOR

All four workflows must have an intelligent chat interface that can
accept natural-language instructions, follow-up messages, uploaded
media, and project-specific constraints.

The AI must ask useful questions at the right stages instead of making
every creative decision blindly. Examples: - What is the topic or
purpose of the video? - What duration do you want? - Which language
should the narration use? - Who is the target audience? - What aspect
ratio should be used? - What editing style do you prefer? - Should the
video be educational, documentary, cinematic, comedic, motivational, or
another style? - Should subtitles be included? - Should the AI use
uploaded assets, find suitable available assets, or generate original
visuals? - Which voice style and speaking pace should be used? - What
level of motion graphics and sound design is appropriate? - Are there
any assets, phrases, scenes, or styles that must be avoided?

Do not ask every question on every project. Infer what is already known
from the user's prompt, uploaded files, and earlier answers. Ask only
questions that materially improve the result.

Conversational question cards: - Display important questions as
attractive selectable cards, chips, or compact option panels inside the
chat. - Where appropriate, support four sensible preset choices and a
fifth Custom option. - For duration selection, provide four useful
presets and a Custom duration input. Select sensible defaults for the
actual use case. - Custom must let users type their own requirements or
explain answers in detail. - After each answer, update the current
project configuration and continue without discarding earlier answers. -
Provide controls to confirm choices, edit earlier answers, skip optional
questions, cancel generation, pause/resume supported processing, revise
creative direction, and return to the timeline without losing
progress. - Save workflow state so users can leave and return later.

5.  WORKFLOW ONE --- ZERO TO VIDEO

This is the full autonomous text-to-video editing workflow.

Stage A: User prompt Accept short topic descriptions and long pasted
scripts, including examples such as "How to grow on YouTube," "How
artificial intelligence works," scientific explainers, documentaries,
educational videos, and scripts/files uploaded by the user.

Distinguish between: 1. A topic requiring research and original
scriptwriting. 2. A complete user-supplied script that should primarily
be preserved. 3. A partial script that needs expansion. 4. A brief
requiring clarification.

Never unnecessarily rewrite a complete script when the user wants it
preserved.

Stage B: Duration selection Ask the desired duration before writing the
final script. Offer four useful presets and Custom, with exact
minutes/seconds input. Estimate spoken-word count according to language
and pace, not a single fixed multiplier. Explain when the requested
duration is unrealistic and offer concise narration versus a longer,
more detailed treatment.

Stage C: Language selection Ask which language the video should use.
Support languages the configured model and voice provider can actually
process, including Hindi, English, and Hinglish where supported. Allow
separate settings for narration, on-screen text, subtitles, and
research/source material. Never silently convert the chosen language.

Stage D: Creative brief Ask only useful follow-ups: target audience,
platform, visual style, aspect ratio, narration, subtitles, music,
balance of live footage and motion graphics. Summarize the brief before
production.

Stage E: Research and scriptwriting For topic-based videos, research
current information through a real configured web-search/research
provider. Prefer fresh reliable sources for recent topics. Use official
documentation, primary sources, credible publications, and expert
sources where appropriate. Never claim to have searched if no search
provider exists.

For research-heavy videos: 1. Collect relevant information. 2. Verify
important claims against reliable sources. 3. Separate established facts
from opinions and uncertainty. 4. Record source URLs and relevant
publication dates. 5. Avoid fabricated statistics, quotes, references,
and facts. 6. Include citations or a source list in project research
metadata where applicable.

Generate a detailed script appropriate to duration, including a strong
hook, coherent narrative, clear explanations, examples, natural
transitions, scene-level narration, visual direction, sound-design
suggestions, and conclusion. Preserve user scripts when requested;
distinguish necessary edits from optional improvements. Let the user
revise/regenerate sections or approve. Do not start expensive rendering
before approval unless autonomous production is explicitly enabled.

Stage F: Automatic scene and timeline planning Convert approved script
into a structured scene plan. Each scene manifest must include scene ID,
start/end time, narration, visual objective, required assets,
motion-graphics instructions, animation keyframes/parameters, camera
movement, transitions, sound effects, music cues, captions, asset
requirements, and fallbacks. Use millisecond or frame-based timing with
a defined frame rate. Total timeline must match target duration within
reasonable tolerance.

Stage G: Generate original motion graphics and animation Implement a
real dynamic scene-generation system, not fixed templates. Generate
scene-specific instructions from topic/script: e.g. neural-network
diagrams for AI explanations, retention charts for YouTube growth,
maps/dates for documentaries, timed reactions for comedy. These are
examples, not mandatory templates.

Build/extend a renderer that interprets validated scene descriptions
into supported animation primitives. Use Android-native rendering,
Canvas, OpenGL, an embedded renderer, or compatible existing library as
appropriate. If HTML/CSS/JS rendering exists, use a secure controlled
interface. Generated code must not access user files, secrets, or
arbitrary device capabilities. Prefer structured validated animation
instructions over arbitrary generated code when reliable. Support
original layouts, typography, charts, diagrams, object animations,
transitions, and camera movement. Do not reuse the same complete
animation sequence merely because projects share a category.

Stage H: Asset retrieval and generation Identify image, PNG overlay,
transparent graphic, B-roll, illustration, diagram, icon, meme, sound
effect, music, texture, and supporting video requirements from each
scene. Use existing TelegramAssetManager and existing retrieval
infrastructure. Inspect bot configuration, search, downloads, caching,
and media resolution. Use existing credentials without exposing them.

For each asset: 1. Generate a precise description from scene
requirements. 2. Search configured source. 3. Retrieve candidates. 4.
Verify actual download/usability. 5. Inspect file type, dimensions,
duration, decoding, and technical properties. 6. Use a supported
visual-analysis provider to assess relevance. 7. Check mismatches,
quality, aspect ratio, corruption, and inappropriate content. 8. Reject
unsuitable candidates and search alternatives. 9. Retry a limited number
of times. 10. If none works, use a supported alternative: generated
graphics, licensed stock, user asset, or procedural animation.

Do not assume Telegram search understands visual meaning. Use real asset
inspection and multimodal verification where supported. Prefer original
generated assets, user-owned files, and appropriately licensed media.
Retain attribution where required. Do not assume Telegram media is
licensed. Never silently substitute unrelated content; report failure
and offer alternatives.

Stage I: AI voice generation Generate narration using existing SAIF AI
voice system or configured speech provider after script/timing approval.
Match selected language and voice settings. Generate manageable chunks
and retain text-to-audio relationships. Analyze audio duration and align
segments to scenes. Use forced alignment when available; otherwise use
measured durations and timing corrections. Do not unnaturally stretch
speech or cut off words. Adjust scenes/pacing or ask user if narration
is too long.

Stage J: Build actual timeline Add approved assets, scene instructions,
narration, and effects to the existing timeline as real editable items:
video clips, images, generated graphics, motion-graphics scenes,
narration, sound effects, music, captions, and transitions. Every item
needs a valid source or renderable scene definition, start time,
duration, and track. Prevent overlaps, missing media, invalid timing,
and broken references. Support track assignment, snapping,
synchronization, ordering, and recalculation. Keep assets associated
with the project.

Stage K: Preview and revise Playback must synchronize graphics, footage,
images, narration, effects, music, captions, and transitions from the
actual timeline. Provide play/pause/seeking and frame/time navigation
where supported. Report rendering failures instead of silently omitting
layers. Allow scene-level revisions, asset replacement,
pacing/transition improvements, and partial regeneration without
rebuilding everything. Export only after user approval.

6.  WORKFLOW TWO --- VIDEO TO EDITING

Start with an existing user video. Open AI Director chat, allow video
selection/upload, ask the editing goal and relevant preferences, analyze
the original, create an editing plan, and apply it to the actual
timeline.

Analyze duration, frame rate, resolution, orientation, scene changes,
visual content, audio, speech/silence, detectable captions, and relevant
events. Use frame sampling, scene detection, audio analysis, and speech
recognition where supported. Respect provider file limits; compress,
sample frames, or chunk when needed.

The AI may remove unwanted pauses, identify redundant segments, improve
pacing, reorder scenes when narrative permits, add cuts/transitions,
reframe, add overlays/motion graphics, captions if requested,
music/effects, adjust audio, improve continuity, and apply supported
effects. Never invent footage or claim an event occurred if absent.
Preserve original media and make a reversible edit plan.

Critical: do NOT generate a new narration script by default and do NOT
generate replacement AI narration. Focus on editing existing video and
requested additions. Internal transcription/analysis is allowed. Do not
automatically replace the original soundtrack. Apply approved edits to
the actual timeline and show synchronized preview.

7.  WORKFLOW THREE --- VIDEO EDITING WITH AI VOICE

Follow the VIDEO TO EDITING process but also generate AI narration based
on the original video. Ask for video, analyze visuals and existing
audio, ask relevant narration language/preferences, transcribe speech if
needed, analyze content and scene sequence, generate narration based on
what is actually visible/audible, generate narration through configured
default voice, synchronize it, perform other editing operations, and
place narration on an editable timeline track.

Allow: - Replacing existing speech. - Adding narration while retaining
appropriate original audio. - Combining original dialogue and AI
narration.

Where supported, allow voice, pace, language, pauses, and pronunciation.
Do not clone a person's voice unless supported and permissions/consent
exist. Align narration with scenes using timing and audio analysis.
Never force narration into impossible limits; adjust scene durations,
revise only approved relevant narration, or ask the user. Preserve
original audio when requested.

8.  WORKFLOW FOUR --- VOICE OVER TO VIDEO

Start with uploaded audio/voice-over. Allow supported audio files,
analyze audio, transcribe it, create timestamped transcript when
supported, ask relevant style/aspect ratio/audience/subtitle
preferences, plan scenes from actual narration, retrieve/generate
visuals, validate timeline, add original audio, synchronize
scenes/captions/visuals/effects, and render revisable preview.

Critical: do NOT generate replacement narration. Original uploaded
voice-over is authoritative. Preserve timing/content unless user
requests audio editing. Use actual duration, timestamps, pauses, and
sentence boundaries. Use sentence/segment alignment when word-level
timestamps are unavailable. If transcription is unsupported, report it
and offer a real fallback; never fabricate transcript. Put uploaded
audio on the actual timeline.

9.  UNIVERSAL API PROVIDER ARCHITECTURE

Inspect existing API settings/provider abstraction. Extend a provider
registry with capabilities: chat/reasoning, script generation, web
search, video understanding, image understanding/generation,
transcription, speech generation, forced alignment,
embeddings/retrieval, structured output/tool calling. Each provider
declares capabilities, model configuration, authentication, input
limits, and constraints.

For each task: identify capability, check configured compatible
providers, validate model/config, route to suitable provider, handle
rate limits/timeouts/network/invalid response/quota, retry transient
errors sensibly, use another compatible provider if possible, and inform
the user if none is available. Do not assume every OpenAI-compatible
endpoint supports every feature. Do not send unsupported parameters. Do
not treat chat as video rendering or speech generation. A text-only API
should still be used for supported tasks, while missing capabilities are
identified. Preserve current provider support and chat behavior.

Security: securely store credentials; never commit secrets or hardcode
API keys in Kotlin/assets; redact logs/errors; validate
endpoints/downloads; use HTTPS where supported; explain server-side
credential protection; never upload project files without configured
workflow and user authorization. Preserve provider settings and
migration compatibility.

10. REAL VIDEO RENDERING AND EXPORT

Inspect and reuse AndroidVideoExportEngine.kt and existing rendering
code. Combine existing/retrieved video, images, generated animations,
graphics, narration, voice-over, effects, music, captions, and
transitions using a shared timeline and deterministic timestamps.

Support applicable settings: aspect ratio, resolution, frame rate,
format, codec, audio codec, bitrate/quality. Provide 720p/1080p and
other technically supported resolutions. Respect existing export
UI/settings.

Export: 1. Validate project. 2. Check referenced media. 3. Validate
timing and synchronization. 4. Resolve missing assets/unsupported
effects. 5. Prepare renderable scenes. 6. Render frames or media
segments at selected frame rate. 7. Composite supported visual layers.
8. Mix/synchronize audio. 9. Encode and mux output. 10. Verify output
readability and expected duration. 11. Save to suitable location. 12.
Add to gallery through MediaStore when supported/authorized. 13. Show
true success/failure.

Use background processing, cancellation, real progress, and resource
management. Avoid loading entire high-resolution videos/all frames into
memory. Handle storage, codec, memory, permission, and interruption
failures. Use actual project timeline/settings; never create unrelated
placeholder MP4. Detect unsupported scenes before rendering and provide
fallback or clear error.

11. TELEGRAM ASSET PIPELINE

Extend existing TelegramAssetManager. Inspect bot integration, config,
downloads, media types. Implement search, retrieval, verification,
caching, project association, and timeline placement. Handle Telegram
errors/rate limits, timeouts, bounded retries, file-content validation,
file-size/media-format checks, corruption/unsupported files, actual
visual analysis where needed, source/licensing metadata, duplicate
avoidance, safe cleanup, and preservation of project-referenced assets.
Clearly report when Telegram is unavailable or has no suitable assets.
Never fake assets or claim failed downloads succeeded.

12. DYNAMIC TIMELINE AND SCENE DATA MODEL

Inspect existing project/timeline models and extend rather than replace
where practical. Every item should support relevant properties: unique
item ID, project ID, track ID, media/scene reference, start, duration,
source trim, layer order, volume/opacity, transforms, animation
parameters, effects/transitions, asset metadata, source/cache
references, render status, and validation errors.

Use consistent timebase and frame-rate conversion. Support
serialization/deserialization, schema migration, and reopening projects
without losing assets/edits. Keep scene manifests/render instructions
editable and versioned. Use stable IDs, not filenames/array indices.

13. PROJECT MEMORY AND RECOVERY

Maintain per-project context: original request, approved script,
duration/language, creative preferences, scene manifest, asset
decisions, narration segments, timeline changes, export settings,
failed/completed tasks, and feedback. Persist using existing storage or
compatible extension. On app restart, restore project and identify
completed tasks. Reuse valid cached results safely; do not regenerate
all assets/voice after minor edits. Scene changes should update
dependent timing/rendering only. Distinguish saved, active, failed, and
completed work.

14. PROFESSIONAL UX AND JOB STATUS

Use real production stages rather than a generic spinner: understanding
brief, research, script, scene planning, animation, asset search, asset
checking, narration, timeline, preview, export. Progress must reflect
real state; never invent percentages. Let users inspect scenes, review
scripts, retry failed tasks, and cancel supported operations. Use
background jobs where appropriate, keep UI responsive, prevent duplicate
expensive jobs, and show actionable errors without stack traces/secrets.

15. ADVANCED FEATURES WHERE PRACTICAL

After core workflows, consider scene regeneration, caption timing,
silence detection, smart reframing, audio ducking, scene-aware effects,
beat-aware editing when available, pacing analysis, asset deduplication,
preview/final render modes, render queue, undo/redo for AI edits,
before/after preview, project duplication/recovery, diagnostics,
pre-export validation, and export recovery where supported. Do not let
optional enhancements delay or destabilize the four core workflows.

16. PERFORMANCE AND ANDROID COMPATIBILITY

Inspect Gradle, min/target SDK, manifest, permissions, media libraries,
and build setup. Keep this an Android app. Use background
dispatchers/workers for CPU-heavy tasks, networking, downloads, audio
analysis, and rendering. Avoid main-thread blocking. Request permissions
only when needed and use suitable Android media-access APIs. Handle
lifecycle, network interruptions, cancellation, and device limits. Use
efficient decoding/caching. If work is too intensive for a phone, use a
configured remote service or report limitation; never pretend local
processing completed.

17. TESTING REQUIREMENTS

Inspect/update existing tests, including video-studio tests. Add tests
for workflow navigation, mode switching, question cards, custom
duration/language, script preservation/revision, scene manifest
validation, provider capabilities/fallback, API errors/timeouts, asset
verification/rejection, timeline timing/sync, voice-over alignment,
project save/recovery, export settings, missing assets/failed jobs,
cancellation/retry. Add integration tests where practical. Do not use
hardcoded success tests. Build the Android project and fix
compilation/dependency/test failures. Separate deterministic tests from
integrations requiring unavailable API keys/services and report what
could not be verified.

18. REQUIRED IMPLEMENTATION ORDER

PHASE 1 --- PROJECT AUDIT - Inspect ZIP/repository. - Identify entry
points and components. - Map new-project, editor, AI, timeline, voice,
asset, and export flows. - Identify capabilities/missing dependencies. -
Check secrets/security. - Create concise plan.

PHASE 2 --- UI AND NAVIGATION - Integrate SAIF AI at bottom of existing
editor. - Add AI mode toggle. - Preserve four original editing
options. - Add four AI workflows. - Implement chat and adaptive question
cards.

PHASE 3 --- SHARED AI SERVICES - Implement/extend capability
detection. - Add routing and compatible-provider fallback. - Add
persistent AI Director state. - Add structured scene
planning/validation.

PHASE 4 --- MEDIA PIPELINE - Integrate asset retrieval/verification. -
Implement/extend dynamic animation renderer. - Integrate
narration/transcription. - Implement all four workflow pipelines. -
Populate actual editable timeline.

PHASE 5 --- PREVIEW AND EXPORT - Synchronize visual/audio tracks. -
Integrate existing export engine. - Implement validation, rendering,
error handling, gallery saving.

PHASE 6 --- QUALITY ASSURANCE - Add/update tests. - Run available
tests. - Build Android app. - Fix issues. - Verify primary workflows
with local tests/integrations.

PHASE 7 --- FINAL DELIVERY - Deliver modified project, not just
instructions. - List added/modified files. - Explain working features
and external API requirements. - Provide exact build result. - Report
limitations honestly. - Explain provider configuration and running all
four workflows.

19. DEFINITION OF DONE

Implementation is not complete merely because four buttons appear.
Demonstrate: 1. Existing purple plus/new-project flow still works. 2.
Original editing options remain functional. 3. SAIF AI toggle opens four
correct workflows. 4. Each opens AI Director chat. 5. Question cards
preserve choices. 6. ZERO TO VIDEO turns approved topic/script into
structured scene plan. 7. VIDEO TO EDITING analyzes uploaded video and
creates a real edit plan without auto-generating narration. 8. VIDEO
EDITING WITH AI VOICE analyzes footage, creates narration, and places it
on timeline. 9. VOICE OVER TO VIDEO uses uploaded narration and
generates synchronized visuals without replacement narration. 10.
Providers are selected by real capabilities. 11. Assets are verified.
12. Timeline contains real media/renderable scene instructions. 13.
Preview synchronizes visual/audio content. 14. Export creates a genuine
playable video using selected settings. 15. Output saves to gallery
where supported. 16. Projects/assets recover after leaving editor. 17.
Failures are reported and retryable where possible. 18. Project builds
and relevant tests pass.

20. YOUR AUTONOMY

You may make architectural improvements, add helper classes, improve UX,
select dependencies, and optimize rendering where useful. Repair broken
implementations rather than duplicating them. Extend existing
components. If a behavior is impossible with the current API/device,
implement the closest real maintainable solution and explain the
limitation. Do not stop at a plan: make code changes, integrate, test,
and verify the build. Do not remove working functionality for
convenience.

Ultimate goal: transform the existing SAIF AI app into a genuinely
usable, extensible AI video creation/editing studio---not a template
demo or nonfunctional buttons.

Begin by auditing the uploaded ZIP and identifying existing
implementation paths. Then execute the implementation in phases until
you reach the strongest verifiable working result possible.
