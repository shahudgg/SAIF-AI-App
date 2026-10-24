package com.example.studio.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Robust JSON storage for StudioProject v2 under filesDir/studio/projects/<id>/project.json.
 * Completely immune to name-based collisions (uses UUID project IDs).
 */
class StudioProjectStorage(private val context: Context) {

    private val studioDir = File(context.filesDir, "studio")
    private val projectsDir = File(studioDir, "projects")
    private val indexFile = File(studioDir, "index.json")

    init {
        projectsDir.mkdirs()
    }

    @Synchronized
    fun saveProject(project: StudioProject) {
        val projDir = File(projectsDir, project.id)
        projDir.mkdirs()
        val tempFile = File(projDir, "project.json.tmp")
        val finalFile = File(projDir, "project.json")

        val root = JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
            put("createdAt", project.createdAt)
            put("updatedAt", project.updatedAt)
            put("aspect", project.aspect)
            put("fps", project.fps)
            put("schemaVersion", project.schemaVersion)

            val lanesArr = JSONArray()
            project.lanes.forEach { l ->
                lanesArr.put(JSONObject().apply {
                    put("id", l.id)
                    put("trackType", l.trackType.name)
                    put("name", l.name)
                    put("isMuted", l.isMuted)
                    put("isSolo", l.isSolo)
                    put("isLocked", l.isLocked)
                    put("heightDp", l.heightDp)
                })
            }
            put("lanes", lanesArr)

            val itemsArr = JSONArray()
            project.items.forEach { item ->
                val itemObj = JSONObject().apply {
                    put("id", item.id)
                    put("laneId", item.laneId)
                    put("trackType", item.trackType.name)
                    put("startUs", item.startUs)
                    put("durationUs", item.durationUs)
                    put("label", item.label)
                    put("origin", item.origin)
                    put("locked", item.locked)
                    put("aiWhy", item.aiWhy ?: "")

                    when (item) {
                        is VideoItem -> {
                            put("mediaUri", item.mediaUri)
                            put("sourceInUs", item.sourceInUs)
                            put("sourceOutUs", item.sourceOutUs)
                            put("playbackSpeed", item.playbackSpeed.toDouble())
                            put("volume", item.volume.toDouble())
                        }
                        is ImageItem -> {
                            put("mediaUri", item.mediaUri)
                        }
                        is TextItem -> {
                            put("text", item.text)
                            put("fontSizeSp", item.fontSizeSp.toDouble())
                            put("textColorHex", item.textColorHex)
                            put("stylePreset", item.stylePreset)
                        }
                        is VoiceItem -> {
                            put("text", item.text)
                            put("speakerTone", item.speakerTone)
                            put("audioUri", item.audioUri ?: "")
                        }
                        is AudioItem -> {
                            put("audioUri", item.audioUri ?: "")
                            put("isSfx", item.isSfx)
                            put("volume", item.volume.toDouble())
                        }
                        is CodeSceneItem -> {
                            put("title", item.title)
                            put("htmlCode", item.htmlCode)
                            put("animationType", item.animationType)
                        }
                    }
                }
                itemsArr.put(itemObj)
            }
            put("items", itemsArr)

            val markersArr = JSONArray()
            project.markersUs.forEach { markersArr.put(it) }
            put("markersUs", markersArr)
        }

        tempFile.writeText(root.toString(2))
        tempFile.renameTo(finalFile)

        updateIndex(project)
    }

    @Synchronized
    fun loadProject(id: String): StudioProject? {
        val projDir = File(projectsDir, id)
        val file = File(projDir, "project.json")
        if (!file.exists()) return null

        return try {
            val root = JSONObject(file.readText())
            val lanesArr = root.optJSONArray("lanes") ?: JSONArray()
            val lanes = mutableListOf<StudioLane>()
            for (i in 0 until lanesArr.length()) {
                val o = lanesArr.getJSONObject(i)
                lanes.add(
                    StudioLane(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        trackType = TrackType.valueOf(o.optString("trackType", TrackType.VIDEO.name)),
                        name = o.optString("name", "Lane"),
                        isMuted = o.optBoolean("isMuted", false),
                        isSolo = o.optBoolean("isSolo", false),
                        isLocked = o.optBoolean("isLocked", false),
                        heightDp = o.optInt("heightDp", 48)
                    )
                )
            }

            val itemsArr = root.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<TimelineItem>()
            for (i in 0 until itemsArr.length()) {
                val o = itemsArr.getJSONObject(i)
                val type = TrackType.valueOf(o.optString("trackType", TrackType.VIDEO.name))
                val id = o.optString("id", UUID.randomUUID().toString())
                val laneId = o.optString("laneId", "")
                val startUs = o.optLong("startUs", 0L)
                val durationUs = o.optLong("durationUs", 1_000_000L)
                val label = o.optString("label", "Item")

                val item: TimelineItem = when (type) {
                    TrackType.VIDEO -> VideoItem(
                        id = id,
                        laneId = laneId,
                        startUs = startUs,
                        durationUs = durationUs,
                        mediaUri = o.optString("mediaUri", ""),
                        sourceInUs = o.optLong("sourceInUs", 0L),
                        sourceOutUs = o.optLong("sourceOutUs", durationUs),
                        playbackSpeed = o.optDouble("playbackSpeed", 1.0).toFloat(),
                        volume = o.optDouble("volume", 1.0).toFloat(),
                        label = label
                    )
                    TrackType.VISUAL -> ImageItem(
                        id = id,
                        laneId = laneId,
                        startUs = startUs,
                        durationUs = durationUs,
                        mediaUri = o.optString("mediaUri", ""),
                        label = label
                    )
                    TrackType.TEXT -> TextItem(
                        id = id,
                        laneId = laneId,
                        startUs = startUs,
                        durationUs = durationUs,
                        text = o.optString("text", ""),
                        fontSizeSp = o.optDouble("fontSizeSp", 24.0).toFloat(),
                        textColorHex = o.optLong("textColorHex", 0xFFFFFFFF),
                        stylePreset = o.optString("stylePreset", "Hormozi Neon"),
                        label = label
                    )
                    TrackType.VOICE -> VoiceItem(
                        id = id,
                        laneId = laneId,
                        startUs = startUs,
                        durationUs = durationUs,
                        audioUri = o.optString("audioUri", null),
                        text = o.optString("text", ""),
                        speakerTone = o.optString("speakerTone", "Energetic"),
                        label = label
                    )
                    TrackType.AUDIO -> AudioItem(
                        id = id,
                        laneId = laneId,
                        startUs = startUs,
                        durationUs = durationUs,
                        audioUri = o.optString("audioUri", null),
                        isSfx = o.optBoolean("isSfx", true),
                        volume = o.optDouble("volume", 0.8).toFloat(),
                        label = label
                    )
                    TrackType.CODE -> CodeSceneItem(
                        id = id,
                        laneId = laneId,
                        startUs = startUs,
                        durationUs = durationUs,
                        title = o.optString("title", "Code Scene"),
                        htmlCode = o.optString("htmlCode", ""),
                        animationType = o.optString("animationType", "GSAP Motion Graphics"),
                        label = label
                    )
                }
                items.add(item)
            }

            val markersArr = root.optJSONArray("markersUs") ?: JSONArray()
            val markers = mutableListOf<Long>()
            for (i in 0 until markersArr.length()) {
                markers.add(markersArr.getLong(i))
            }

            StudioProject(
                id = root.getString("id"),
                name = root.optString("name", "Untitled"),
                createdAt = root.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = root.optLong("updatedAt", System.currentTimeMillis()),
                aspect = root.optString("aspect", "9:16"),
                fps = root.optInt("fps", 30),
                lanes = if (lanes.isNotEmpty()) lanes else StudioProject.defaultEmptyLanes(),
                items = items,
                markersUs = markers,
                schemaVersion = root.optInt("schemaVersion", 2)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Synchronized
    fun listProjects(): List<StudioProjectSummary> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            val list = mutableListOf<StudioProjectSummary>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    StudioProjectSummary(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        updatedAt = o.optLong("updatedAt", 0L),
                        aspect = o.optString("aspect", "9:16"),
                        durationSeconds = o.optDouble("durationSeconds", 0.0).toFloat(),
                        itemCount = o.optInt("itemCount", 0)
                    )
                )
            }
            list.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun deleteProject(id: String) {
        val projDir = File(projectsDir, id)
        projDir.deleteRecursively()
        val current = listProjects().filterNot { it.id == id }
        val arr = JSONArray()
        current.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("updatedAt", s.updatedAt)
                put("aspect", s.aspect)
                put("durationSeconds", s.durationSeconds.toDouble())
                put("itemCount", s.itemCount)
            })
        }
        indexFile.writeText(arr.toString(2))
    }

    private fun updateIndex(project: StudioProject) {
        val current = listProjects().filterNot { it.id == project.id }
        val updatedSummary = StudioProjectSummary(
            id = project.id,
            name = project.name,
            updatedAt = project.updatedAt,
            aspect = project.aspect,
            durationSeconds = project.totalDurationSeconds,
            itemCount = project.items.size
        )
        val combined = listOf(updatedSummary) + current
        val arr = JSONArray()
        combined.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("updatedAt", s.updatedAt)
                put("aspect", s.aspect)
                put("durationSeconds", s.durationSeconds.toDouble())
                put("itemCount", s.itemCount)
            })
        }
        indexFile.writeText(arr.toString(2))
    }
}

data class StudioProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val aspect: String,
    val durationSeconds: Float,
    val itemCount: Int
)
