package com.clipforge.autoeditor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.autoeditor.ui.components.AmoledCard
import com.clipforge.autoeditor.ui.components.MediaSlotButton
import com.clipforge.autoeditor.ui.components.OutcomeBadgeChip
import com.clipforge.autoeditor.ui.theme.ClipForgeColors
import com.clipforge.autoeditor.viewmodel.ClipForgeViewModel

private sealed interface ScriptPickTarget {
    data class ClipA(val pairId: String) : ScriptPickTarget
    data class RealAngle(val pairId: String, val angleId: String) : ScriptPickTarget
}

@Composable
fun AiScriptScreen(
    scriptInput: String,
    parseMessage: String?,
    pairs: List<com.clipforge.autoeditor.model.StoryPair>,
    viewModel: ClipForgeViewModel,
    onNavigateToRender: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var pendingTarget by remember { mutableStateOf<ScriptPickTarget?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            when (val target = pendingTarget) {
                is ScriptPickTarget.ClipA -> viewModel.attachClipAVideo(target.pairId, uri)
                is ScriptPickTarget.RealAngle -> viewModel.attachRealAngleVideo(target.pairId, target.angleId, uri)
                null -> Unit
            }
        }
        pendingTarget = null
    }

    val bulkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { u ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            viewModel.attachVideosInOrder(uris)
        }
    }

    val allReady = pairs.isNotEmpty() && pairs.all { it.isReadyToRender }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "AI Script Mode",
                style = MaterialTheme.typography.headlineSmall,
                color = ClipForgeColors.TextPrimary
            )
            Text(
                text = "Paste a JSON Story Pair script, tap each video slot to attach clips, and render.",
                style = MaterialTheme.typography.bodyMedium,
                color = ClipForgeColors.TextSecondary
            )
        }

        item {
            AmoledCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            if (pasted.isNotBlank()) {
                                viewModel.updateScriptInput(pasted)
                                viewModel.parseScriptIntoPairs()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Paste Script", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { viewModel.loadSampleTemplate() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.DataObject, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Load Sample", maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = scriptInput,
                    onValueChange = { viewModel.updateScriptInput(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = ClipForgeColors.TextPrimary
                    ),
                    placeholder = {
                        Text(
                            "Paste Story Pairs JSON array here...",
                            style = MaterialTheme.typography.labelSmall,
                            color = ClipForgeColors.TextMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ClipForgeColors.AmoledBlack,
                        unfocusedContainerColor = ClipForgeColors.AmoledBlack,
                        focusedBorderColor = ClipForgeColors.CrimsonAccent,
                        unfocusedBorderColor = ClipForgeColors.BorderSubtle
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.parseScriptIntoPairs() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClipForgeColors.ElevatedSurface,
                        contentColor = ClipForgeColors.TextPrimary
                    )
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ClipForgeColors.CrimsonAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-Build Story Cards", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { bulkPickerLauncher.launch(arrayOf("video/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Attach ALL videos at once (auto-order by file name)", maxLines = 2)
                }

                if (!parseMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val isErr = parseMessage.startsWith("JSON Error")
                    Text(
                        text = parseMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isErr) ClipForgeColors.TragicRed else ClipForgeColors.SurvivedGreen
                    )
                }
            }
        }

        itemsIndexed(pairs, key = { _, p -> p.id }) { index, pair ->
            AmoledCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PAIR #${index + 1} • ${pair.locationYear.ifBlank { "NO LOCATION" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ClipForgeColors.CrimsonAccent
                        )
                        Text(
                            text = pair.storyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = ClipForgeColors.TextPrimary
                        )
                    }
                    OutcomeBadgeChip(badge = pair.outcomeBadge)
                }

                if (pair.shockFact.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pair.shockFact,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ClipForgeColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                MediaSlotButton(
                    label = "Clip A (9:16 Animation • ${pair.clipAStartSec}s–${pair.clipAEndSec}s)",
                    attachedFileName = pair.clipADisplayName,
                    placeholderHint = pair.clipAPlaceholderLabel,
                    isAttached = pair.clipAUri != null,
                    onClick = {
                        pendingTarget = ScriptPickTarget.ClipA(pair.id)
                        videoPickerLauncher.launch(arrayOf("video/*"))
                    }
                )

                pair.realClips.forEachIndexed { angleIdx, angle ->
                    Spacer(modifier = Modifier.height(8.dp))
                    MediaSlotButton(
                        label = "Real Footage Angle #${angleIdx + 1} (${angle.startSec}s–${angle.endSec}s)",
                        attachedFileName = angle.displayName,
                        placeholderHint = angle.placeholderLabel,
                        isAttached = angle.uri != null,
                        onClick = {
                            pendingTarget = ScriptPickTarget.RealAngle(pair.id, angle.id)
                            videoPickerLauncher.launch(arrayOf("video/*"))
                        }
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (allReady) {
                        viewModel.startRender()
                    }
                    onNavigateToRender()
                },
                enabled = pairs.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClipForgeColors.CrimsonAccent,
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (allReady) "Render 16:9 Compilation" else "Review & Render (${pairs.count { it.isReadyToRender }}/${pairs.size} Ready)",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
