package com.clipforge.autoeditor.ffmpeg

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.clipforge.autoeditor.model.GlobalSettings
import com.clipforge.autoeditor.model.StoryPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FfmpegRenderManager {

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var baseMs = 0L

    @Volatile
    private var baseFrames = 0L

    fun createOutputFilePath(context: Context): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outDir = File(context.cacheDir, "renders").apply {
            if (!exists()) mkdirs()
        }
        return File(outDir, "ClipForge_$stamp.mp4").absolutePath
    }

    suspend fun startRender(
        context: Context,
        pairs: List<StoryPair>,
        settings: GlobalSettings,
        useSoftwareFallback: Boolean = false,
        onSessionStarted: (sessionId: Long, built: BuiltFfmpegCommand) -> Unit,
        onProgress: (timeMs: Long, frame: Long, fps: Float, speed: Float, logLine: String?) -> Unit,
        onLog: (String) -> Unit,
        onCompleted: (outputPath: String) -> Unit,
        onCancelled: () -> Unit,
        onFailed: (message: String, canRetrySoftware: Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        cancelRequested = false
        baseMs = 0L
        baseFrames = 0L
        val canRetry = !useSoftwareFallback && settings.preferHardwareEncoder
        val workDir = FfmpegCommandBuilder.segmentsDir(context)

        // 0 = completed, 1 = cancelled, 2 = failed
        var outcome = 0
        var failMessage = ""
        var failRetry = false
        var builtOutput = ""

        try {
            workDir.deleteRecursively()
            workDir.mkdirs()
            val outputPath = createOutputFilePath(context)
            val built = FfmpegCommandBuilder.build(
                context = context,
                pairs = pairs,
                settings = settings,
                outputPath = outputPath,
                useSoftwareFallback = useSoftwareFallback,
                forPreview = false
            )
            builtOutput = built.outputPath
            onSessionStarted(0L, built)

            FFmpegKitConfig.enableLogCallback { log ->
                val msg = log.message?.trim()
                if (!msg.isNullOrEmpty()) onLog(msg)
            }
            FFmpegKitConfig.enableStatisticsCallback { stats ->
                onProgress(
                    baseMs + stats.time.toLong(),
                    baseFrames + stats.videoFrameNumber.toLong(),
                    stats.videoFps,
                    stats.speed.toFloat(),
                    null
                )
            }

            for ((i, step) in built.steps.withIndex()) {
                if (cancelRequested) break
                onLog("Step ${i + 1}/${built.steps.size}: ${step.label}")
                val session = FFmpegKit.executeWithArguments(step.arguments)
                val rc = session.returnCode
                if (cancelRequested || ReturnCode.isCancel(rc)) {
                    cancelRequested = true
                    break
                }
                if (!ReturnCode.isSuccess(rc)) {
                    val trace = session.failStackTrace ?: session.output ?: "Unknown FFmpeg error (code ${rc?.value})"
                    failMessage = "Step ${i + 1} (${step.label}) failed: " + trace.takeLast(400)
                    failRetry = canRetry
                    outcome = 2
                    break
                }
                baseMs += step.durationMs
                baseFrames += step.durationMs * 30L / 1000L
                onProgress(baseMs, baseFrames, 0f, 0f, null)
            }

            if (outcome == 0 && cancelRequested) outcome = 1

            if (outcome == 0) {
                onLog("Joining segments...")
                val joinSession = FFmpegKit.executeWithArguments(built.concatArguments)
                val joinRc = joinSession.returnCode
                if (ReturnCode.isCancel(joinRc)) {
                    outcome = 1
                } else if (!ReturnCode.isSuccess(joinRc)) {
                    failMessage = "Join failed: " + (joinSession.failStackTrace ?: joinSession.output ?: "unknown").takeLast(400)
                    failRetry = false
                    outcome = 2
                }
            }
        } catch (t: Throwable) {
            failMessage = "Render error: " + (t.message ?: t.javaClass.simpleName)
            failRetry = false
            outcome = 2
        } finally {
            // Clean up BEFORE reporting, so a software retry started from onFailed is never wiped.
            workDir.deleteRecursively()
        }

        when (outcome) {
            0 -> onCompleted(builtOutput)
            1 -> onCancelled()
            else -> onFailed(failMessage, failRetry)
        }
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") sessionId: Long) {
        cancelRequested = true
        FFmpegKit.cancel()
    }
}
