package com.example.liftrix.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.LiftrixColorsV2
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.liftrix.ui.theme.getStableBackground
import com.example.liftrix.ui.theme.DirectV2Colors

@Composable
fun StartingScreen(
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    Surface(
        modifier = modifier.fillMaxSize(),
        color = getStableBackground() // FIXED: Use stable background color to prevent fallbacks during auth
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Section: App Logo and Name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text(
                    text = "Liftrix",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = DirectV2Colors.getPrimary(), // FIXED: Direct V2 color access prevents fallbacks
                    modifier = Modifier.semantics {
                        contentDescription = "Liftrix app logo"
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "AI-Powered Gym Companion",
                    style = MaterialTheme.typography.titleMedium,
                    color = DirectV2Colors.getTextSecondary(), // FIXED: Direct V2 color access
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        contentDescription = "AI-Powered Gym Companion tagline"
                    }
                )
            }

            // Center Section: Fitness Illustration
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                FitnessIllustration(
                    modifier = Modifier
                        .size(240.dp)
                        .semantics {
                            contentDescription = "Fitness and gym illustration with dumbbells and workout equipment"
                        }
                )
            }

            // Bottom Section: Call to Action Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription = "Get started with creating your account"
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiftrixColorsV2.Teal,
                        contentColor = if (isDarkTheme) LiftrixColorsV2.Dark.BackgroundPrimary else LiftrixColorsV2.Light.BackgroundPrimary
                    )
                ) {
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onSignIn,
                    modifier = Modifier.semantics {
                        contentDescription = "Sign in to existing account"
                    }
                ) {
                    Text(
                        text = "Already have an account? Log in",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LiftrixColorsV2.Teal
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun FitnessIllustration(
    modifier: Modifier = Modifier
) {
    val primaryColor = LiftrixColorsV2.Teal
    val secondaryColor = LiftrixColorsV2.TealHover
    val accentColor = LiftrixColorsV2.DataViz.Series3
    
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f
        
        // Draw gradient background circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.1f),
                    primaryColor.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = canvasWidth * 0.4f
            ),
            center = Offset(centerX, centerY),
            radius = canvasWidth * 0.4f
        )
        
        // Draw main dumbbell
        drawDumbbell(
            centerX = centerX,
            centerY = centerY,
            color = primaryColor,
            size = canvasWidth * 0.3f
        )
        
        // Draw smaller dumbbells around
        val smallSize = canvasWidth * 0.15f
        val radius = canvasWidth * 0.25f
        
        // Top-left dumbbell
        drawDumbbell(
            centerX = centerX - radius * 0.7f,
            centerY = centerY - radius * 0.7f,
            color = secondaryColor,
            size = smallSize,
            rotation = -45f
        )
        
        // Top-right dumbbell
        drawDumbbell(
            centerX = centerX + radius * 0.7f,
            centerY = centerY - radius * 0.7f,
            color = accentColor,
            size = smallSize,
            rotation = 45f
        )
        
        // Bottom accent elements
        drawCircle(
            color = accentColor.copy(alpha = 0.3f),
            center = Offset(centerX - radius * 0.8f, centerY + radius * 0.8f),
            radius = 12.dp.toPx()
        )
        
        drawCircle(
            color = secondaryColor.copy(alpha = 0.3f),
            center = Offset(centerX + radius * 0.8f, centerY + radius * 0.8f),
            radius = 8.dp.toPx()
        )
    }
}

private fun DrawScope.drawDumbbell(
    centerX: Float,
    centerY: Float,
    color: Color,
    size: Float,
    rotation: Float = 0f
) {
    val strokeWidth = size * 0.08f
    val barLength = size * 0.6f
    val weightRadius = size * 0.15f
    
    // Draw bar
    drawLine(
        color = color,
        start = Offset(centerX - barLength / 2, centerY),
        end = Offset(centerX + barLength / 2, centerY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    
    // Draw left weight
    drawCircle(
        color = color,
        center = Offset(centerX - barLength / 2, centerY),
        radius = weightRadius,
        style = Stroke(width = strokeWidth)
    )
    
    // Draw right weight
    drawCircle(
        color = color,
        center = Offset(centerX + barLength / 2, centerY),
        radius = weightRadius,
        style = Stroke(width = strokeWidth)
    )
    
    // Draw weight details
    drawCircle(
        color = color.copy(alpha = 0.6f),
        center = Offset(centerX - barLength / 2, centerY),
        radius = weightRadius * 0.6f
    )
    
    drawCircle(
        color = color.copy(alpha = 0.6f),
        center = Offset(centerX + barLength / 2, centerY),
        radius = weightRadius * 0.6f
    )
}

@Preview(showBackground = true)
@Composable
private fun StartingScreenPreview() {
    LiftrixTheme {
        StartingScreen(
            onGetStarted = { },
            onSignIn = { }
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StartingScreenDarkPreview() {
    LiftrixTheme(darkTheme = true) {
        StartingScreen(
            onGetStarted = { },
            onSignIn = { }
        )
    }
} 