package com.clipforge.autoeditor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clipforge.autoeditor.model.ClipAFramingMode
import com.clipforge.autoeditor.model.GlobalSettings
import com.clipforge.autoeditor.model.OutcomeBadge
import com.clipforge.autoeditor.model.StoryPair
import com.clipforge.autoeditor.model.TransitionSfx
import com.clipforge.autoeditor.model.WatermarkPosition
import com.clipforge.autoeditor.ui.components.AmoledCard
import com.clipforge.autoeditor.ui.components.MediaSlotButton
import com.clipforge.autoeditor.ui.components.OutcomeBadgeChip
import com.clipforge.autoeditor.ui.components.TrimRangeControl
import com.clipforge.autoeditor.ui.theme.ClipForgeColors
import com.clipforge.autoeditor.viewmodel.ClipForgeViewModel
import java.util.Locale
import kotlin.math.roundToInt

private sealed interface BuilderPickTarget {
    data class ClipA(val pairId: String) : BuilderPickTarget
    data class RealAngle(val pairId: String, val angleId: String) : BuilderPickTarget
}

@Composable
fun VisualBuilderScreen(
    pairs: List<StoryPair>,
    settings: GlobalSettings,
    viewModel: ClipForgeViewModel
) {
    val context = LocalContext.current
    var pendingVideoTarget by remember { mutableStateOf<BuilderPickTarget?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            when (val t = pendingVideoTarget) {
                is BuilderPickTarget.ClipA -> viewModel.attachClipAVideo(t.pairId, uri)
                is BuilderPickTarget.RealAngle -> viewModel.attachRealAngleVideo(t.pairId, t.angleId, uri)
                null -> Unit
            }
        }
        pendingVideoTarget = null
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.attachBackgroundMusic(uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Visual Pair Builder",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ClipForgeColors.TextPrimary
                    )
                    Text(
                        text = "Long-press drag handle or use arrows to reorder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ClipForgeColors.TextSecondary
                    )
                }
                Button(
                    onClick = { viewModel.addEmptyPair() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClipForgeColors.CrimsonAccent)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Pair")
                }
            }
        }

        itemsIndexed(pairs, key = { _, p -> p.id }) { index, pair ->
            var dragAccumulatedY by remember { mutableFloatStateOf(0f) }

            AmoledCard {
                // Header row with drag handle + reorder arrows + delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = "Drag to reorder",
                        tint = ClipForgeColors.TextSecondary,
                        modifier = Modifier
                            .size(28.dp)
                            .pointerInput(index, pairs.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragAccumulatedY = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragAccumulatedY += dragAmount.y
                                        val thresholdPx = 140f
                                        if (dragAccumulatedY > thresholdPx && index < pairs.lastIndex) {
                                            viewModel.movePair(index, index + 1)
                                            dragAccumulatedY = 0f
                                        } else if (dragAccumulatedY < -thresholdPx && index > 0) {
                                            viewModel.movePair(index, index - 1)
                                            dragAccumulatedY = 0f
                                        }
                                    }
                                )
                            }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "PAIR #${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ClipForgeColors.CrimsonAccent,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.movePair(index, index - 1) },
                        enabled = index > 0
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up")
                    }
                    IconButton(
                        onClick = { viewModel.movePair(index, index + 1) },
                        enabled = index < pairs.lastIndex
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down")
                    }
                    IconButton(onClick = { viewModel.deletePair(pair.id) }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete Pair",
                            tint = ClipForgeColors.TragicRed
                        )
                    }
                }

                // Story Title
                OutlinedTextField(
                    value = pair.storyTitle,
                    onValueChange = { viewModel.updatePairMetadata(pair.id, storyTitle = it) },
                    label = { Text("Story Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClipForgeColors.CrimsonAccent,
                        unfocusedBorderColor = ClipForgeColors.BorderSubtle
                    )
                )

                Spacer(Modifier.height(12.dp))

                // Clip A (9:16 Animation)
                MediaSlotButton(
                    label = "Clip A (9:16 Animation)",
                    attachedFileName = pair.clipADisplayName,
                    placeholderHint = pair.clipAPlaceholderLabel,
                    isAttached = pair.clipAUri != null,
                    onClick = {
                        pendingVideoTarget = BuilderPickTarget.ClipA(pair.id)
                        videoPicker.launch(arrayOf("video/*"))
                    }
                )

                Spacer(Modifier.height(8.dp))

                // Framing Mode Selector
                Text(
                    text = "Clip A 16:9 Framing",
                    style = MaterialTheme.typography.labelMedium,
                    color = ClipForgeColors.TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClipAFramingMode.entries.forEach { mode ->
                        val selected = pair.framingMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) ClipForgeColors.CrimsonContainer else ClipForgeColors.AmoledBlack)
                                .border(
                                    1.dp,
                                    if (selected) ClipForgeColors.CrimsonAccent else ClipForgeColors.BorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.updatePairMetadata(pair.id, framingMode = mode) }
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else ClipForgeColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                TrimRangeControl(
                    title = "Clip A Trim Range",
                    startSec = pair.clipAStartSec,
                    endSec = pair.clipAEndSec,
                    maxDurationSec = pair.clipAMaxDurationSec,
                    onRangeChange = { s, e -> viewModel.updateClipATrim(pair.id, s, e) }
                )

                HorizontalDivider(color = ClipForgeColors.BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))

                // Clip B Multi-Angle Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Clip B — Real Footage Angles (${pair.realClips.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ClipForgeColors.TextPrimary
                    )
                    OutlinedButton(
                        onClick = { viewModel.addRealAngle(pair.id) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Angle", style = MaterialTheme.typography.labelMedium)
                    }
                }

                pair.realClips.forEachIndexed { angleIdx, angle ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            MediaSlotButton(
                                label = "Real Angle #${angleIdx + 1}",
                                attachedFileName = angle.displayName,
                                placeholderHint = angle.placeholderLabel,
                                isAttached = angle.uri != null,
                                onClick = {
                                    pendingVideoTarget = BuilderPickTarget.RealAngle(pair.id, angle.id)
                                    videoPicker.launch(arrayOf("video/*"))
                                }
                            )
                        }
                        if (pair.realClips.size > 1) {
                            IconButton(onClick = { viewModel.removeRealAngle(pair.id, angle.id) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove Angle", tint = ClipForgeColors.TextMuted)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TrimRangeControl(
                        title = "Angle #${angleIdx + 1} Trim",
                        startSec = angle.startSec,
                        endSec = angle.endSec,
                        maxDurationSec = angle.maxDurationSec,
                        onRangeChange = { s, e -> viewModel.updateRealAngleTrim(pair.id, angle.id, s, e) }
                    )
                }

                HorizontalDivider(color = ClipForgeColors.BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))

                // Text Overlays
                Text(
                    text = "Text Overlays & Outcome Badge",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ClipForgeColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = pair.locationYear,
                    onValueChange = { viewModel.updatePairMetadata(pair.id, locationYear = it) },
                    label = { Text("Top-Left Location & Year (e.g. LOUISVILLE, 2015)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClipForgeColors.CrimsonAccent,
                        unfocusedBorderColor = ClipForgeColors.BorderSubtle
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pair.shockFact,
                    onValueChange = { viewModel.updatePairMetadata(pair.id, shockFact = it) },
                    label = { Text("Bottom 1-Line Shock Fact") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClipForgeColors.CrimsonAccent,
                        unfocusedBorderColor = ClipForgeColors.BorderSubtle
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pair.creditTag,
                    onValueChange = { viewModel.updatePairMetadata(pair.id, creditTag = it) },
                    label = { Text("Optional Credit Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClipForgeColors.CrimsonAccent,
                        unfocusedBorderColor = ClipForgeColors.BorderSubtle
                    )
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutcomeBadge.entries.forEach { badge ->
                        OutcomeBadgeChip(
                            badge = badge,
                            selected = pair.outcomeBadge == badge,
                            onClick = { viewModel.updatePairMetadata(pair.id, outcomeBadge = badge) }
                        )
                    }
                }
            }
        }

        // Global Settings Card
        item {
            AmoledCard(borderColor = ClipForgeColors.CrimsonAccent.copy(alpha = 0.45f)) {
                Text(
                    text = "Global Export & Audio Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = ClipForgeColors.TextPrimary
                )
                Spacer(Modifier.height(10.dp))

                // Channel Watermark
                OutlinedTextField(
                    value = settings.watermarkText,
                    onValueChange = { txt -> viewModel.updateGlobalSettings { it.copy(watermarkText = txt) } },
                    label = { Text("Channel Watermark Text (e.g. @YourChannel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClipForgeColors.CrimsonAccent,
                        unfocusedBorderColor = ClipForgeColors.BorderSubtle
                    )
                )

                Spacer(Modifier.height(10.dp))
                Text("Watermark Corner", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WatermarkPosition.entries.forEach { pos ->
                        val selected = settings.watermarkPosition == pos
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) ClipForgeColors.CrimsonContainer else ClipForgeColors.AmoledBlack)
                                .border(1.dp, if (selected) ClipForgeColors.CrimsonAccent else ClipForgeColors.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { viewModel.updateGlobalSettings { it.copy(watermarkPosition = pos) } }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pos.label.replace("Bottom ", "B-").replace("Top ", "T-"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else ClipForgeColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Watermark Opacity", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextSecondary)
                    Text("${(settings.watermarkOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = ClipForgeColors.TextPrimary)
                }
                Slider(
                    value = settings.watermarkOpacity,
                    onValueChange = { v -> viewModel.updateGlobalSettings { it.copy(watermarkOpacity = v) } },
                    valueRange = 0.05f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = ClipForgeColors.CrimsonAccent, activeTrackColor = ClipForgeColors.CrimsonAccent)
                )

                HorizontalDivider(color = ClipForgeColors.BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

                // Transition SFX
                Text("Transition SFX (Clip A → Real Footage)", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransitionSfx.entries.forEach { sfx ->
                        val selected = settings.transitionSfx == sfx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) ClipForgeColors.CrimsonContainer else ClipForgeColors.AmoledBlack)
                                .border(1.dp, if (selected) ClipForgeColors.CrimsonAccent else ClipForgeColors.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { viewModel.updateGlobalSettings { it.copy(transitionSfx = sfx) } }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sfx.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else ClipForgeColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SFX Volume", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextSecondary)
                    Text("${(settings.sfxVolume * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = ClipForgeColors.TextPrimary)
                }
                Slider(
                    value = settings.sfxVolume,
                    onValueChange = { v -> viewModel.updateGlobalSettings { it.copy(sfxVolume = v) } },
                    valueRange = 0f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = ClipForgeColors.CrimsonAccent, activeTrackColor = ClipForgeColors.CrimsonAccent)
                )

                HorizontalDivider(color = ClipForgeColors.BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

                // Background Dark Ambient Music + Auto-Ducking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Ambient Music", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextSecondary)
                        Text(
                            text = settings.bgMusicDisplayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (settings.bgMusicUri != null) ClipForgeColors.SurvivedGreen else ClipForgeColors.TextMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (settings.bgMusicUri != null) {
                            IconButton(onClick = { viewModel.clearBackgroundMusic() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear Music", tint = ClipForgeColors.TextMuted)
                            }
                        }
                        OutlinedButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pick Audio")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Auto-Ducking Depth (during real audio)", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextSecondary)
                    Text(String.format(Locale.US, "%.0f dB", settings.autoDuckDb), style = MaterialTheme.typography.labelSmall, color = ClipForgeColors.TextPrimary)
                }
                Slider(
                    value = settings.autoDuckDb,
                    onValueChange = { v -> viewModel.updateGlobalSettings { it.copy(autoDuckDb = v) } },
                    valueRange = -32f..0f,
                    colors = SliderDefaults.colors(thumbColor = ClipForgeColors.CrimsonAccent, activeTrackColor = ClipForgeColors.CrimsonAccent)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mute Music on Clip A Anchor", style = MaterialTheme.typography.bodyMedium, color = ClipForgeColors.TextPrimary)
                        Text("Silences ambient track while animation narration plays", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextMuted)
                    }
                    Switch(
                        checked = settings.muteAnchorMusic,
                        onCheckedChange = { checked -> viewModel.updateGlobalSettings { it.copy(muteAnchorMusic = checked) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ClipForgeColors.CrimsonAccent)
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware Encoder (h264_mediacodec)", style = MaterialTheme.typography.bodyMedium, color = ClipForgeColors.TextPrimary)
                        Text("Auto-falls back to libx264 if device codec fails", style = MaterialTheme.typography.labelMedium, color = ClipForgeColors.TextMuted)
                    }
                    Switch(
                        checked = settings.preferHardwareEncoder,
                        onCheckedChange = { checked -> viewModel.updateGlobalSettings { it.copy(preferHardwareEncoder = checked) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ClipForgeColors.CrimsonAccent)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
