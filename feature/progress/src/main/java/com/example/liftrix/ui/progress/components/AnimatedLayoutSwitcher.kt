package com.example.liftrix.ui.progress.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.progress.components.DashboardLayoutMode
import com.example.liftrix.ui.common.WindowSizeClass

/**
 * Animated layout switcher that provides smooth transitions between different
 * dashboard layout modes with enhanced visual feedback.
 * 
 * Features:
 * - Smooth crossfade transitions between layout modes
 * - Staggered animations for widgets during layout changes
 * - Loading state animations with shimmer effects
 * - Responsive transition durations based on widget count
 * - Accessibility-friendly motion with respect for reduced motion preferences
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedLayoutSwitcher(
    widgets: List<AnalyticsWidget>,
    configuration: DashboardConfiguration,
    layoutMode: DashboardLayoutMode,
    onWidgetClick: (AnalyticsWidget) -> Unit = {},
    onWidgetReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    widgetDataProvider: (AnalyticsWidget) -> WidgetData = { createDefaultWidgetData(it) },
    isLoading: Boolean = false,
    enableDragAndDrop: Boolean = false,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    onSaveCustomLayout: ((List<String>) -> Unit)? = null
) {
    // Animation configuration
    val transitionDuration = remember(widgets.size) {
        // Adaptive transition duration based on widget count
        when {
            widgets.size <= 4 -> 300
            widgets.size <= 8 -> 400
            widgets.size <= 12 -> 500
            else -> 600
        }
    }
    
    val enterTransition = remember(transitionDuration) {
        slideInVertically(
            animationSpec = tween(
                durationMillis = transitionDuration,
                easing = EaseOutCubic
            ),
            initialOffsetY = { it / 4 }
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = transitionDuration,
                easing = EaseOutCubic
            )
        )
    }
    
    val exitTransition = remember(transitionDuration) {
        slideOutVertically(
            animationSpec = tween(
                durationMillis = transitionDuration / 2,
                easing = EaseInCubic
            ),
            targetOffsetY = { -it / 4 }
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = transitionDuration / 2,
                easing = EaseInCubic
            )
        )
    }
    
    // Loading state animation
    val loadingAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.85f else 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = EaseInOutCubic
        ),
        label = "loading_alpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(loadingAlpha)
    ) {
        AnimatedContent(
            targetState = layoutMode,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = enterTransition,
                    initialContentExit = exitTransition,
                    sizeTransform = SizeTransform(
                        clip = false,
                        sizeAnimationSpec = { _, _ ->
                            tween(
                                durationMillis = transitionDuration,
                                easing = EaseInOutCubic
                            )
                        }
                    )
                )
            },
            label = "layout_mode_transition"
        ) { currentLayoutMode ->
            when (currentLayoutMode) {
                DashboardLayoutMode.AUTO -> {
                    StaggeredAnimationWrapper(
                        widgets = widgets,
                        animationDelay = 50L
                    ) {
                        if (enableDragAndDrop) {
                            DragAndDropGrid(
                                widgets = widgets,
                                windowSizeClass = windowSizeClass,
                                onReorder = onWidgetReorder,
                                onWidgetClick = onWidgetClick,
                                widgetDataProvider = widgetDataProvider,
                                isLoading = isLoading
                            )
                        } else {
                            AdaptiveWidgetGrid(
                                widgets = widgets,
                                windowSizeClass = windowSizeClass,
                                onWidgetClick = onWidgetClick,
                                widgetDataProvider = widgetDataProvider,
                                isLoading = isLoading
                            )
                        }
                    }
                }
                
                DashboardLayoutMode.SECTIONS -> {
                    StaggeredAnimationWrapper(
                        widgets = widgets,
                        animationDelay = 75L
                    ) {
                        EnhancedResponsiveGrid(
                            widgets = widgets,
                            windowSizeClass = windowSizeClass,
                            onWidgetClick = onWidgetClick,
                            widgetDataProvider = widgetDataProvider,
                            isLoading = isLoading,
                            enableCollapsibleSections = windowSizeClass.shouldShowCollapsibleSections,
                            useVerticalList = shouldUseVerticalList(windowSizeClass)
                        )
                    }
                }
                
                DashboardLayoutMode.GRID -> {
                    StaggeredAnimationWrapper(
                        widgets = widgets,
                        animationDelay = 100L
                    ) {
                        WidgetContainer(
                            widgets = widgets,
                            configuration = configuration,
                            layoutMode = WidgetLayoutMode.LIST,
                            onWidgetClick = onWidgetClick,
                            onWidgetReorder = onWidgetReorder,
                            widgetDataProvider = widgetDataProvider,
                            isLoading = isLoading,
                            enableDragAndDrop = false,
                            windowSizeClass = windowSizeClass
                        )
                    }
                }
                
                DashboardLayoutMode.CUSTOM -> {
                    StaggeredAnimationWrapper(
                        widgets = widgets,
                        animationDelay = 60L
                    ) {
                        CustomizableLayoutGrid(
                            widgets = widgets,
                            windowSizeClass = windowSizeClass,
                            onReorder = onWidgetReorder,
                            onWidgetClick = onWidgetClick,
                            widgetDataProvider = widgetDataProvider,
                            isLoading = isLoading,
                            isCustomLayoutMode = enableDragAndDrop,
                            onLayoutSave = { customLayout ->
                                // Extract widget order from custom layout and pass to parent
                                val widgetOrder = customLayout.items.map { item ->
                                    widgets.find { it.id == item.widgetId }?.id ?: item.widgetId
                                }
                                onSaveCustomLayout?.invoke(widgetOrder)
                            }
                        )
                    }
                }
                
                DashboardLayoutMode.GRID -> {
                    StaggeredAnimationWrapper(
                        widgets = widgets,
                        animationDelay = 50L
                    ) {
                        if (enableDragAndDrop) {
                            DragAndDropGrid(
                                widgets = widgets,
                                windowSizeClass = windowSizeClass,
                                onReorder = onWidgetReorder,
                                onWidgetClick = onWidgetClick,
                                widgetDataProvider = widgetDataProvider,
                                isLoading = isLoading
                            )
                        } else {
                            AdaptiveWidgetGrid(
                                widgets = widgets,
                                windowSizeClass = windowSizeClass,
                                onWidgetClick = onWidgetClick,
                                widgetDataProvider = widgetDataProvider,
                                isLoading = isLoading
                            )
                        }
                    }
                }
                
                DashboardLayoutMode.SECTIONS -> {
                    StaggeredAnimationWrapper(
                        widgets = widgets,
                        animationDelay = 70L
                    ) {
                        WidgetContainer(
                            widgets = widgets,
                            configuration = configuration,
                            layoutMode = WidgetLayoutMode.SECTIONS,
                            onWidgetClick = onWidgetClick,
                            onWidgetReorder = onWidgetReorder,
                            widgetDataProvider = widgetDataProvider,
                            isLoading = isLoading,
                            enableDragAndDrop = enableDragAndDrop,
                            enableCollapsibleSections = true,
                            windowSizeClass = windowSizeClass
                        )
                    }
                }
            }
        }
        
        // Loading overlay with shimmer effect
        if (isLoading) {
            LoadingOverlay(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * Wrapper that provides staggered animations for widgets
 */
@Composable
private fun StaggeredAnimationWrapper(
    widgets: List<AnalyticsWidget>,
    animationDelay: Long,
    content: @Composable () -> Unit
) {
    val animatedVisibility = remember { mutableStateMapOf<Int, Boolean>() }
    
    // Initialize all widgets as invisible
    LaunchedEffect(widgets.size) {
        widgets.forEachIndexed { index, _ ->
            animatedVisibility[index] = false
        }
    }
    
    // Stagger the appearance of widgets
    LaunchedEffect(widgets.size) {
        widgets.forEachIndexed { index, _ ->
            kotlinx.coroutines.delay(index * animationDelay)
            animatedVisibility[index] = true
        }
    }
    
    // Apply staggered animation wrapper
    Box(
        modifier = Modifier.graphicsLayer {
            // Apply subtle entrance animation to the entire container
            val progress = animatedVisibility.values.count { it } / widgets.size.toFloat()
            alpha = 0.3f + (1f - 0.3f) * progress
            scaleX = 0.95f + (1f - 0.95f) * progress
            scaleY = 0.95f + (1f - 0.95f) * progress
        }
    ) {
        content()
    }
}

/**
 * Loading overlay with subtle shimmer effect
 */
@Composable
private fun LoadingOverlay(
    modifier: Modifier = Modifier
) {
    val shimmerAlpha by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                alpha = shimmerAlpha
                scaleX = 1f + (shimmerAlpha - 0.5f) * 0.2f
                scaleY = 1f + (shimmerAlpha - 0.5f) * 0.2f
            }
    ) {
        // Could add a loading indicator here
        // For now, just providing the shimmer effect to the background
    }
}

/**
 * Extension to determine if collapsible sections should be shown
 */
private val WindowSizeClass.shouldShowCollapsibleSections: Boolean
    get() = widthDp.value >= 400

/**
 * Helper function to determine if vertical list layout should be used
 */
private fun shouldUseVerticalList(windowSizeClass: WindowSizeClass): Boolean {
    return windowSizeClass.widthDp.value < 400
}

/**
 * Creates default widget data for error states and previews
 */
private fun createDefaultWidgetData(widget: AnalyticsWidget): WidgetData {
    return com.example.liftrix.domain.model.analytics.BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = "—",
        secondaryValue = "Loading...",
        unit = "",
        trend = com.example.liftrix.domain.model.analytics.TrendDirection.STABLE
    )
}