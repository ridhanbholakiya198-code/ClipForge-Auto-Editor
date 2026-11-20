package com.clipforge.autoeditor.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.clipforge.autoeditor.model.RenderState
import com.clipforge.autoeditor.model.RenderStats
import com.clipforge.autoeditor.model.StoryPair
import com.clipforge.autoeditor.ui.components.AmoledCard
import com.clipforge.autoeditor.ui.theme.ClipForgeColors
import com.clipforge.autoeditor.util.StorageHelper
import com.clipforge.autoeditor.viewmodel.ClipForgeViewModel
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RenderExportScreen(
    pairs: List<StoryPair>,
    renderState: RenderState,
    renderStats: RenderStats,
    showCommandDebug: Boolean,
    statusBanner: String?,
    viewModel: ClipForgeViewModel
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showLogs by remember { mutableStateOf(false) }

    val totalSec = pairs.sumOf { it.totalDurationSec.toDouble() }.toFloat()
    val readyCount = pairs.count { it.isReadyToRender }
    val isRendering = renderState is RenderState.Preparing || renderState is RenderState.Rendering

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Render & Export",
                style = MaterialTheme.typography.headlineSmall,
                color = ClipForgeColors.TextPrimary
            )
            Text(
                text = "1920x1080 • 30fps MP4 • Local FFmpegKit Engine",
                style = MaterialTheme.typography.bodyMedium,
                color = ClipForgeColors.TextSecondary
            )
        }

        // Summary & Primary Action Card
        item {
            AmoledCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Story Pairs Ready", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextSecondary)
                        Text("$readyCount / ${pairs.size}", style = MaterialTheme.typography.titleMedium, color = ClipForgeColors.TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Est. Output Duration", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextSecondary)
                        Text(String.format(Locale.US, "%.1fs (1080p)", totalSec), style = MaterialTheme.typography.titleMedium, color = ClipForgeColors.TextPrimary)
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (isRendering) {
                    Button(
                        onClick = { viewModel.cancelRender() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClipForgeColors.TragicRed)
                    ) {
                        Icon(Icons.Rounded.Cancel, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel Render", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.startRender() },
                        enabled = pairs.isNotEmpty() && readyCount == pairs.size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClipForgeColors.CrimsonAccent)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Render 16:9 Compilation", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live Progress Card
        if (isRendering || renderState is RenderState.Completed || renderState is RenderState.Failed || renderState is RenderState.Cancelled) {
            item {
                AmoledCard {
                    val pct = (renderStats.progress * 100f).roundToInt().coerceIn(0, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (renderState) {
                                is RenderState.Preparing -> "Preparing FFmpeg pipeline..."
                                is RenderState.Rendering -> "Rendering (${renderStats.encoderLabel}) • $pct%"
                                is RenderState.Completed -> "Render Complete • 100%"
                                is RenderState.Failed -> "Render Failed"
                                is RenderState.Cancelled -> "Render Cancelled"
                                RenderState.Idle -> ""
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = when (renderState) {
                                is RenderState.Completed -> ClipForgeColors.SurvivedGreen
                                is RenderState.Failed -> ClipForgeColors.TragicRed
                                else -> ClipForgeColors.TextPrimary
                            }
                        )
                        if (isRendering && renderStats.etaSeconds > 0L) {
                            Text(
                                text = "ETA ${renderStats.etaSeconds}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = ClipForgeColors.CrimsonAccent
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { renderStats.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (renderState is RenderState.Completed) ClipForgeColors.SurvivedGreen else ClipForgeColors.CrimsonAccent,
                        trackColor = ClipForgeColors.ElevatedSurface
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Frame ${renderStats.currentFrame} / ${renderStats.totalFrames}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ClipForgeColors.TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f fps • %.2fx", renderStats.fps, renderStats.speed),
                            style = MaterialTheme.typography.labelSmall,
                            color = ClipForgeColors.TextSecondary
                        )
                    }

                    if (renderState is RenderState.Failed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = renderState.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = ClipForgeColors.TragicRed
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showLogs = !showLogs },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (showLogs) "Hide FFmpeg Logs" else "Show FFmpeg Logs (${renderStats.logs.size})")
                    }

                    if (showLogs && renderStats.logs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClipForgeColors.AmoledBlack)
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = renderStats.logs.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = ClipForgeColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Built-in ExoPlayer Preview + Export Buttons
        if (renderState is RenderState.Completed) {
            item {
                AmoledCard(borderColor = ClipForgeColors.SurvivedGreen.copy(alpha = 0.5f)) {
                    Text(
                        text = "16:9 Compilation Preview",
                        style = MaterialTheme.typography.titleMedium,
                        color = ClipForgeColors.TextPrimary
                    )
                    if (!statusBanner.isNullOrBlank()) {
                        Text(
                            text = statusBanner,
                            style = MaterialTheme.typography.labelMedium,
                            color = ClipForgeColors.SurvivedGreen
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    ExoVideoPreview(
                        videoUri = renderState.savedGalleryUri ?: Uri.fromFile(File(renderState.outputPath)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.saveCompletedToGallery() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save to Gallery", maxLines = 1)
                        }

                        Button(
                            onClick = {
                                StorageHelper.shareVideo(
                                    context = context,
                                    outputPath = renderState.outputPath,
                                    galleryUri = renderState.savedGalleryUri,
                                    targetYouTube = true
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ClipForgeColors.CrimsonAccent)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share to YouTube", maxLines = 1)
                        }
                    }
                }
            }
        }

        // Debug FFmpeg filter_complex Command Toggle
        item {
            AmoledCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Copy FFmpeg Command (Debug)", style = MaterialTheme.typography.titleMedium, color = ClipForgeColors.TextPrimary)
                        Text("Inspect exact generated filter_complex pipeline", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextSecondary)
                    }
                    Switch(
                        checked = showCommandDebug,
                        onCheckedChange = { viewModel.toggleCommandDebug(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ClipForgeColors.CrimsonAccent)
                    )
                }

                if (showCommandDebug) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ClipForgeColors.AmoledBlack)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = renderStats.commandPreview.ifBlank { "No command generated yet." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = ClipForgeColors.SurvivedGreen
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(renderStats.commandPreview)) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy FFmpeg Command")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExoVideoPreview(
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            view.player = exoPlayer
        }
    )
}
