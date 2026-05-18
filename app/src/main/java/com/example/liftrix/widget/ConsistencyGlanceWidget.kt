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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.android.EntryPointAccessors

class ConsistencyGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HomeWidgetEntryPoint::class.java
        )
        val snapshot = entryPoint.dataSource().loadSnapshots().consistency
        provideContent {
            ConsistencyWidgetContent(context = context, snapshot = snapshot)
        }
    }
}

@Composable
private fun ConsistencyWidgetContent(
    context: Context,
    snapshot: ConsistencyWidgetSnapshot
) {
    val hasData = hasVolumeData(snapshot.totalVolumeLabel)
    PremiumWidgetSurface(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(LiftrixWidgetActions.openWorkoutAction(context, "consistency")),
        cornerRadius = 22.dp,
        compact = true
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumIcon(size = 28.dp, iconSize = 14.dp)
                Spacer(modifier = GlanceModifier.width(10.dp))
                Column {
                    PremiumVolumeValue(
                        label = snapshot.totalVolumeLabel,
                        valueSize = 32,
                        unitSize = 14,
                        active = hasData,
                        unitBottomPadding = 3.dp
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Text(
                        text = "30D VOLUME",
                        maxLines = 1,
                        style = TextStyle(
                            color = LiftrixWidgetTheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = WidgetFontFamily
                        )
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            PremiumWeekBlock(
                days = snapshot.lastSevenDays,
                title = "LAST 5",
                compact = true,
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }
}
