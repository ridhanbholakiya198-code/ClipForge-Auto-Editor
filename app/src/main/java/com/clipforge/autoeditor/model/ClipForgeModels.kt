package com.clipforge.autoeditor.model

import android.net.Uri
import java.util.UUID

enum class OutcomeBadge(val label: String) {
    SURVIVED("SURVIVED"),
    TRAGIC("TRAGIC"),
    NONE("NONE");

    companion object {
        fun fromString(raw: String?): OutcomeBadge {
            return entries.firstOrNull {
                it.name.equals(raw?.trim(), ignoreCase = true)
            } ?: NONE
        }
    }
}

enum class ClipAFramingMode(val label: String) {
    BLURRED_BG("Blurred 16:9 Fill"),
    LEFT_WITH_TEASER("Left + Teaser Card")
}

enum class WatermarkPosition(val label: String) {
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right")
}

enum class TransitionSfx(val label: String, val fileName: String) {
    NONE("None", ""),
    WHOOSH("Whoosh", "sfx_whoosh.wav"),
    BOOM("Boom", "sfx_boom.wav"),
    SHUTTER("Shutter", "sfx_shutter.wav")
}

data class RealClipAngle(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val displayName: String = "",
    val placeholderLabel: String = "",
    val startSec: Float = 0f,
    val endSec: Float = 8f,
    val maxDurationSec: Float = 60f,
    val hasAudio: Boolean = true,
    val portrait: Boolean = false
) {
    val trimmedDurationSec: Float
        get() = (endSec - startSec).coerceAtLeast(0.5f)
}

data class StoryPair(
    val id: String = UUID.randomUUID().toString(),
    val storyTitle: String = "Untitled Story",
    val clipAUri: Uri? = null,
    val clipADisplayName: String = "",
    val clipAPlaceholderLabel: String = "",
    val clipAStartSec: Float = 0f,
    val clipAEndSec: Float = 8f,
    val clipAMaxDurationSec: Float = 60f,
    val clipAHasAudio: Boolean = true,
    val clipAPortrait: Boolean = true,
    val framingMode: ClipAFramingMode = ClipAFramingMode.BLURRED_BG,
    val realClips: List<RealClipAngle> = listOf(RealClipAngle()),
    val locationYear: String = "",
    val shockFact: String = "",
    val outcomeBadge: OutcomeBadge = OutcomeBadge.NONE,
    val creditTag: String = ""
) {
    val clipATrimmedDurationSec: Float
        get() = (clipAEndSec - clipAStartSec).coerceAtLeast(0.5f)

    val totalDurationSec: Float
        get() = clipATrimmedDurationSec + realClips.sumOf { it.trimmedDurationSec.toDouble() }.toFloat()

    val isReadyToRender: Boolean
        get() = clipAUri != null && realClips.isNotEmpty() && realClips.all { it.uri != null }
}

data class GlobalSettings(
    val watermarkText: String = "@YourChannel",
    val watermarkPosition: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val watermarkOpacity: Float = 0.28f,
    val transitionSfx: TransitionSfx = TransitionSfx.WHOOSH,
    val sfxVolume: Float = 0.80f,
    val bgMusicUri: Uri? = null,
    val bgMusicDisplayName: String = "None selected",
    val bgMusicVolumeDb: Float = -14f,
    val autoDuckDb: Float = -20f,
    val muteAnchorMusic: Boolean = false,
    val preferHardwareEncoder: Boolean = true,
    val defaultZackCredit: String = "@zackdfilms",
    val tensionRiser: Boolean = true
)

sealed interface RenderState {
    data object Idle : RenderState
    data object Preparing : RenderState
    data class Rendering(val sessionId: Long) : RenderState
    data class Completed(val outputPath: String, val savedGalleryUri: Uri?) : RenderState
    data class Failed(val message: String) : RenderState
    data object Cancelled : RenderState
}

data class RenderStats(
    val progress: Float = 0f,
    val currentTimeMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val currentFrame: Long = 0L,
    val totalFrames: Long = 0L,
    val fps: Float = 0f,
    val speed: Float = 0f,
    val etaSeconds: Long = 0L,
    val commandPreview: String = "",
    val logs: List<String> = emptyList(),
    val encoderLabel: String = "h264_mediacodec"
)
