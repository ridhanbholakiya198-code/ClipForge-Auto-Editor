package com.clipforge.autoeditor.data

import android.net.Uri
import com.clipforge.autoeditor.model.OutcomeBadge
import com.clipforge.autoeditor.model.RealClipAngle
import com.clipforge.autoeditor.model.StoryPair
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ScriptPairDto(
    @SerialName("story_title")
    val storyTitle: String,
    @SerialName("zack_clip_uri")
    val zackClipUri: String = "",
    @SerialName("zack_start_sec")
    val zackStartSec: Float = 0f,
    @SerialName("zack_end_sec")
    val zackEndSec: Float = 8f,
    @SerialName("real_clips")
    val realClips: List<RealClipDto> = emptyList(),
    @SerialName("location_year")
    val locationYear: String = "",
    @SerialName("shock_fact")
    val shockFact: String = "",
    @SerialName("outcome_badge")
    val outcomeBadge: String = "NONE",
    @SerialName("credit")
    val credit: String = ""
)

@Serializable
data class RealClipDto(
    @SerialName("uri")
    val uri: String = "",
    @SerialName("start_sec")
    val startSec: Float = 0f,
    @SerialName("end_sec")
    val endSec: Float = 8f
)

object ScriptParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val SAMPLE_TEMPLATE_JSON: String = """
        [
          {
            "story_title": "The Rollercoaster Derailment",
            "zack_clip_uri": "zack_coaster_anim.mp4",
            "zack_start_sec": 0.0,
            "zack_end_sec": 7.5,
            "real_clips": [
              {
                "uri": "real_coaster_cam1.mp4",
                "start_sec": 2.0,
                "end_sec": 8.0
              },
              {
                "uri": "real_coaster_cam2.mp4",
                "start_sec": 1.0,
                "end_sec": 6.0
              }
            ],
            "location_year": "LOUISVILLE, 2015",
            "shock_fact": "Car detached mid-loop at 55 mph before emergency brakes engaged",
            "outcome_badge": "SURVIVED",
            "credit": "Clip: Local News Archive"
          },
          {
            "story_title": "Deep Sea Bell Incident",
            "zack_clip_uri": "zack_diving_anim.mp4",
            "zack_start_sec": 1.0,
            "zack_end_sec": 9.0,
            "real_clips": [
              {
                "uri": "real_diver_footage.mp4",
                "start_sec": 0.0,
                "end_sec": 7.0
              }
            ],
            "location_year": "NORTH SEA, 1983",
            "shock_fact": "9-atmosphere pressure drop occurred in less than 0.2 seconds",
            "outcome_badge": "TRAGIC",
            "credit": ""
          }
        ]
    """.trimIndent()

    fun parse(rawJson: String): Result<List<StoryPair>> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("JSON script is empty."))
        }
        return runCatching {
            val dtos = json.decodeFromString<List<ScriptPairDto>>(trimmed)
            if (dtos.isEmpty()) {
                throw IllegalArgumentException("JSON array must contain at least one Story Pair.")
            }
            dtos.map { dto ->
                val clipAUri = parseOptionalUri(dto.zackClipUri)
                val clipAStart = dto.zackStartSec.coerceAtLeast(0f)
                val clipAEnd = dto.zackEndSec.coerceAtLeast(clipAStart + 0.5f)

                val mappedRealClips = if (dto.realClips.isEmpty()) {
                    listOf(RealClipAngle())
                } else {
                    dto.realClips.map { rc ->
                        val rcUri = parseOptionalUri(rc.uri)
                        val rcStart = rc.startSec.coerceAtLeast(0f)
                        val rcEnd = rc.endSec.coerceAtLeast(rcStart + 0.5f)
                        RealClipAngle(
                            uri = rcUri,
                            displayName = if (rcUri != null) rcUri.lastPathSegment ?: "Selected Clip" else "",
                            placeholderLabel = if (rcUri == null) rc.uri.ifBlank { "Tap to pick real clip" } else "",
                            startSec = rcStart,
                            endSec = rcEnd,
                            maxDurationSec = (rcEnd + 30f).coerceAtLeast(60f)
                        )
                    }
                }

                StoryPair(
                    storyTitle = dto.storyTitle.ifBlank { "Untitled Story" },
                    clipAUri = clipAUri,
                    clipADisplayName = if (clipAUri != null) clipAUri.lastPathSegment ?: "Animation Clip" else "",
                    clipAPlaceholderLabel = if (clipAUri == null) dto.zackClipUri.ifBlank { "Tap to pick animation clip" } else "",
                    clipAStartSec = clipAStart,
                    clipAEndSec = clipAEnd,
                    clipAMaxDurationSec = (clipAEnd + 30f).coerceAtLeast(60f),
                    realClips = mappedRealClips,
                    locationYear = dto.locationYear.trim(),
                    shockFact = dto.shockFact.trim(),
                    outcomeBadge = OutcomeBadge.fromString(dto.outcomeBadge),
                    creditTag = dto.credit.trim()
                )
            }
        }
    }

    private fun parseOptionalUri(raw: String): Uri? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        return if (value.startsWith("content://") || value.startsWith("file://")) {
            Uri.parse(value)
        } else {
            // Filename placeholder (e.g., "zack_coaster_anim.mp4") -> user taps slot to pick real file
            null
        }
    }
}
