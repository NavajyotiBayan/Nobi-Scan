package com.nobi.scan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun Modifier.glassPress(interactions: MutableInteractionSource): Modifier {
    val scale = remember { Animatable(1f) }
    val ripple = remember { Animatable(1f) }
    val position = remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interactions) {
        interactions.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    position.value = interaction.pressPosition
                    ripple.snapTo(0f)
                    scale.animateTo(.94f, tween(80))
                }
                is PressInteraction.Release -> kotlinx.coroutines.coroutineScope {
                    launch { scale.animateTo(1f, spring(dampingRatio = .52f, stiffness = Spring.StiffnessMedium)) }
                    ripple.animateTo(1f, tween(250))
                }
                is PressInteraction.Cancel -> {
                    ripple.snapTo(1f)
                    scale.animateTo(1f, spring())
                }
            }
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.drawWithContent {
        drawContent()
        val strength = 1f - ripple.value
        if (strength > 0f) {
            val origin = position.value.takeIf { it != Offset.Unspecified } ?: center
            val radius = size.maxDimension
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .42f * strength), Color.Transparent),
                    center = origin,
                    radius = radius * .55f
                ),
                radius = radius * .55f,
                center = origin
            )
            drawCircle(
                color = Color.White.copy(alpha = .4f * strength),
                radius = radius * (.12f + ripple.value),
                center = origin,
                style = Stroke(1.dp.toPx())
            )
        }
    }
}
