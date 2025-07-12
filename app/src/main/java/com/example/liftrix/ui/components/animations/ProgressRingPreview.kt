package com.example.liftrix.ui.components.animations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.delay

/**
 * Preview parameter provider for different progress values
 */
class ProgressValueProvider : PreviewParameterProvider<Float> {
    override val values = sequenceOf(
        0.0f,    // Empty
        0.25f,   // Quarter
        0.5f,    // Half
        0.75f,   // Three quarters
        1.0f     // Complete
    )
}

/**
 * Basic progress ring preview
 */
@Preview(
    name = "Basic Progress Ring",
    showBackground = true,
    backgroundColor = 0xFFF8F9FA
)
@Composable
fun ProgressRingPreview(
    @PreviewParameter(ProgressValueProvider::class) progress: Float
) {
    LiftrixTheme {
        Surface {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProgressRing(
                        progress = progress,
                        size = 120.dp,
                        strokeWidth = 8.dp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "${(progress * 100).toInt()}% Complete",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Animated progress ring preview with dynamic progress
 */
@Preview(
    name = "Animated Progress Ring",
    showBackground = true,
    backgroundColor = 0xFFF8F9FA
)
@Composable
fun AnimatedProgressRingPreview() {
    LiftrixTheme {
        Surface {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                var progress by remember { mutableFloatStateOf(0f) }
                
                LaunchedEffect(Unit) {
                    while (true) {
                        progress = 0f
                        delay(500)
                        progress = 0.75f
                        delay(2000)
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedProgressRing(
                        progress = progress,
                        size = 120.dp,
                        strokeWidth = 8.dp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Animated: ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Progress ring with gradient preview
 */
@Preview(
    name = "Gradient Progress Ring",
    showBackground = true,
    backgroundColor = 0xFFF8F9FA
)
@Composable
fun GradientProgressRingPreview() {
    LiftrixTheme {
        Surface {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                var progress by remember { mutableFloatStateOf(0f) }
                
                LaunchedEffect(Unit) {
                    while (true) {
                        progress = 0f
                        delay(500)
                        progress = 0.85f
                        delay(2000)
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedProgressRingWithGradient(
                        progress = progress,
                        progressBrush = LiftrixColors.BrandGradients.TealCoral,
                        size = 120.dp,
                        strokeWidth = 10.dp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Gradient: ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Multi-layer progress ring preview
 */
@Preview(
    name = "Multi-Layer Progress Ring",
    showBackground = true,
    backgroundColor = 0xFFF8F9FA
)
@Composable
fun MultiLayerProgressRingPreview() {
    LiftrixTheme {
        Surface {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                var outerProgress by remember { mutableFloatStateOf(0f) }
                var innerProgress by remember { mutableFloatStateOf(0f) }
                
                LaunchedEffect(Unit) {
                    while (true) {
                        outerProgress = 0f
                        innerProgress = 0f
                        delay(500)
                        outerProgress = 0.6f
                        delay(500)
                        innerProgress = 0.9f
                        delay(2000)
                    }
                }
                
                val progressLayers = listOf(
                    ProgressLayer(
                        progress = outerProgress,
                        color = LiftrixColors.Primary,
                        strokeWidth = 8.dp
                    ),
                    ProgressLayer(
                        progress = innerProgress,
                        color = LiftrixColors.Accent,
                        strokeWidth = 6.dp
                    )
                )
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MultiLayerProgressRing(
                        progressLayers = progressLayers,
                        size = 140.dp,
                        layerSpacing = 8.dp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Multi-Layer Progress",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Progress ring variants comparison
 */
@Preview(
    name = "Progress Ring Variants",
    showBackground = true,
    backgroundColor = 0xFFF8F9FA,
    widthDp = 400,
    heightDp = 300
)
@Composable
fun ProgressRingVariantsPreview() {
    LiftrixTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Workout Progress
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProgressRingDefaults.workoutProgress(
                            progress = 0.7f,
                            size = 80.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Workout",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Streak Progress
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProgressRingDefaults.streakProgress(
                            progress = 0.85f,
                            size = 80.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Streak",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Volume Progress
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProgressRingDefaults.volumeProgress(
                            progress = 0.45f,
                            size = 80.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Volume",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Duration Progress
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProgressRingDefaults.durationProgress(
                            progress = 0.6f,
                            size = 80.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Different sizes comparison
 */
@Preview(
    name = "Progress Ring Sizes",
    showBackground = true,
    backgroundColor = 0xFFF8F9FA,
    widthDp = 400,
    heightDp = 200
)
@Composable
fun ProgressRingSizesPreview() {
    LiftrixTheme {
        Surface {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Small
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedProgressRing(
                        progress = 0.6f,
                        size = 60.dp,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Small",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // Medium
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedProgressRing(
                        progress = 0.6f,
                        size = 80.dp,
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Medium",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // Large
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedProgressRing(
                        progress = 0.6f,
                        size = 120.dp,
                        strokeWidth = 8.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Large",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Dark theme preview
 */
@Preview(
    name = "Progress Ring Dark Theme",
    showBackground = true,
    backgroundColor = 0xFF121212,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ProgressRingDarkThemePreview() {
    LiftrixTheme(darkTheme = true) {
        Surface {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedProgressRingWithGradient(
                        progress = 0.75f,
                        progressBrush = LiftrixColors.BrandGradients.TealCoral,
                        size = 120.dp,
                        strokeWidth = 8.dp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "75% Complete",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}