package com.nobi.scan.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobi.scan.ui.theme.LocalNobiPalette
import com.nobi.scan.ui.theme.LiquidGlassBorder
import com.nobi.scan.ui.theme.LiquidGlassCard
import com.nobi.scan.ui.theme.LiquidGlassRim
import com.nobi.scan.ui.theme.LiquidGlassRimSubtle
import com.nobi.scan.ui.theme.LiquidGlassShadow

import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial

/**
 * Ambient Iridescent Aura mesh gradient canvas matching the iOS 27 design.
 */
@Composable
fun AuraBackgroundBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalNobiPalette.current
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(palette.background, palette.backdropTint, palette.background))
        ),
        content = content
    )
}

/**
 * High-fidelity Liquid Glass card with specular rim lighting and ambient drop shadow.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .shadow(
            elevation = 16.dp,
            shape = shape,
            ambientColor = LiquidGlassShadow,
            spotColor = LiquidGlassShadow
        )
        .clip(shape)
        .background(LiquidGlassCard)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(LiquidGlassRim, LiquidGlassRimSubtle, LiquidGlassBorder)
            ),
            shape = shape
        )

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier.padding(14.dp)
    ) {
        content()
    }
}

/**
 * Hero Liquid Purple Pill button with glowing elevation and specular highlight.
 */
@Composable
fun LiquidPurplePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    framedIcon: Boolean = false
) {
    val palette = LocalNobiPalette.current
    val shape = RoundedCornerShape(50)
    val interactions = remember { MutableInteractionSource() }
    GlassSurface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(palette.accent)
            .glassPress(interactions)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactions,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        style = GlassSurfaceStyle.Navigation,
        emphasis = GlassEmphasis.Prominent,
        navigationSurfaceColor = palette.accent,
        enabled = enabled,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = if (framedIcon) {
                        Modifier.size(34.dp).background(palette.onAccent.copy(alpha = .17f), CircleShape)
                    } else Modifier,
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.onAccent,
                        modifier = Modifier.size(if (framedIcon) 20.dp else 17.dp)
                    )
                }
            }
            Text(
                text = text,
                color = palette.onAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

enum class NavigationTab {
    HOME,
    SCAN,
    TOOLS,
    SETTINGS
}

/**
 * Floating Liquid Glass Bottom Navigation Dock.
 */
@Composable
fun LiquidBottomDock(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberNobiHaptics()
    fun selectByTap(tab: NavigationTab) {
        if (tab == currentTab) return
        haptics.step()
        onTabSelected(tab)
    }
    val palette = LocalNobiPalette.current
    val shape = RoundedCornerShape(9999.dp)

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .fillMaxWidth()
    ) {
        GlassSurface(
            modifier = Modifier.matchParentSize().graphicsLayer(alpha = if (palette.isDark) .78f else .58f),
            shape = shape,
            style = GlassSurfaceStyle.Navigation,
            material = NavigationGlassMaterial.Clear,
            navigationSurfaceColor = palette.glass,
            preserveBackdropColors = true,
            frostRadius = 2.dp
        ) {}
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val tabs = listOf(NavigationTab.HOME, NavigationTab.TOOLS, NavigationTab.SETTINGS)
            val activeIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
            val itemWidth = (maxWidth - 4.dp * tabs.lastIndex) / tabs.size
            val stride = itemWidth + 4.dp
            val stridePx = with(LocalDensity.current) { stride.toPx() }
            val readingDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
            val selectTab by rememberUpdatedState(onTabSelected)
            val position = remember { Animatable(activeIndex.toFloat()) }
            var dragPosition by remember { mutableStateOf<Float?>(null) }
            var dragging by remember { mutableStateOf(false) }

            LaunchedEffect(activeIndex, dragging) {
                if (!dragging) {
                    dragPosition?.let { position.snapTo(it) }
                    dragPosition = null
                    position.animateTo(
                        activeIndex.toFloat(),
                        spring(dampingRatio = .74f, stiffness = Spring.StiffnessMediumLow)
                    )
                }
            }

            val lift by animateFloatAsState(
                targetValue = if (dragging) 1.06f else 1f,
                spring(dampingRatio = .75f, stiffness = Spring.StiffnessMedium),
                label = "glass dock lift"
            )
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset(((dragPosition ?: position.value) * stridePx).roundToInt(), 0) }
                    .width(itemWidth).height(48.dp)
                    .graphicsLayer {
                        scaleX = lift
                        scaleY = lift
                    }
                    .background(
                        Brush.verticalGradient(listOf(palette.glass, palette.lens.copy(alpha = .76f))),
                        shape
                    ).border(1.dp, palette.rim, shape)
            )
            Row(
                modifier = Modifier.fillMaxWidth().pointerInput(stridePx, readingDirection) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (stridePx > 0f) {
                                dragPosition = dragPosition ?: position.value
                                dragging = true
                                haptics.grab()
                            }
                        },
                        onDragCancel = { dragging = false },
                        onDragEnd = {
                            val destination = dragPosition?.roundToInt()?.coerceIn(tabs.indices)
                            dragging = false
                            destination?.let { selectTab(tabs[it]) }
                        },
                    ) { change, delta ->
                        val current = dragPosition
                        if (current != null && dragging) {
                            change.consume()
                            val next = (current + readingDirection * delta / stridePx)
                                .coerceIn(0f, tabs.lastIndex.toFloat())
                            if (next.roundToInt() != current.roundToInt()) {
                                haptics.step()
                            }
                            dragPosition = next
                        }
                    }
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockItem(
                    modifier = Modifier.weight(1f),
                    label = "Home",
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    isSelected = currentTab == NavigationTab.HOME,
                    onClick = { selectByTap(NavigationTab.HOME) }
                )

                DockItem(
                    modifier = Modifier.weight(1f),
                    label = "Tools",
                    selectedIcon = Icons.Filled.Widgets,
                    unselectedIcon = Icons.Outlined.Widgets,
                    isSelected = currentTab == NavigationTab.TOOLS,
                    onClick = { selectByTap(NavigationTab.TOOLS) }
                )
                DockItem(
                    modifier = Modifier.weight(1f),
                    label = "Settings",
                    selectedIcon = Icons.Filled.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    isSelected = currentTab == NavigationTab.SETTINGS,
                    onClick = { selectByTap(NavigationTab.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun DockItem(
    modifier: Modifier,
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalNobiPalette.current
    val tintColor by animateColorAsState(
        targetValue = if (isSelected) palette.accent else palette.mutedInk,
        label = "dock_tint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tintColor,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
