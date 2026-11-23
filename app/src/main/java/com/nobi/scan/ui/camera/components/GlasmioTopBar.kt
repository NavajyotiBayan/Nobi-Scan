package com.nobi.scan.ui.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobi.scan.ui.theme.AccentAmber
import com.nobi.scan.ui.theme.AccentBlue
import com.nobi.scan.ui.theme.AccentCyan
import com.nobi.scan.ui.theme.GlassBackground
import com.nobi.scan.ui.theme.GlassRimBorder
import com.nobi.scan.ui.theme.GlassRimLight

enum class FlashMode {
    OFF, ON, AUTO
}

@Composable
fun GlasmioTopBar(
    flashMode: FlashMode,
    isAutoCapture: Boolean,
    rustLatencyMs: Float,
    onFlashToggle: () -> Unit,
    onAutoCaptureToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(GlassBackground)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(GlassRimLight, GlassRimBorder)
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Mode Pill
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(onClick = onFlashToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        FlashMode.OFF -> Icons.Default.FlashOff
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                    },
                    contentDescription = "Flash Toggle",
                    tint = if (flashMode == FlashMode.OFF) Color.White.copy(alpha = 0.7f) else AccentAmber,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Center: Auto / Manual Mode Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isAutoCapture) AccentBlue else Color.Transparent)
                        .clickable { if (!isAutoCapture) onAutoCaptureToggle() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AUTO",
                        color = if (isAutoCapture) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!isAutoCapture) AccentBlue else Color.Transparent)
                        .clickable { if (isAutoCapture) onAutoCaptureToggle() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "MANUAL",
                        color = if (!isAutoCapture) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right: Rust Core Telemetry Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                val latencyText = if (rustLatencyMs > 0f) String.format("%.1fms", rustLatencyMs) else "Rust Core"
                Text(
                    text = "⚡ $latencyText",
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
