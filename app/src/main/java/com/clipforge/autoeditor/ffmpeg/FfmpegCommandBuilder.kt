package com.clipforge.autoeditor.ffmpeg

import android.content.Context
import android.net.Uri
import com.clipforge.autoeditor.model.ClipAFramingMode
import com.clipforge.autoeditor.model.GlobalSettings
import com.clipforge.autoeditor.model.OutcomeBadge
import com.clipforge.autoeditor.model.StoryPair
import com.clipforge.autoeditor.model.TransitionSfx
import com.clipforge.autoeditor.model.WatermarkPosition
import com.clipforge.autoeditor.util.StorageHelper
import java.io.File
import java.util.Locale
import kotlin.math.pow

/** One small FFmpeg job that renders exactly one clip (Zack clip or one real-footage angle). */
class RenderStep(
    val label: String,
    val arguments: Array<String>,
    val outputPath: String,
    val durationMs: Long
)

class BuiltFfmpegCommand(
    val steps: List<RenderStep>,
    val concatArguments: Array<String>,
    val printableCommand: String,
    val totalDurationMs: Long,
    val totalFrames: Long,
    val outputPath: String,
    val encoderName: String
)

private class SegSpec(
    val index: Int,
    val isZack: Boolean,
    val inputPath: String,
    val startSec: Float,
    val durSec: Float,
    val hasAudio: Boolean,
    val portrait: Boolean,
    val pair: StoryPair,
    val showBadge: Boolean
)

/**
 * Renders every clip in its OWN small ffmpeg run (input-level -ss/-t seeking, one decoder at a time),
 * then joins the finished segments with the concat demuxer using stream copy.
 *
 * The old single giant filter_complex opened every input at once and buffered frames of all of them,
 * which got the app killed (out of memory) with ~17 videos.
 */
object FfmpegCommandBuilder {

    private const val FMT_A = "aformat=sample_rates=48000:channel_layouts=stereo:sample_fmts=fltp"

    fun segmentsDir(context: Context): File = File(context.cacheDir, "segments")

    fun build(
        context: Context,
        pairs: List<StoryPair>,
        settings: GlobalSettings,
        outputPath: String,
        useSoftwareFallback: Boolean = false,
        forPreview: Boolean = false
    ): BuiltFfmpegCommand {
        val fontPath = escapeFilterValue(StorageHelper.resolveSystemFontPath())
        val useSoftware = useSoftwareFallback || !settings.preferHardwareEncoder
        val encoderName = if (useSoftware) "libx264" else "h264_mediacodec"
        val workDir = segmentsDir(context)
        if (!forPreview) workDir.mkdirs()

        // In preview mode never open SAF file descriptors (they leak if ffmpeg never reads them).
        fun inputPath(uri: Uri): String =
            if (forPreview) (uri.lastPathSegment ?: uri.toString())
            else StorageHelper.resolveSafInputPath(context, uri)

        val sfxPath: String? = when {
            settings.transitionSfx == TransitionSfx.NONE -> null
            forPreview -> File(context.cacheDir, settings.transitionSfx.fileName).absolutePath
            else -> StorageHelper.ensureTransitionSfxFile(context, settings.transitionSfx)
        }
        val bgmUri = settings.bgMusicUri
        val bgmPath: String? = if (bgmUri != null) inputPath(bgmUri) else null

        val steps = mutableListOf<RenderStep>()
        var segIndex = 0
        var totalSec = 0f

        for (pair in pairs) {
            val clipAUri = requireNotNull(pair.clipAUri) {
                "Missing Clip A video for '${pair.storyTitle}'"
            }
            val aSpec = SegSpec(
                index = segIndex,
                isZack = true,
                inputPath = inputPath(clipAUri),
                startSec = pair.clipAStartSec,
                durSec = pair.clipATrimmedDurationSec,
                hasAudio = pair.clipAHasAudio,
                portrait = pair.clipAPortrait,
                pair = pair,
                showBadge = false
            )
            steps += buildStep(aSpec, pair.storyTitle + " - Clip A", settings, fontPath, sfxPath, bgmPath, useSoftware, workDir)
            totalSec += aSpec.durSec
            segIndex++

            pair.realClips.forEachIndexed { angleIdx, angle ->
                val angleUri = requireNotNull(angle.uri) {
                    "Missing Real Footage video for '${pair.storyTitle}'"
                }
                val bSpec = SegSpec(
                    index = segIndex,
                    isZack = false,
                    inputPath = inputPath(angleUri),
                    startSec = angle.startSec,
                    durSec = angle.trimmedDurationSec,
                    hasAudio = angle.hasAudio,
                    portrait = angle.portrait,
                    pair = pair,
                    showBadge = angleIdx == pair.realClips.lastIndex
                )
                steps += buildStep(bSpec, pair.storyTitle + " - Real #" + (angleIdx + 1), settings, fontPath, sfxPath, bgmPath, useSoftware, workDir)
                totalSec += bSpec.durSec
                segIndex++
            }
        }

        val listFile = File(workDir, "list.txt")
        if (!forPreview) {
            listFile.writeText(steps.joinToString("\n") { "file '" + it.outputPath + "'" } + "\n")
        }
        val concatArgs = arrayOf(
            "-y", "-f", "concat", "-safe", "0", "-i", listFile.absolutePath,
            "-c", "copy", "-movflags", "+faststart", outputPath
        )

        val printable = buildString {
            steps.forEachIndexed { i, step ->
                append("# step ").append(i + 1).append("/").append(steps.size).append(": ").append(step.label).append('\n')
                append(printArgs(step.arguments)).append("\n\n")
            }
            append("# join\n").append(printArgs(concatArgs))
        }

        val totalDurationSec = totalSec.coerceAtLeast(1f)
        return BuiltFfmpegCommand(
            steps = steps,
            concatArguments = concatArgs,
            printableCommand = printable,
            totalDurationMs = (totalDurationSec * 1000f).toLong(),
            totalFrames = (totalDurationSec * 30f).toLong(),
            outputPath = outputPath,
            encoderName = encoderName
        )
    }

    private fun buildStep(
        spec: SegSpec,
        label: String,
        settings: GlobalSettings,
        fontPath: String,
        sfxPath: String?,
        bgmPath: String?,
        useSoftware: Boolean,
        workDir: File
    ): RenderStep {
        val d = spec.durSec
        val pair = spec.pair
        val inputs = mutableListOf<String>()
        inputs.addAll(listOf("-ss", fmt(spec.startSec), "-t", fmt(d), "-i", spec.inputPath))
        var nextInput = 1

        var sfxIdx = -1
        if (!spec.isZack && sfxPath != null) {
            inputs.addAll(listOf("-i", sfxPath))
            sfxIdx = nextInput
            nextInput++
        }
        var bgmIdx = -1
        if (bgmPath != null && !(spec.isZack && settings.muteAnchorMusic)) {
            inputs.addAll(listOf("-stream_loop", "-1", "-t", fmt(d), "-i", bgmPath))
            bgmIdx = nextInput
            nextInput++
        }

        // ---------------- VIDEO ----------------
        val teaser = spec.isZack && pair.framingMode == ClipAFramingMode.LEFT_WITH_TEASER
        var core: String = when {
            teaser -> {
                val teaserTitle = escapeDrawtext(pair.storyTitle.uppercase(Locale.US).take(28))
                "color=c=0x0A0A0A:s=1920x1080:r=30:d=${fmt(d)}[canvas];" +
                    "[0:v]fps=30,scale=-2:1080:force_original_aspect_ratio=decrease[fgs];" +
                    "[canvas][fgs]overlay=x=140:y=(H-h)/2:shortest=1," +
                    "drawbox=x=860:y=360:w=920:h=360:color=0x161618@0.92:t=fill," +
                    "drawbox=x=860:y=360:w=8:h=360:color=0xFF2E4C@1.0:t=fill," +
                    "drawtext=fontfile='$fontPath':text='REAL CLIP NEXT  >>':fontcolor=0xFF2E4C:fontsize=46:x=910:y=420," +
                    "drawtext=fontfile='$fontPath':text='$teaserTitle':fontcolor=white:fontsize=38:x=910:y=515," +
                    "setsar=1,format=yuv420p"
            }
            spec.portrait -> {
                "[0:v]fps=30,split=2[bg][fg];" +
                    "[bg]scale=192:108:force_original_aspect_ratio=increase,crop=192:108,boxblur=4:2,scale=1920:1080,eq=brightness=-0.12[bgb];" +
                    "[fg]scale=-2:1080:force_original_aspect_ratio=decrease[fgs];" +
                    "[bgb][fgs]overlay=(W-w)/2:(H-h)/2,setsar=1,format=yuv420p"
            }
            else -> {
                "[0:v]fps=30,scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,setsar=1,format=yuv420p"
            }
        }
        // white flash on the cut into real footage
        if (!spec.isZack) core += ",fade=t=in:st=0:d=0.18:color=white"

        val overlays = overlayFilters(spec, settings, fontPath)
        val videoChain = if (overlays.isEmpty()) "$core[v]" else core + "," + overlays.joinToString(",") + "[v]"

        // ---------------- AUDIO ----------------
        val aChains = mutableListOf<String>()
        val mix = mutableListOf<String>()

        if (spec.hasAudio) {
            aChains += "[0:a]$FMT_A[a_orig]"
        } else {
            aChains += "anullsrc=channel_layout=stereo:sample_rate=48000,atrim=duration=${fmt(d)},$FMT_A[a_orig]"
        }

        if (bgmIdx >= 0) {
            val volStr = String.format(Locale.US, "%.4f", 10.0.pow(settings.bgMusicVolumeDb / 20.0))
            val duckRatio = (1.0 + (-settings.autoDuckDb / 5.0)).coerceIn(2.0, 12.0)
            val duckStr = String.format(Locale.US, "%.1f", duckRatio)
            aChains += "[a_orig]asplit=2[a_main][a_sc]"
            aChains += "[$bgmIdx:a]atrim=duration=${fmt(d)},asetpts=PTS-STARTPTS,$FMT_A,volume=$volStr[bgm_pre]"
            aChains += "[bgm_pre][a_sc]sidechaincompress=threshold=0.03:ratio=$duckStr:attack=40:release=350[bgm_d]"
            mix += "[a_main]"
            mix += "[bgm_d]"
        } else {
            mix += "[a_orig]"
        }

        if (sfxIdx >= 0) {
            val sfxVol = String.format(Locale.US, "%.2f", settings.sfxVolume.coerceIn(0f, 1.5f))
            aChains += "[$sfxIdx:a]$FMT_A,volume=$sfxVol[sfx]"
            mix += "[sfx]"
        }

        if (spec.isZack && settings.tensionRiser && d > 4.5f) {
            val delayMs = ((d - 3f) * 1000f).toLong()
            aChains += "anoisesrc=d=3:c=pink:r=48000:a=0.7,lowpass=f=4000,highpass=f=250," +
                "afade=t=in:st=0:d=3,$FMT_A,volume=-9dB,adelay=$delayMs|$delayMs[riser]"
            mix += "[riser]"
        }

        if (spec.showBadge && pair.outcomeBadge != OutcomeBadge.NONE && d > 3f) {
            val delayMs = ((d - 2f) * 1000f).toLong()
            val e = "0.6*sin(2*PI*1760*t)*exp(-9*t)"
            aChains += "aevalsrc='$e|$e':d=0.5:s=48000,$FMT_A,adelay=$delayMs|$delayMs[ding]"
            mix += "[ding]"
        }

        aChains += mix.joinToString("") +
            "amix=inputs=${mix.size}:duration=longest:normalize=0,atrim=duration=${fmt(d)},alimiter=limit=0.95[aout]"

        val filterComplex = (listOf(videoChain) + aChains).joinToString(";")
        val outFile = File(workDir, String.format(Locale.US, "seg_%02d.mp4", spec.index)).absolutePath

        val args = mutableListOf<String>()
        args.add("-y")
        args.addAll(inputs)
        args.addAll(listOf("-filter_complex", filterComplex, "-map", "[v]", "-map", "[aout]"))
        args.addAll(listOf("-c:v", if (useSoftware) "libx264" else "h264_mediacodec"))
        if (useSoftware) args.addAll(listOf("-preset", "veryfast", "-crf", "23")) else args.addAll(listOf("-b:v", "8M"))
        args.addAll(listOf("-g", "30", "-r", "30", "-pix_fmt", "yuv420p"))
        args.addAll(listOf("-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2", "-movflags", "+faststart", outFile))

        return RenderStep(
            label = label,
            arguments = args.toTypedArray(),
            outputPath = outFile,
            durationMs = (d * 1000f).toLong()
        )
    }

    private fun overlayFilters(spec: SegSpec, settings: GlobalSettings, fontPath: String): List<String> {
        val f = mutableListOf<String>()
        val pair = spec.pair
        if (spec.isZack) {
            val credit = pair.creditTag.ifBlank { settings.defaultZackCredit }
            if (credit.isNotBlank()) {
                f += drawText(fontPath, credit, 28, "white@0.85", "w-text_w-44", "40")
            }
        } else {
            if (pair.locationYear.isNotBlank()) {
                f += drawText(fontPath, pair.locationYear.uppercase(Locale.US), 34, "white", "44", "40", "black@0.68", 18)
            }
            if (pair.shockFact.isNotBlank()) {
                f += drawText(fontPath, pair.shockFact, 36, "white", "(w-text_w)/2", "h-112", "black@0.72", 20)
            }
            if (spec.showBadge) {
                val enable = "gte(t,${fmt((spec.durSec - 2f).coerceAtLeast(0f))})"
                when (pair.outcomeBadge) {
                    OutcomeBadge.SURVIVED -> f += drawText(fontPath, "SURVIVED", 36, "black", "w-text_w-44", "40", "0x00E676@0.95", 18, enable)
                    OutcomeBadge.TRAGIC -> f += drawText(fontPath, "TRAGIC", 36, "white", "w-text_w-44", "40", "0xFF3B30@0.95", 18, enable)
                    OutcomeBadge.NONE -> Unit
                }
            }
        }
        if (settings.watermarkText.isNotBlank()) {
            val alpha = String.format(Locale.US, "%.2f", settings.watermarkOpacity.coerceIn(0.05f, 1f))
            val pos: Pair<String, String> = when (settings.watermarkPosition) {
                WatermarkPosition.TOP_LEFT -> Pair("48", "40")
                WatermarkPosition.TOP_RIGHT -> Pair("w-text_w-48", "40")
                WatermarkPosition.BOTTOM_LEFT -> Pair("48", "h-text_h-40")
                WatermarkPosition.BOTTOM_RIGHT -> Pair("w-text_w-48", "h-text_h-40")
            }
            f += drawText(fontPath, settings.watermarkText.trim(), 34, "white@$alpha", pos.first, pos.second)
        }
        return f
    }

    private fun drawText(
        fontPath: String,
        text: String,
        size: Int,
        color: String,
        x: String,
        y: String,
        box: String? = null,
        boxBorder: Int = 18,
        enable: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("drawtext=fontfile='").append(fontPath).append("':text='").append(escapeDrawtext(text)).append("'")
        sb.append(":fontsize=").append(size).append(":fontcolor=").append(color)
        sb.append(":x=").append(x).append(":y=").append(y)
        if (box != null) sb.append(":box=1:boxcolor=").append(box).append(":boxborderw=").append(boxBorder)
        if (enable != null) sb.append(":enable='").append(enable).append("'")
        return sb.toString()
    }

    private fun printArgs(args: Array<String>): String {
        val sb = StringBuilder("ffmpeg ")
        for (arg in args) {
            if (arg.contains(" ") || arg.contains(";") || arg.contains("[")) {
                sb.append('"').append(arg).append("\" ")
            } else {
                sb.append(arg).append(' ')
            }
        }
        return sb.toString().trim()
    }

    private fun escapeDrawtext(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace(":", "\\:")
            .replace("'", "\u2019")
            .replace("%", "\\%")
            .replace("\n", " ")
    }

    private fun escapeFilterValue(input: String): String {
        return input.replace("\\", "\\\\").replace(":", "\\:")
    }

    private fun fmt(value: Float): String = String.format(Locale.US, "%.2f", value)
}
