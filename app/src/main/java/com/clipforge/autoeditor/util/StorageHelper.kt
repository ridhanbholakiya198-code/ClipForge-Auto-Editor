package com.clipforge.autoeditor.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.clipforge.autoeditor.model.TransitionSfx
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

data class ProbedMedia(
    val durationSec: Float,
    val hasAudio: Boolean,
    val displayName: String,
    val portrait: Boolean = false
)

object StorageHelper {

    fun probeMedia(context: Context, uri: Uri): ProbedMedia {
        val displayName = queryDisplayName(context, uri)
        val retriever = MediaMetadataRetriever()
        var durationSec = 10f
        var hasAudio = true
        var portrait = false
        try {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durMs = durStr?.toLongOrNull() ?: 10_000L
            durationSec = (durMs / 1000f).coerceAtLeast(1f)
            val audioMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            hasAudio = audioMeta == null || audioMeta.equals("yes", ignoreCase = true)
            val vw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val vh = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val ew = if (rot == 90 || rot == 270) vh else vw
            val eh = if (rot == 90 || rot == 270) vw else vh
            portrait = eh > ew
        } catch (_: Exception) {
            // Keep safe defaults if retriever fails on exotic container
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        return ProbedMedia(durationSec = durationSec, hasAudio = hasAudio, displayName = displayName, portrait = portrait)
    }

    fun queryDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) {
                                return cursor.getString(idx) ?: "selected_media"
                            }
                        }
                    }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "selected_media"
    }

    fun resolveSafInputPath(context: Context, uri: Uri): String {
        return if (uri.scheme == "content") {
            FFmpegKitConfig.getSafParameterForRead(context, uri)
        } else {
            uri.path ?: uri.toString()
        }
    }

    fun resolveSystemFontPath(): String {
        val candidates = listOf(
            "/system/fonts/Roboto-Bold.ttf",
            "/system/fonts/Roboto-Medium.ttf",
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Bold.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans-Bold.ttf",
            "/system/fonts/DroidSans.ttf"
        )
        return candidates.firstOrNull { File(it).exists() } ?: "/system/fonts/Roboto-Regular.ttf"
    }

    /**
     * Generates clean 48kHz stereo 16-bit PCM WAV files in cacheDir for Whoosh, Boom, and Shutter
     * so Transition SFX work out-of-the-box without external asset downloads.
     */
    fun ensureTransitionSfxFile(context: Context, sfx: TransitionSfx): String? {
        if (sfx == TransitionSfx.NONE) return null
        val outFile = File(context.cacheDir, sfx.fileName)
        if (outFile.exists() && outFile.length() > 44L) {
            return outFile.absolutePath
        }
        val sampleRate = 48000
        val durationSec = when (sfx) {
            TransitionSfx.WHOOSH -> 0.55f
            TransitionSfx.BOOM -> 0.85f
            TransitionSfx.SHUTTER -> 0.25f
            TransitionSfx.NONE -> return null
        }
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples * 2)
        val rng = Random(42)

        var lpState = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            val sampleVal: Double = when (sfx) {
                TransitionSfx.WHOOSH -> {
                    val env = sin(PI * progress) * sin(PI * progress)
                    val white = rng.nextDouble(-1.0, 1.0)
                    val alpha = 0.04 + 0.22 * sin(PI * progress)
                    lpState += alpha * (white - lpState)
                    lpState * env * 0.85
                }
                TransitionSfx.BOOM -> {
                    val env = exp(-4.8 * t)
                    val freq = 95.0 * exp(-2.5 * t) + 32.0
                    val sub = sin(2.0 * PI * freq * t)
                    val punch = rng.nextDouble(-0.35, 0.35) * exp(-28.0 * t)
                    (sub * 0.85 + punch) * env
                }
                TransitionSfx.SHUTTER -> {
                    val click1 = exp(-120.0 * t) * rng.nextDouble(-1.0, 1.0)
                    val t2 = (t - 0.075).coerceAtLeast(0.0)
                    val click2 = if (t >= 0.075) exp(-140.0 * t2) * rng.nextDouble(-1.0, 1.0) else 0.0
                    (click1 + click2) * 0.9
                }
                TransitionSfx.NONE -> 0.0
            }
            val clamped = (sampleVal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            pcm[i * 2] = clamped
            pcm[i * 2 + 1] = clamped
        }

        writeWavFile(outFile, pcm, sampleRate, 2)
        return outFile.absolutePath
    }

    private fun writeWavFile(file: File, pcm: ShortArray, sampleRate: Int, channels: Int) {
        val dataSize = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // AudioFormat = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * 2) // ByteRate
            putShort((channels * 2).toShort()) // BlockAlign
            putShort(16) // BitsPerSample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }
        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            val dataBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) {
                dataBuffer.putShort(s)
            }
            fos.write(dataBuffer.array())
        }
    }

    fun saveVideoToGallery(context: Context, sourcePath: String, fileName: String): Result<Uri> {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return Result.failure(IllegalStateException("Rendered output file not found."))
        }
        return runCatching {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ClipForge")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Failed to create MediaStore entry.")
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Failed to open MediaStore output stream.")

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                itemUri
            } else {
                @Suppress("DEPRECATION")
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ClipForge"
                )
                if (!moviesDir.exists()) moviesDir.mkdirs()
                val destFile = File(moviesDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DATA, destFile.absolutePath)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                }
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: Uri.fromFile(destFile)
            }
        }
    }

    fun shareVideo(context: Context, outputPath: String, galleryUri: Uri?, targetYouTube: Boolean) {
        val shareUri: Uri = galleryUri ?: FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(outputPath)
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (targetYouTube) {
                setPackage("com.google.android.youtube")
            }
        }
        try {
            if (targetYouTube) {
                context.startActivity(intent)
            } else {
                context.startActivity(Intent.createChooser(intent, "Share Compilation Video"))
            }
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Share Compilation Video"))
        }
    }
}
