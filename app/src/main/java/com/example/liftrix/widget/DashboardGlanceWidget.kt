package com.example.liftrix.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.android.EntryPointAccessors

class DashboardGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HomeWidgetEntryPoint::class.java
        )
        val snapshot = entryPoint.dataSource().loadSnapshots().dashboard
        provideContent {
            DashboardWidgetContent(context = context, snapshot = snapshot)
        }
    }
}

@Composable
private fun DashboardWidgetContent(
    context: Context,
    snapshot: DashboardWidgetSnapshot
) {
    val hasData = hasVolumeData(snapshot.totalVolumeLabel)
    PremiumWidgetSurface(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(LiftrixWidgetActions.openWorkoutAction(context, "dashboard")),
        cornerRadius = 22.dp
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "30D VOLUME",
                    maxLines = 1,
                    style = TextStyle(
                        color = LiftrixWidgetTheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = WidgetFontFamily
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                PremiumVolumeValue(
                    label = snapshot.totalVolumeLabel,
                    valueSize = 52,
                    unitSize = 22,
                    active = hasData,
                    unitBottomPadding = 8.dp
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = if (hasData) {
                        "${snapshot.currentStreakWeeks} week streak"
                    } else {
                        "Log your first session"
                    },
                    maxLines = 1,
                    style = TextStyle(
                        color = LiftrixWidgetTheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = WidgetFontFamily
                    )
                )
            }
            Spacer(modifier = GlanceModifier.width(12.dp))
            PremiumWeekPanel(
                days = snapshot.recentVolumeDays,
                title = "LAST 5",
                modifier = GlanceModifier
                    .width(155.dp)
                    .fillMaxHeight()
            )
        }
    }
}
