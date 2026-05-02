package com.example.liftrix.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Reusable shimmer loading animation placeholder component
 * 
 * Provides smooth shimmer effect during loading states with performance optimized animations.
 * Used across different components for consistent loading experience.
 */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                RoundedCornerShape(4.dp)
            )
    )
}

/**
 * Shimmer loading placeholder for workout feed items
 * 
 * Matches the layout of WorkoutFeedItem component for seamless loading transitions.
 * Displays placeholders for workout title, metadata row, and optional notes.
 */
@Composable
fun FeedItemShimmer(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Workout title placeholder
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(20.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Metadata row placeholder
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Notes placeholder (optional)
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(14.dp)
            )
        }
    }
}

/**
 * Shimmer loading placeholder for user recommendation cards
 * 
 * Matches the layout of RecommendedUserCard component for consistent loading experience.
 * Displays placeholders for profile image, username, and follow button.
 */
@Composable
fun UserCardShimmer(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.size(width = 140.dp, height = 120.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Profile image placeholder
            ShimmerPlaceholder(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            
            // Username placeholder
            ShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp)
            )
            
            // Follow button placeholder
            ShimmerPlaceholder(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        }
    }
}