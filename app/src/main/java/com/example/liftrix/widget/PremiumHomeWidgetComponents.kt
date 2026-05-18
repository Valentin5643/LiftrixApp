package com.example.liftrix.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.liftrix.R

internal val WidgetFontFamily = FontFamily("Inter")
internal val WidgetDisplayFontFamily = FontFamily("Plus Jakarta Sans")

internal data class WidgetVolumeParts(
    val value: String,
    val unit: String
)

internal fun splitVolumeLabel(label: String): WidgetVolumeParts {
    val trimmed = label.trim()
    val separatorIndex = trimmed.lastIndexOf(' ')
    if (separatorIndex <= 0 || separatorIndex >= trimmed.lastIndex) {
        return WidgetVolumeParts(value = trimmed, unit = "")
    }
    return WidgetVolumeParts(
        value = trimmed.substring(0, separatorIndex),
        unit = trimmed.substring(separatorIndex + 1)
    )
}

internal fun hasVolumeData(label: String): Boolean {
    return splitVolumeLabel(label).value != "0"
}

@Composable
internal fun PremiumWidgetSurface(
    modifier: GlanceModifier,
    cornerRadius: Dp,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    val backgroundRes = when {
        cornerRadius <= 18.dp -> R.drawable.widget_card_small_premium
        compact -> R.drawable.widget_card_compact_premium
        else -> R.drawable.widget_card_premium
    }
    Box(
        modifier = modifier
            .appWidgetBackground()
            .background(
                ImageProvider(backgroundRes),
                ContentScale.FillBounds
            )
            .cornerRadius(cornerRadius)
    ) {
        content()
    }
}

@Composable
internal fun PremiumIcon(
    icon: Int = R.drawable.ic_widget_volume,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    containerRadius: Dp = 10.dp,
    containerColor: Color = Color(0xFF083E4D),
    iconColor: Color = Color(0xFF00BCD4)
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ColorProvider(containerColor))
            .cornerRadius(containerRadius),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(ColorProvider(iconColor))
        )
    }
}

@Composable
internal fun PremiumVolumeValue(
    label: String,
    valueSize: Int,
    unitSize: Int,
    active: Boolean,
    unitBottomPadding: Dp = 0.dp,
    modifier: GlanceModifier = GlanceModifier
) {
    val parts = splitVolumeLabel(label)
    val color = if (active) LiftrixWidgetTheme.onBackground else LiftrixWidgetTheme.zeroValue
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = parts.value,
            maxLines = 1,
            style = TextStyle(
                color = color,
                fontSize = valueSize.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = WidgetDisplayFontFamily
            )
        )
        if (parts.unit.isNotBlank()) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = parts.unit,
                modifier = GlanceModifier.padding(bottom = unitBottomPadding),
                maxLines = 1,
                style = TextStyle(
                    color = color,
                    fontSize = unitSize.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = WidgetDisplayFontFamily
                )
            )
        }
    }
}

@Composable
internal fun PremiumWeekStrip(
    days: List<ConsistencyDay>,
    modifier: GlanceModifier = GlanceModifier,
    compact: Boolean = false,
    barWidth: Dp = 22.dp,
    barHeight: Dp = if (compact) 48.dp else 70.dp,
    spacing: Dp = if (compact) 4.dp else 5.dp,
    weightedColumns: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.Bottom
    ) {
        days.take(5).forEachIndexed { index, day ->
            val cellModifier = if (weightedColumns) {
                GlanceModifier.defaultWeight()
            } else {
                GlanceModifier
            }
            PremiumDayCell(
                day = day,
                barWidth = barWidth,
                barHeight = barHeight,
                weightedColumn = weightedColumns,
                modifier = cellModifier
            )
            if (!weightedColumns && index < 4) {
                Spacer(modifier = GlanceModifier.width(spacing))
            }
        }
    }
}

@Composable
internal fun PremiumWeekPanel(
    days: List<ConsistencyDay>,
    title: String,
    modifier: GlanceModifier = GlanceModifier,
    compact: Boolean = false
) {
    val active = days.any { it.isActive }
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                Spacer(
                    modifier = GlanceModifier
                        .size(5.dp)
                        .background(LiftrixWidgetTheme.brand)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
            Text(
                text = title.uppercase(),
                maxLines = 1,
                style = TextStyle(
                    color = LiftrixWidgetTheme.onSurfaceSubtle,
                    fontSize = if (compact) 8.sp else 9.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = WidgetFontFamily
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(if (compact) 6.dp else 8.dp))
        PremiumWeekStrip(
            days = days,
            modifier = GlanceModifier.fillMaxWidth(),
            compact = compact,
            barWidth = if (compact) 22.dp else 22.dp,
            barHeight = if (compact) 48.dp else 70.dp,
            spacing = if (compact) 4.dp else 5.dp,
            weightedColumns = true
        )
    }
}

@Composable
internal fun PremiumWeekBlock(
    days: List<ConsistencyDay>,
    title: String = "THIS WEEK",
    modifier: GlanceModifier = GlanceModifier,
    compact: Boolean = false
) {
    val active = days.any { it.isActive }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                Spacer(
                    modifier = GlanceModifier
                        .size(5.dp)
                        .background(LiftrixWidgetTheme.brand)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    color = LiftrixWidgetTheme.onSurfaceSubtle,
                    fontSize = if (compact) 8.sp else 9.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = WidgetFontFamily
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(if (compact) 6.dp else 8.dp))
        PremiumWeekStrip(
            days = days,
            modifier = GlanceModifier.fillMaxWidth(),
            compact = compact,
            barWidth = 22.dp,
            barHeight = if (compact) 48.dp else 70.dp,
            spacing = if (compact) 4.dp else 5.dp,
            weightedColumns = compact
        )
    }
}

@Composable
private fun PremiumDayCell(
    day: ConsistencyDay,
    barWidth: Dp,
    barHeight: Dp,
    weightedColumn: Boolean,
    modifier: GlanceModifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val barModifier = if (weightedColumn) {
            GlanceModifier
                .fillMaxWidth()
                .height(barHeight)
        } else {
            GlanceModifier
                .width(barWidth)
                .height(barHeight)
        }

        Spacer(
            modifier = barModifier
                .background(
                    ImageProvider(
                        if (day.isActive) {
                            R.drawable.widget_bar_active
                        } else {
                            R.drawable.widget_bar_inactive
                        }
                    ),
                    ContentScale.FillBounds
                )
        )
        Spacer(modifier = GlanceModifier.height(5.dp))
        Text(
            text = day.dateLabel.uppercase(),
            maxLines = 1,
            style = TextStyle(
                color = if (day.isToday && day.isActive) {
                    LiftrixWidgetTheme.onSurfaceVariant
                } else {
                    LiftrixWidgetTheme.onSurfaceSubtle
                },
                fontSize = 8.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = WidgetFontFamily
            )
        )
    }
}
