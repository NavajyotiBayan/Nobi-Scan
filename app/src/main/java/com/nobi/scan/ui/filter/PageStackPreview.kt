package com.nobi.scan.ui.filter

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.nobi.scan.ui.theme.LocalNobiPalette

@Composable
internal fun PageStackPreview(
    bitmap: Bitmap?,
    pageKey: String,
    selectedIndex: Int,
    pageCount: Int,
    enabled: Boolean,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNobiPalette.current
    var drag by remember { mutableFloatStateOf(0f) }
    var precedingIndex by remember { mutableIntStateOf(selectedIndex) }
    val selectPage by rememberUpdatedState(onSelectPage)
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(pageKey, selectedIndex, bitmap != null) {
        if (bitmap == null) return@LaunchedEffect
        val direction = (selectedIndex - precedingIndex).coerceIn(-1, 1)
        precedingIndex = selectedIndex
        entrance.snapTo(direction * .20f)
        entrance.animateTo(0f, spring(dampingRatio = .78f, stiffness = Spring.StiffnessMediumLow))
    }
    val relaxedDrag by animateFloatAsState(
        targetValue = drag,
        animationSpec = spring(dampingRatio = .76f, stiffness = Spring.StiffnessMedium),
        label = "page stack spring"
    )
    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(20.dp)).pointerInput(enabled, selectedIndex, pageCount) {
            if (enabled && pageCount > 1) detectHorizontalDragGestures(
                onDragCancel = { drag = 0f },
                onDragEnd = {
                    val destination = selectedIndex + if (drag < 0f) 1 else -1
                    if (abs(drag) > size.width * .13f && destination in 0 until pageCount) {
                        selectPage(destination)
                    }
                    drag = 0f
                },
            ) { change, amount ->
                change.consume()
                drag = (drag + amount).coerceIn(-size.width * .35f, size.width * .35f)
            }
        },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) return@BoxWithConstraints
        val ratio = bitmap.width.toFloat() / bitmap.height
        val width = minOf(maxWidth, maxHeight * ratio) * if (pageCount > 1) .91f else .98f
        val height = width / ratio
        for (level in minOf(pageCount - 1, 2) downTo 1) {
            Box(Modifier.size(width, height).graphicsLayer {
                translationX = (level * 5).dp.toPx() + relaxedDrag * .05f * level
                translationY = (level * 3).dp.toPx()
                rotationZ = (level * 2).toFloat()
                scaleX = 1f - level * .025f
                scaleY = scaleX
            }.background(if (level == 1) palette.surface else palette.lens, shape)
                .border(1.dp, palette.rim, shape))
        }
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = "Corrected page preview",
            modifier = Modifier.size(width, height).graphicsLayer {
                translationX = relaxedDrag * .56f + entrance.value * size.width
                rotationZ = (relaxedDrag / size.width.coerceAtLeast(1f) * 12f) + entrance.value * 9f
                val scale = 1f - abs(entrance.value) * .15f
                scaleX = scale
                scaleY = scale
            }.shadow(5.dp, shape).clip(shape).background(Color.White),
            contentScale = ContentScale.Fit
        )
    }
}
