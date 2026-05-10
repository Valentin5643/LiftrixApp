package com.example.liftrix.ui.branding

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.liftrix.R
import com.example.liftrix.ui.theme.ThemeManager

@Composable
fun LiftrixLaunchAnimation(
    modifier: Modifier = Modifier,
    isDarkThemeOverride: Boolean? = null
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager.getInstance(context) }
    val themeMode by themeManager.themeMode.collectAsState()
    val isSystemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = isDarkThemeOverride
        ?: remember(themeMode, isSystemDarkTheme) {
            themeManager.getEffectiveThemeState(isSystemDarkTheme)
        }
    val animationRes = if (isDarkTheme) {
        R.raw.anim_liftrix_loading
    } else {
        R.raw.anim_liftrix_loading_white
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animationRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(168.dp)
    )
}
