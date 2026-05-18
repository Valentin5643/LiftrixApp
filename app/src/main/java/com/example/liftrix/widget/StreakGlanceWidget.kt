package com.example.liftrix.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.liftrix.R
import dagger.hilt.android.EntryPointAccessors

class StreakGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HomeWidgetEntryPoint::class.java
        )
        val snapshot = entryPoint.dataSource().loadSnapshots().streak
        provideContent {
            StreakWidgetContent(context = context, snapshot = snapshot)
        }
    }
}

@Composable
private fun StreakWidgetContent(
    context: Context,
    snapshot: StreakWidgetSnapshot
) {
    PremiumWidgetSurface(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(LiftrixWidgetActions.openWorkoutAction(context, "streak")),
        cornerRadius = 18.dp,
        compact = true
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PremiumIcon(
                icon = R.drawable.ic_widget_flame,
                size = 24.dp,
                iconSize = 12.dp,
                containerRadius = 8.dp,
                containerColor = Color(0xFF3A1608),
                iconColor = Color(0xFFFF8A2A)
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = snapshot.currentStreakWeeks.toString(),
                maxLines = 1,
                style = TextStyle(
                    color = if (snapshot.hasData) {
                        LiftrixWidgetTheme.onBackground
                    } else {
                        LiftrixWidgetTheme.zeroValue
                    },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = WidgetDisplayFontFamily
                )
            )
            Text(
                text = "WEEKS",
                maxLines = 1,
                style = TextStyle(
                    color = LiftrixWidgetTheme.zeroValue,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = WidgetFontFamily
                )
            )
        }
    }
}
