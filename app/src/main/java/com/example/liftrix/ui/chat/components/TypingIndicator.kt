package com.example.liftrix.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.example.liftrix.ui.theme.LiftrixColorsV2

/**
 * Simple typing indicator with animated dots.
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    dotColor: Color = LiftrixColorsV2.primary
) {
    if (!isVisible) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "typing_animation")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val animationDelay = index * 100
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.3f at animationDelay with LinearEasing
                        1f at animationDelay + 400 with LinearEasing
                        0.3f at animationDelay + 800 with LinearEasing
                    }
                ),
                label = "dot_alpha_$index"
            )
            
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.8f at animationDelay with LinearEasing
                        1f at animationDelay + 400 with LinearEasing
                        0.8f at animationDelay + 800 with LinearEasing
                    }
                ),
                label = "dot_scale_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(
                        color = dotColor.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }
    }
}