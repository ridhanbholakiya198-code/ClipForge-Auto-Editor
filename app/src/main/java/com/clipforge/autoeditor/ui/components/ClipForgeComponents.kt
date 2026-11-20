package com.clipforge.autoeditor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clipforge.autoeditor.model.OutcomeBadge
import com.clipforge.autoeditor.ui.theme.ClipForgeColors
import java.util.Locale

@Composable
fun AmoledCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ClipForgeColors.BorderSubtle,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ClipForgeColors.CardSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun OutcomeBadgeChip(
    badge: OutcomeBadge,
    selected: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val (bg, fg) = when (badge) {
        OutcomeBadge.SURVIVED -> ClipForgeColors.SurvivedBg to ClipForgeColors.SurvivedGreen
        OutcomeBadge.TRAGIC -> ClipForgeColors.TragicBg to ClipForgeColors.TragicRed
        OutcomeBadge.NONE -> ClipForgeColors.ElevatedSurface to ClipForgeColors.TextSecondary
    }
    val border = if (selected) fg.copy(alpha = 0.8f) else ClipForgeColors.BorderSubtle
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) bg else ClipForgeColors.AmoledBlack)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) fg else ClipForgeColors.TextMuted
        )
    }
}

@Composable
fun MediaSlotButton(
    label: String,
    attachedFileName: String,
    placeholderHint: String,
    isAttached: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isAttached) ClipForgeColors.SurvivedGreen.copy(alpha = 0.45f) else ClipForgeColors.CrimsonAccent.copy(alpha = 0.65f)
    val bgColor = if (isAttached) ClipForgeColors.ElevatedSurface else ClipForgeColors.CrimsonContainer.copy(alpha = 0.25f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isAttached) Icons.Rounded.CheckCircle else Icons.Rounded.VideoLibrary,
            contentDescription = null,
            tint = if (isAttached) ClipForgeColors.SurvivedGreen else ClipForgeColors.CrimsonAccent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = ClipForgeColors.TextSecondary
            )
            Text(
                text = if (isAttached) attachedFileName else (placeholderHint.ifBlank { "Tap to pick video from storage" }),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isAttached) FontWeight.Medium else FontWeight.SemiBold,
                color = if (isAttached) ClipForgeColors.TextPrimary else ClipForgeColors.CrimsonAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isAttached) ClipForgeColors.SurvivedGreen else ClipForgeColors.CrimsonAccent)
        )
    }
}

@Composable
fun TrimRangeControl(
    title: String,
    startSec: Float,
    endSec: Float,
    maxDurationSec: Float,
    onRangeChange: (Float, Float) -> Unit
) {
    val safeMax = maxDurationSec.coerceAtLeast(2f)
    val clampedStart = startSec.coerceIn(0f, safeMax - 0.5f)
    val clampedEnd = endSec.coerceIn(clampedStart + 0.5f, safeMax)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = ClipForgeColors.TextSecondary
            )
            Text(
                text = String.format(
                    Locale.US,
                    "%.1fs - %.1fs (%.1fs)",
                    clampedStart,
                    clampedEnd,
                    clampedEnd - clampedStart
                ),
                style = MaterialTheme.typography.labelSmall,
                color = ClipForgeColors.TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        RangeSlider(
            value = clampedStart..clampedEnd,
            onValueChange = { range ->
                val s = range.start.coerceAtLeast(0f)
                val e = range.endInclusive.coerceAtLeast(s + 0.5f)
                onRangeChange(s, e)
            },
            valueRange = 0f..safeMax,
            colors = SliderDefaults.colors(
                thumbColor = ClipForgeColors.CrimsonAccent,
                activeTrackColor = ClipForgeColors.CrimsonAccent,
                inactiveTrackColor = ClipForgeColors.BorderSubtle
            )
        )
    }
}
